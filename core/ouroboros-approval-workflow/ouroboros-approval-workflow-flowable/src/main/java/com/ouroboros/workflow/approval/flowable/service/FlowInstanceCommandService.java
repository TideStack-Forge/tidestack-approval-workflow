package com.ouroboros.workflow.approval.flowable.service;

import static com.ouroboros.workflow.approval.flowable.util.BpmnUtil.readStructuredFlowDefinition;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.runtime.ProcessInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.NoTransactionException;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import io.vavr.control.Try;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.util.Dates;
import com.ouroboros.workflow.approval.FlowDefinitionService;
import com.ouroboros.workflow.approval.UserSelectAssigneeRangeChecker;
import com.ouroboros.workflow.approval.FlowFormService;
import com.ouroboros.workflow.approval.definition.FlowNode;
import com.ouroboros.workflow.approval.definition.NodeFormFieldPermission;
import com.ouroboros.workflow.approval.definition.graph.StructuredFlowDefinition;
import com.ouroboros.workflow.approval.event.InstanceEventDispatcher;
import com.ouroboros.workflow.approval.flowable.transpiler.Constants;
import com.ouroboros.workflow.approval.model.events.FlowInstanceEvent;

@Service
public class FlowInstanceCommandService {
  final TaskService taskService;
  final RuntimeService runtimeService;
  final List<FlowFormService> flowFormServices;

  RepositoryService repositoryService;
  HistoryService historyService;
  FlowDefinitionService flowDefinitionService;
  InstanceEventDispatcher instanceEventDispatcher;

  @Autowired
  public FlowInstanceCommandService(
      TaskService taskService,
      RuntimeService runtimeService,
      RepositoryService repositoryService,
      HistoryService historyService,
      FlowDefinitionService flowDefinitionService,
      List<FlowFormService> flowFormServices,
      InstanceEventDispatcher instanceEventDispatcher) {
    this(taskService, runtimeService, flowFormServices);
    this.repositoryService = repositoryService;
    this.historyService = historyService;
    this.flowDefinitionService = flowDefinitionService;
    this.instanceEventDispatcher = instanceEventDispatcher;
  }

  public FlowInstanceCommandService(TaskService taskService, RuntimeService runtimeService, List<FlowFormService> flowFormServices) {
    this.taskService = taskService;
    this.runtimeService = runtimeService;
    this.flowFormServices = flowFormServices == null ? Collections.emptyList() : flowFormServices;
  }

  public Try<String> start(String flowKey, String userId, Map<String, List<String>> taskAssignee, Map<String, Object> instanceVariables,
                           Map<String, Object> formData, CompletableFuture<Void> initTaskDeferred) {
    var definitionTry = getProcessDefinition(flowKey);
    if (definitionTry.isFailure()) {
      return Try.failure(definitionTry.getCause());
    }

    var definition = definitionTry.get();
    var process = repositoryService.getBpmnModel(definition.getId()).getMainProcess();
    var flowDefinition = readStructuredFlowDefinition(process);
    var startContext = buildStartConditionContext(instanceVariables, formData);
    var finalTaskAssigneeTry = filterUserSelectAssignee(flowKey, startContext.conditionVariables, taskAssignee);
    if (finalTaskAssigneeTry.isFailure()) {
      return Try.failure(finalTaskAssigneeTry.getCause());
    }

    var variables = new HashMap<String, Object>(startContext.conditionVariables);
    var formType = flowDefinition == null ? "default" : StringUtils.defaultIfBlank(flowDefinition.getFormType(), "default");
    if (!startContext.persistedFormData.isEmpty()) {
      variables.put(Constants.CANDIDATE_FORM_DATA_KEY, new HashMap<>(startContext.persistedFormData));
    }
    variables.put(Constants.Variable.PROCESS_STARTER, userId);
    variables.put(Constants.Variable.PROCESS_FLOW_KEY, flowKey);
    variables.put(Constants.Variable.USER_SELECTED_ASSIGNEES, finalTaskAssigneeTry.get());
    variables.put(Constants.TASK_FORM_TYPE_KEY, formType);

    var instance = runtimeService.startProcessInstanceByKey(definition.getKey(), variables);
    var permissions = getInitialPermissions(flowDefinition);

    var formKey = flowDefinition == null ? null : flowDefinition.getFormKey();
    var formServiceOptional = getFlowFormService(formType);
    if (formServiceOptional.isPresent() && StringUtils.isNotBlank(formKey)) {
      var submitTry = formServiceOptional.get().submitInitialForm(instance.getId(), formKey, userId, permissions, formData);
      if (submitTry.isFailure()) {
        return rollbackFailure(submitTry.getCause());
      }
      var formId = submitTry.get();
      if (StringUtils.isNotBlank(formId)) {
        runtimeService.setVariable(instance.getId(), Constants.TASK_FORM_ID_KEY, formId);
      }
    }

    Optional.ofNullable(initTaskDeferred)
        .orElseGet(() -> CompletableFuture.completedFuture(null))
        .thenRun(() -> initializeFirstTask(instance.getId(), userId, formData));

    return Try.success(instance.getId());
  }

  public Try<Void> pause(String instanceId, String userId, String comment) {
    return getInstance(instanceId)
        .map(instance -> {
          if (instance.isSuspended()) {
            return null;
          }

          var variables = new HashMap<String, Object>();
          variables.put(Constants.PROCESS_PAUSE_USER_VARIABLE_NAME, userId);
          variables.put(Constants.PROCESS_PAUSE_REASON_VARIABLE_NAME, comment);
          variables.put(Constants.PROCESS_PAUSE_TIME_VARIABLE_NAME, Dates.now());
          runtimeService.setVariables(instanceId, variables);
          runtimeService.suspendProcessInstanceById(instanceId);
          dispatchInstanceEvent(instanceId, instance, FlowInstanceEvent.EventType.SUSPENDED, null);
          return null;
        });
  }

  public Try<Void> resume(String instanceId, String userId) {
    return getInstance(instanceId)
        .map(instance -> {
          if (!instance.isSuspended()) {
            return null;
          }

          runtimeService.activateProcessInstanceById(instanceId);
          var variables = new HashMap<String, Object>();
          variables.put(Constants.PROCESS_RESUME_USER_VARIABLE_NAME, userId);
          variables.put(Constants.PROCESS_RESUME_TIME_VARIABLE_NAME, Dates.now());
          runtimeService.setVariables(instanceId, variables);
          dispatchInstanceEvent(instanceId, instance, FlowInstanceEvent.EventType.RESUMED, null);
          return null;
        });
  }

  public Try<Void> cancel(String instanceId, String userId, String comment) {
    return getInstance(instanceId)
        .map(instance -> {
          var lastTaskId = getLastFinishedTaskId(instanceId);
          var flowKey = getFlowKey(instanceId, instance);
          var starter = getRuntimeStringVariable(instanceId, Constants.Variable.PROCESS_STARTER);
          var outcome = StringUtils.defaultIfBlank(getRuntimeStringVariable(instanceId, Constants.Variable.PROCESS_OUTCOME), "cancelled");
          var businessContext = businessContextOf(instanceId);
          var callbackFlow = getRuntimeStringVariable(instanceId, Constants.Variable.CALLBACK_FLOW);
          var approvalCaseId = getRuntimeStringVariable(instanceId, Constants.Variable.APPROVAL_CASE_ID);
          var submissionRound = runtimeService.getVariable(
              instanceId, Constants.Variable.SUBMISSION_ROUND, Integer.class);
          var variables = new HashMap<String, Object>();
          variables.put(Constants.PROCESS_CANCEL_USER_VARIABLE_NAME, userId);
          variables.put(Constants.PROCESS_CANCEL_REASON_VARIABLE_NAME, comment);
          variables.put(Constants.PROCESS_CANCEL_TIME_VARIABLE_NAME, Dates.now());
          variables.put(Constants.Variable.PROCESS_APPROVED, false);
          variables.put(Constants.Variable.PROCESS_OUTCOME, outcome);
          variables.put(Constants.Variable.PROCESS_LAST_FINISHED_TASK_ID, lastTaskId);
          runtimeService.setVariables(instanceId, variables);

          runtimeService.deleteProcessInstance(instanceId, comment);
          dispatchInstanceEvent(instanceId, flowKey, starter, FlowInstanceEvent.EventType.CANCELLED, lastTaskId,
              outcome, businessContext, callbackFlow, approvalCaseId, submissionRound);
          return null;
        });
  }

  /**
   * 发起人撤回：仅发起人可撤回审批中的实例；撤回后事件类型为 CANCELLED、outcome=withdrawn。
   */
  public Try<Void> withdraw(String instanceId, String userId, String comment) {
    return getInstance(instanceId)
        .flatMap(instance -> {
          var starter = getRuntimeStringVariable(instanceId, Constants.Variable.PROCESS_STARTER);
          if (starter == null || !starter.equals(userId)) {
            return Try.failure(new BusinessException("只有发起人可以撤回流程实例: " + instanceId));
          }
          if (instance.isEnded()) {
            return Try.failure(new BusinessException("流程已结束，无法撤回: " + instanceId));
          }
          runtimeService.setVariable(instanceId, Constants.Variable.PROCESS_OUTCOME, "withdrawn");
          return cancel(instanceId, userId, comment);
        });
  }

  public Try<Void> complete(String instanceId, String userId, Boolean approved, String comment) {
    return getInstance(instanceId)
        .map(instance -> {
          var approvedResult = Boolean.TRUE.equals(approved);
          var lastTaskId = getLastFinishedTaskId(instanceId);
          var flowKey = getFlowKey(instanceId, instance);
          var starter = getRuntimeStringVariable(instanceId, Constants.Variable.PROCESS_STARTER);
          var businessContext = businessContextOf(instanceId);
          var callbackFlow = getRuntimeStringVariable(instanceId, Constants.Variable.CALLBACK_FLOW);
          var approvalCaseId = getRuntimeStringVariable(instanceId, Constants.Variable.APPROVAL_CASE_ID);
          var submissionRound = runtimeService.getVariable(
              instanceId, Constants.Variable.SUBMISSION_ROUND, Integer.class);
          var variables = new HashMap<String, Object>();
          variables.put(Constants.PROCESS_CANCEL_USER_VARIABLE_NAME, userId);
          variables.put(Constants.PROCESS_CANCEL_REASON_VARIABLE_NAME, comment);
          variables.put(Constants.PROCESS_CANCEL_TIME_VARIABLE_NAME, Dates.now());
          variables.put(Constants.Variable.PROCESS_APPROVED, approvedResult);
          variables.put(Constants.Variable.PROCESS_OUTCOME, approvedResult ? "approved" : "rejected");
          variables.put(Constants.Variable.PROCESS_LAST_FINISHED_TASK_ID, lastTaskId);
          runtimeService.setVariables(instanceId, variables);

          runtimeService.deleteProcessInstance(instanceId, comment);
          dispatchInstanceEvent(
              instanceId,
              flowKey,
              starter,
              approvedResult ? FlowInstanceEvent.EventType.APPROVED : FlowInstanceEvent.EventType.REJECTED,
              lastTaskId,
              approvedResult ? "approved" : "rejected",
              businessContext,
              callbackFlow,
              approvalCaseId,
              submissionRound);
          return null;
        });
  }

  private void initializeFirstTask(String instanceId, String userId, Map<String, Object> formData) {
    var task = taskService.createTaskQuery()
        .processInstanceId(instanceId)
        .singleResult();
    if (task == null) {
      return;
    }

    taskService.setAssignee(task.getId(), userId);
    taskService.setVariableLocal(task.getId(), Constants.Variable.TASK_APPROVED, true);
    if (formData != null && !formData.isEmpty()) {
      taskService.setVariableLocal(task.getId(), Constants.TASK_FORM_DATA_KEY, formData);
    }
    taskService.complete(task.getId());
  }

  private String getLastFinishedTaskId(String instanceId) {
    return Optional.ofNullable(getRuntimeStringVariable(instanceId, Constants.Variable.PROCESS_LAST_FINISHED_TASK_ID))
        .orElseGet(() -> Optional.ofNullable(historyService)
            .map(service -> service.createHistoricTaskInstanceQuery()
                .processInstanceId(instanceId)
                .finished()
                .orderByHistoricTaskInstanceEndTime()
                .desc()
                .listPage(0, 1))
            .filter(tasks -> !tasks.isEmpty())
            .map(tasks -> tasks.get(0).getId())
            .orElse(null));
  }

  private void dispatchInstanceEvent(String instanceId, ProcessInstance instance, FlowInstanceEvent.EventType eventType, String lastTaskId) {
    dispatchInstanceEvent(instanceId, getFlowKey(instanceId, instance), getRuntimeStringVariable(instanceId, Constants.Variable.PROCESS_STARTER),
        eventType, lastTaskId, null, null, null, null, null);
  }

  private void dispatchInstanceEvent(String instanceId, String flowKey, String starter, FlowInstanceEvent.EventType eventType, String lastTaskId) {
    dispatchInstanceEvent(instanceId, flowKey, starter, eventType, lastTaskId, null, null, null, null, null);
  }

  private void dispatchInstanceEvent(String instanceId, String flowKey, String starter, FlowInstanceEvent.EventType eventType, String lastTaskId,
                                     String outcome, FlowInstanceEvent.BusinessContext businessContext, String callbackFlow,
                                     String approvalCaseId, Integer submissionRound) {
    var event = new FlowInstanceEvent();
    event.setInstanceId(instanceId);
    event.setFlowKey(flowKey);
    event.setEventType(eventType);
    event.setEventTime(Dates.now());
    event.setStarter(starter);
    event.setLastTaskId(lastTaskId);
    event.setOutcome(outcome);
    event.setApprovalCaseId(approvalCaseId);
    event.setSubmissionRound(submissionRound);
    event.setBusinessContext(businessContext);
    event.setCallbackFlow(callbackFlow);
    instanceEventDispatcher.dispatchEvent(event);
  }

  private FlowInstanceEvent.BusinessContext businessContextOf(String instanceId) {
    var recordType = getRuntimeStringVariable(instanceId, Constants.Variable.BUSINESS_RECORD_TYPE);
    var recordId = getRuntimeStringVariable(instanceId, Constants.Variable.BUSINESS_RECORD_ID);
    if (recordType == null || recordId == null) {
      return null;
    }
    return new FlowInstanceEvent.BusinessContext(recordType, recordId);
  }

  private String getFlowKey(String instanceId, ProcessInstance instance) {
    return Optional.ofNullable(getRuntimeStringVariable(instanceId, Constants.Variable.PROCESS_FLOW_KEY))
        .orElseGet(instance::getProcessDefinitionKey);
  }

  private String getRuntimeStringVariable(String instanceId, String variableName) {
    return runtimeService.getVariable(instanceId, variableName, String.class);
  }

  private <T> Try<T> rollbackFailure(Throwable cause) {
    try {
      TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
    } catch (NoTransactionException noTransaction) {
      // Direct unit tests may invoke the service without a transaction proxy.
      return Try.failure(cause);
    }
    return Try.failure(cause);
  }

  private List<NodeFormFieldPermission> getInitialPermissions(StructuredFlowDefinition flowDefinition) {
    return Optional.ofNullable(flowDefinition)
        .flatMap(StructuredFlowDefinition::getStartFlowNode)
        .map(FlowNode::getPermission)
        .orElseGet(Collections::emptyList);
  }

  private Try<ProcessDefinition> getProcessDefinition(String flowKey) {
    return Try.of(() -> repositoryService.createProcessDefinitionQuery()
            .processDefinitionKey(flowKey)
            .latestVersion()
            .singleResult())
        .flatMap(definition -> {
          if (definition == null) {
            return Try.failure(new BusinessException("未找到已部署的流程"));
          }
          if (definition.isSuspended()) {
            return Try.failure(new BusinessException("流程已挂起"));
          }
          return Try.success(definition);
        });
  }

  private Try<ProcessInstance> getInstance(String instanceId) {
    return Try.of(() -> runtimeService.createProcessInstanceQuery()
            .processInstanceId(instanceId)
            .singleResult())
        .flatMap(instance -> instance == null
            ? Try.failure(new BusinessException("流程实例不存在"))
            : Try.success(instance));
  }

  private Optional<FlowFormService> getFlowFormService(String formType) {
    return flowFormServices.stream()
        .filter(Objects::nonNull)
        .filter(service -> service.getType().equalsIgnoreCase(formType))
        .findFirst();
  }

  private StartConditionContext buildStartConditionContext(Map<String, Object> instanceVariables, Map<String, Object> formData) {
    var persistedFormData = formData == null ? Collections.<String, Object>emptyMap() : new HashMap<>(formData);
    var conditionVariables = new HashMap<String, Object>();
    putConditionVariables(conditionVariables, persistedFormData);
    putConditionVariables(conditionVariables, instanceVariables);
    return new StartConditionContext(persistedFormData, conditionVariables);
  }

  private void putConditionVariables(Map<String, Object> target, Map<String, Object> source) {
    if (ObjectUtils.isEmpty(source)) {
      return;
    }
    source.entrySet().stream()
        .filter(entry -> !isReservedStartVariable(entry.getKey()))
        .forEach(entry -> target.put(entry.getKey(), entry.getValue()));
  }

  private boolean isReservedStartVariable(String key) {
    return StringUtils.startsWith(key, "__");
  }

  private Try<Map<String, List<String>>> filterUserSelectAssignee(String flowKey, Map<String, Object> variableMap, Map<String, List<String>> assigneeMap) {
    var sourceAssigneeMap = assigneeMap == null ? Collections.<String, List<String>>emptyMap() : assigneeMap;

    var approvalNodes = ObjectUtils.isEmpty(variableMap)
        ? flowDefinitionService.getUserSelectCandidateApprovalNodes(flowKey)
        : flowDefinitionService.getUserSelectCandidateApprovalNodes(flowKey, variableMap);
    if (!ObjectUtils.isEmpty(variableMap) && ObjectUtils.isEmpty(approvalNodes)) {
      approvalNodes = flowDefinitionService.getUserSelectCandidateApprovalNodes(flowKey);
    }

    if (ObjectUtils.isEmpty(approvalNodes)) {
      return Try.success(sourceAssigneeMap);
    }

    Map<String, List<String>> result = approvalNodes.stream()
        .collect(HashMap::new, (map, node) -> map.put(node.getId(), sourceAssigneeMap.get(node.getId())), HashMap::putAll);

    var emptyNodeIds = result.entrySet().stream()
        .filter(entry -> ObjectUtils.isEmpty(entry.getValue()))
        .map(Map.Entry::getKey)
        .collect(Collectors.toList());
    if (ObjectUtils.isNotEmpty(emptyNodeIds)) {
      var emptyNodeNames = approvalNodes.stream()
          .filter(node -> emptyNodeIds.contains(node.getId()))
          .map(FlowNode::getName)
          .collect(Collectors.joining(", "));
      return Try.failure(new BusinessException("节点：【" + emptyNodeNames + "】 未选择人员"));
    }

    var rangeFailure = UserSelectAssigneeRangeChecker.checkSingle(approvalNodes, result);
    if (rangeFailure.isPresent()) {
      return Try.failure(rangeFailure.get());
    }

    return Try.success(result);
  }

  private static final class StartConditionContext {
    private final Map<String, Object> persistedFormData;
    private final Map<String, Object> conditionVariables;

    private StartConditionContext(Map<String, Object> persistedFormData, Map<String, Object> conditionVariables) {
      this.persistedFormData = persistedFormData;
      this.conditionVariables = conditionVariables;
    }
  }
}
