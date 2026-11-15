package com.ouroboros.workflow.approval.flowable.service;

import java.util.Objects;
import java.util.Map;

import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.springframework.stereotype.Component;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.workflow.approval.BuiltInApprovalBusinessBindingValidator;
import com.ouroboros.workflow.approval.ApprovalInstanceState;
import com.ouroboros.workflow.approval.FlowInstanceVariables;
import com.ouroboros.workflow.approval.flowable.event.FlowableSourceTransaction;
import com.ouroboros.workflow.approval.flowable.transpiler.Constants;
import com.ouroboros.workflow.approval.lifecycle.ApprovalCaseService;
import com.ouroboros.workflow.approval.model.events.FlowInstanceEvent;

/** Uses engine variables and the locked case, never a caller-supplied model or callback claim. */
@Component
public class FlowableBusinessBindingValidator implements BuiltInApprovalBusinessBindingValidator {
  private final RuntimeService runtime;
  private final TaskService tasks;
  private final RepositoryService repository;
  private final ApprovalCaseService cases;
  private final FlowableSourceTransaction transaction;
  private final HistoryService history;

  public FlowableBusinessBindingValidator(RuntimeService runtime, TaskService tasks, RepositoryService repository, HistoryService history,
      ApprovalCaseService cases, FlowableSourceTransaction transaction) {
    this.runtime = runtime;
    this.tasks = tasks;
    this.repository = repository;
    this.cases = cases;
    this.transaction = transaction;
    this.history = history;
  }

  @Override
  public ApprovalInstanceState inspectInstance(String flowKey, String instanceId) {
    transaction.requireParticipation();
    var running = runtime.createProcessInstanceQuery().processInstanceId(instanceId).includeProcessVariables().singleResult();
    if (running != null) {
      requireFlow(flowKey, running.getProcessDefinitionKey());
      var values = running.getProcessVariables();
      var type = running.isSuspended() ? FlowInstanceEvent.EventType.SUSPENDED
          : values.containsKey(Constants.PROCESS_RESUME_TIME_VARIABLE_NAME) ? FlowInstanceEvent.EventType.RESUMED
              : FlowInstanceEvent.EventType.STARTED;
      return snapshot(flowKey, instanceId, values, type, null);
    }
    var ended = history.createHistoricProcessInstanceQuery().processInstanceId(instanceId).includeProcessVariables().singleResult();
    if (ended == null || ended.getEndTime() == null) {
      throw new BusinessException("审批实例没有可核验的持久状态基线");
    }
    requireFlow(flowKey, ended.getProcessDefinitionKey());
    var values = ended.getProcessVariables();
    String outcome = Objects.toString(values.get(Constants.Variable.PROCESS_OUTCOME), null);
    // Old deleted instances can have identical flags for cancel and reject. Never guess from their message or delete reason.
    if (outcome == null && ended.getEndActivityId() != null && values.get(Constants.Variable.PROCESS_APPROVED) instanceof Boolean approved) {
      outcome = approved ? "approved" : "rejected";
    }
    var type = switch (Objects.toString(outcome, "")) {
      case "approved" -> FlowInstanceEvent.EventType.APPROVED;
      case "rejected" -> FlowInstanceEvent.EventType.REJECTED;
      case "withdrawn", "cancelled", "terminated" -> FlowInstanceEvent.EventType.CANCELLED;
      default -> throw new BusinessException("审批历史终态缺少可核验的业务结果，保留原 Outbox 责任");
    };
    return snapshot(flowKey, instanceId, values, type, outcome);
  }

  private ApprovalInstanceState snapshot(String flowKey, String instanceId, Map<String, Object> values,
      FlowInstanceEvent.EventType type, String outcome) {
    String recordType = Objects.toString(values.get(FlowInstanceVariables.BUSINESS_RECORD_TYPE), null);
    String recordId = Objects.toString(values.get(FlowInstanceVariables.BUSINESS_RECORD_ID), null);
    if (recordType == null || recordId == null) { throw new BusinessException("审批状态基线缺少正式业务对象归属"); }
    Object round = values.get(FlowInstanceVariables.SUBMISSION_ROUND);
    return new ApprovalInstanceState(flowKey, instanceId, new FlowInstanceEvent.BusinessContext(recordType, recordId),
        Objects.toString(values.get(FlowInstanceVariables.APPROVAL_CASE_ID), null), round instanceof Number number ? number.intValue() : null,
        Objects.toString(values.get(FlowInstanceVariables.CALLBACK_FLOW), null), type, outcome);
  }

  private void requireFlow(String expected, String actual) {
    if (!Objects.equals(expected, actual)) { throw new BusinessException("审批状态基线与正式流程归属冲突"); }
  }

  @Override
  public void validate(String flowKey, String recordType, String recordId, String instanceId, String taskId) {
    transaction.requireParticipation();
    if (repository.createProcessDefinitionQuery().processDefinitionKey(flowKey).latestVersion().singleResult() == null) {
      throw new BusinessException("业务绑定的审批定义尚未部署");
    }
    var current = cases.lockByBusinessRecord(recordType, recordId, flowKey).orElse(null);
    if (instanceId == null && taskId == null) { return; }
    var instance = runtime.createProcessInstanceQuery().processInstanceId(instanceId).singleResult();
    var task = tasks.createTaskQuery().taskId(taskId).singleResult();
    if (instance == null || task == null || !Objects.equals(flowKey, instance.getProcessDefinitionKey())
        || !Objects.equals(instanceId, task.getProcessInstanceId())) {
      throw new BusinessException("业务记录、审批实例与任务的正式绑定不匹配");
    }
    var variables = runtime.getVariables(instanceId);
    if (!Objects.equals(recordType, variables.get(FlowInstanceVariables.BUSINESS_RECORD_TYPE))
        || !Objects.equals(recordId, Objects.toString(variables.get(FlowInstanceVariables.BUSINESS_RECORD_ID), null))) {
      throw new BusinessException("审批实例不属于本次业务对象");
    }
    Object caseId = variables.get(FlowInstanceVariables.APPROVAL_CASE_ID);
    Object round = variables.get(FlowInstanceVariables.SUBMISSION_ROUND);
    if (current == null && (caseId != null || round != null)
        || current != null && (!Objects.equals(current.getApprovalCaseId(), caseId)
            || !(round instanceof Number number) || current.getSubmissionRound() != number.intValue()
            || !Objects.equals(current.getActiveExecutionId(), instanceId))) {
      throw new BusinessException("审批任务已不属于业务对象的当前案件轮次");
    }
  }
}
