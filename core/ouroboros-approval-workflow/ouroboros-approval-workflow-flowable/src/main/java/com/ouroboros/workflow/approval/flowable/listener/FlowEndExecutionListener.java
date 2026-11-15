package com.ouroboros.workflow.approval.flowable.listener;

import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.ExecutionListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ouroboros.util.Dates;
import com.ouroboros.workflow.approval.definition.nodes.EndNode;
import com.ouroboros.workflow.approval.event.InstanceEventDispatcher;
import com.ouroboros.workflow.approval.flowable.transpiler.Constants;
import com.ouroboros.workflow.approval.flowable.util.BpmnUtil;
import com.ouroboros.workflow.approval.model.events.FlowInstanceEvent;

@Component(FlowEndExecutionListener.BEAN_NAME)
public class FlowEndExecutionListener implements ExecutionListener {
  public static final String BEAN_NAME = "flowEndExecutionListener";
  @Autowired
  private RepositoryService repositoryService;
  @Autowired
  private HistoryService historyService;
  @Autowired
  private org.flowable.engine.RuntimeService runtimeService;
  @Autowired
  private InstanceEventDispatcher instanceEventDispatcher;

  @Override
  public void notify(DelegateExecution execution) {
    applyFinishCc(execution);
    var event = new FlowInstanceEvent();
    event.setInstanceId(execution.getProcessInstanceId());
    event.setFlowKey(resolveFlowKey(execution));
    event.setEventType(readEventType(execution));
    event.setEventTime(Dates.now());
    event.setStarter(execution.getVariable(Constants.Variable.PROCESS_STARTER, String.class));
    event.setLastTaskId(getLastFinishedTaskId(execution));
    event.setOutcome(execution.getVariable(Constants.Variable.PROCESS_OUTCOME, String.class));
    event.setApprovalCaseId(execution.getVariable(Constants.Variable.APPROVAL_CASE_ID, String.class));
    event.setSubmissionRound(execution.getVariable(Constants.Variable.SUBMISSION_ROUND, Integer.class));
    event.setBusinessContext(businessContextOf(execution));
    event.setCallbackFlow(execution.getVariable(Constants.Variable.CALLBACK_FLOW, String.class));
    event.setNotification(BpmnUtil.readNotification(repositoryService, execution.getProcessDefinitionId()));
    instanceEventDispatcher.dispatchEvent(event);
  }

  private FlowInstanceEvent.BusinessContext businessContextOf(DelegateExecution execution) {
    var recordType = execution.getVariable(Constants.Variable.BUSINESS_RECORD_TYPE, String.class);
    var recordId = execution.getVariable(Constants.Variable.BUSINESS_RECORD_ID, String.class);
    if (recordType == null || recordId == null) {
      return null;
    }
    return new FlowInstanceEvent.BusinessContext(recordType, recordId);
  }

  /**
   * 发起时抄送（FINISH 时机）：流程结束时把实例变量中记录的抄送用户加入参与者。
   */
  private void applyFinishCc(DelegateExecution execution) {
    Object rawUsers = execution.getVariable(Constants.Variable.START_CC_FINISH_USERS);
    if (!(rawUsers instanceof List<?> users) || users.isEmpty()) {
      return;
    }
    users.stream()
        .map(String::valueOf)
        .filter(StringUtils::isNotBlank)
        .distinct()
        .forEach(userId -> runtimeService.addUserIdentityLink(
            execution.getProcessInstanceId(), userId, org.flowable.identitylink.api.IdentityLinkType.PARTICIPANT));
  }

  private FlowInstanceEvent.EventType readEventType(DelegateExecution execution) {
    var outcome = execution.getVariable(Constants.Variable.PROCESS_OUTCOME, String.class);
    if (EndNode.OUTCOME_REJECTED.equals(outcome)) {
      return FlowInstanceEvent.EventType.REJECTED;
    }
    var approved = Boolean.TRUE.equals(execution.getVariable(Constants.Variable.PROCESS_APPROVED, Boolean.class));
    return approved ? FlowInstanceEvent.EventType.APPROVED : FlowInstanceEvent.EventType.REJECTED;
  }

  private String getLastFinishedTaskId(DelegateExecution execution) {
    var lastFinishedTaskId = execution.getVariable(Constants.Variable.PROCESS_LAST_FINISHED_TASK_ID, String.class);
    if (lastFinishedTaskId != null) {
      return lastFinishedTaskId;
    }

    if (historyService == null) {
      return null;
    }

    return Optional.of(historyService
            .createHistoricTaskInstanceQuery()
            .processInstanceId(execution.getProcessInstanceId())
            .finished()
            .orderByHistoricTaskInstanceEndTime()
            .desc()
            .listPage(0, 1))
        .filter(tasks -> !tasks.isEmpty())
        .map(tasks -> tasks.get(0).getId())
        .orElse(null);
  }

  private String resolveFlowKey(DelegateExecution execution) {
    var flowKey = execution.getVariable(Constants.Variable.PROCESS_FLOW_KEY, String.class);
    if (flowKey != null) {
      return flowKey;
    }

    if (repositoryService == null || execution.getProcessDefinitionId() == null) {
      return null;
    }

    ProcessDefinition processDefinition = repositoryService.getProcessDefinition(execution.getProcessDefinitionId());
    return processDefinition == null ? null : processDefinition.getKey();
  }
}
