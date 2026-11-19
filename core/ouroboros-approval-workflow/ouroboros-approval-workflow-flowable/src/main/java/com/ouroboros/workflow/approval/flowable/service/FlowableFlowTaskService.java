package com.ouroboros.workflow.approval.flowable.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.TaskService;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.runtime.DataObject;
import org.flowable.engine.task.Comment;
import org.flowable.identitylink.api.IdentityLink;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.flowable.task.api.history.HistoricTaskInstanceQuery;
import org.flowable.variable.api.history.HistoricVariableInstance;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.NoTransactionException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import io.vavr.control.Try;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.exception.PlatformException;
import com.ouroboros.uischema.schema.SchemaNode;
import com.ouroboros.util.Dates;
import com.ouroboros.util.Json;
import com.ouroboros.workflow.approval.BuiltInFlowTaskService;
import com.ouroboros.workflow.approval.FlowFormService;
import com.ouroboros.workflow.approval.FlowTaskAction;
import com.ouroboros.workflow.approval.FlowTaskService;
import com.ouroboros.workflow.approval.TrustedFlowTaskAction;
import com.ouroboros.workflow.approval.TrustedFlowTaskActionService;
import com.ouroboros.workflow.approval.definition.FlowNode;
import com.ouroboros.workflow.approval.definition.NodeFormFieldPermission;
import com.ouroboros.workflow.approval.definition.nodes.ApprovalNode;
import com.ouroboros.workflow.approval.definition.nodes.HandleNode;
import com.ouroboros.workflow.approval.event.TaskEventDispatcher;
import com.ouroboros.workflow.approval.flowable.transpiler.Constants;
import com.ouroboros.workflow.approval.flowable.util.BpmnUtil;
import com.ouroboros.workflow.approval.model.FlowTaskDetail;
import com.ouroboros.workflow.approval.model.TaskState;
import com.ouroboros.workflow.approval.model.events.FlowTaskEvent;

@Service
public class FlowableFlowTaskService
    implements FlowTaskService, BuiltInFlowTaskService, TrustedFlowTaskActionService {
  public static final String SYSTEM_ACTOR_USER_ID = Constants.SYSTEM_ACTOR_USER_ID;

  final TaskService taskService;

  @Autowired
  com.ouroboros.workflow.approval.flowable.event.FlowableSourceTransaction sourceTransaction;

  @Autowired
  HistoryService historyService;
  @Autowired
  RepositoryService repositoryService;
  @Autowired
  org.flowable.engine.RuntimeService runtimeService;
  @Autowired
  TaskEventDispatcher taskEventDispatcher;
  @Autowired(required = false)
  List<FlowFormService> flowFormServices = Collections.emptyList();

  List<FlowTaskAction<?>> flowTaskActions;

  public FlowableFlowTaskService(TaskService taskService, List<FlowTaskAction<?>> actions) {
    this.taskService = taskService;
    this.flowTaskActions = actions == null ? Collections.emptyList() : actions;
  }

  @Override
  @Transactional(propagation = Propagation.REQUIRED)
  public <T> Try<Void> doAction(String taskId, String actionType, String userId, String comment, Map<String, Object> formData, T payload) {
    if (Objects.equals(SYSTEM_ACTOR_USER_ID, userId)) {
      return Try.failure(new PlatformException("系统动作必须通过受信任任务动作端口执行"));
    }
    return executeAction(taskId, actionType, userId, comment, formData, payload, null, null);
  }

  @Override
  @Transactional(propagation = Propagation.REQUIRED)
  public Try<Void> execute(TrustedFlowTaskAction command) {
    if (command == null || command.taskId() == null || command.taskId().isBlank()
        || command.actionType() == null || command.actionType().isBlank()
        || command.sourceFactId() == null || command.sourceFactId().isBlank()
        || command.origin() == null) {
      return Try.failure(new PlatformException("受信任任务动作缺少必要来源事实或动作字段"));
    }
    return executeAction(
        command.taskId(), command.actionType(), null, command.comment(), command.formData(),
        command.payload(), command.origin(), command.sourceFactId());
  }

  private <T> Try<Void> executeAction(
      String taskId,
      String actionType,
      String userId,
      String comment,
      Map<String, Object> formData,
      T payload,
      TrustedFlowTaskAction.Origin origin,
      String sourceFactId) {
    if (sourceTransaction != null) { sourceTransaction.requireParticipation(); }
    boolean trusted = origin != null;
    var normalizedActionType = normalizeActionType(actionType);
    var task = taskService.createTaskQuery()
        .taskId(taskId)
        .singleResult();
    if (task == null) {
      return Try.failure(new PlatformException("未找到对应的任务: " + taskId));
    }

    var isUserInCandidateList = taskService.getIdentityLinksForTask(taskId)
        .stream()
        .map(IdentityLink::getUserId)
        .filter(Objects::nonNull)
        .anyMatch(candidateUserId -> Objects.equals(candidateUserId, userId));
    var isCurrentAssignee = Objects.equals(task.getAssignee(), userId);
    if (!trusted && !isUserInCandidateList && !isCurrentAssignee) {
      return Try.failure(new PlatformException("当前用户不在任务的候选人列表中: " + taskId));
    }

    if (!trusted && task.getAssignee() != null && !Objects.equals(task.getAssignee(), userId)) {
      return Try.failure(new PlatformException("任务已被认领"));
    }

    var authorization = verifyActionAllowed(task, normalizedActionType, origin, payload);
    if (authorization.isFailure()) {
      return Try.failure(authorization.getCause());
    }

    var action = getAction(task, normalizedActionType);
    if (!action.isPresent()) {
      return Try.failure(new PlatformException("未找到对应的流程动作: " + normalizedActionType));
    }

    if (task.getAssignee() == null && (!trusted || Constants.Value.TIMEOUT_ACTION_TRANSFER.equals(normalizedActionType))) {
      taskService.claim(taskId, trusted ? SYSTEM_ACTOR_USER_ID : userId);
      task = taskService.createTaskQuery()
          .taskId(taskId)
          .singleResult();
      if (task == null) {
        return Try.failure(new PlatformException("未找到对应的任务: " + taskId));
      }
    }

    if (formData != null) {
      var submitTry = submitTaskForm(task, userId, formData);
      if (submitTry.isFailure()) {
        return rollbackFailure(submitTry.getCause());
      }
      updateCandidateFormData(taskId, formData);
      taskService.setVariableLocal(taskId, Constants.TASK_FORM_DATA_KEY, formData);
    }

    if (payload != null) {
      taskService.setVariableLocal(taskId, Constants.TASK_PAYLOAD_KEY, Json.toJsonString(payload));
    }

    if (comment != null) {
      taskService.addComment(taskId, task.getProcessInstanceId(), comment);
    }

    if (trusted) {
      taskService.setVariableLocal(taskId, Constants.TRUSTED_SOURCE_FACT_ID_KEY, sourceFactId);
    }

    var fact = new FlowTaskEvent();
    fact.setFactKind(FlowTaskEvent.FactKind.ACTION_COMPLETED);
    fact.setInstanceId(task.getProcessInstanceId());
    fact.setTaskId(task.getId());
    fact.setTaskName(task.getName());
    fact.setNodeId(task.getTaskDefinitionKey());
    fact.setActionName(normalizedActionType);
    var process = getProcessDefinition(task.getProcessDefinitionId());
    if (process != null) { fact.setFlowKey(process.getKey()); }
    taskEventDispatcher.prepareFact(fact);
    return rollbackOnFailure(action.get().doAction(task).map(ignored -> {
      fact.setEventTime(LocalDateTime.now());
      taskEventDispatcher.dispatchEvent(fact);
      return null;
    }));
  }

  @Override
  public <T> T getPayload(String taskId, Class<T> clazz) {
    var payloadValue = taskService.getVariableLocal(taskId, Constants.TASK_PAYLOAD_KEY);
    if (payloadValue == null) {
      payloadValue = Optional.ofNullable(taskService.getDataObject(taskId, Constants.TASK_PAYLOAD_KEY))
          .map(DataObject::getValue)
          .orElse(null);
    }
    return Optional.ofNullable(payloadValue)
        .map(String::valueOf)
        .map(payload -> Json.toBean(clazz, payload))
        .orElse(null);
  }

  @Override
  public Map<String, Object> getTaskFormData(String taskId) {
    return Optional.ofNullable(taskService.getVariableLocal(taskId, Constants.TASK_FORM_DATA_KEY))
        .filter(Map.class::isInstance)
        .map(Map.class::cast)
        .orElseGet(Collections::emptyMap);
  }

  @Override
  public String getComment(String taskId) {
    return taskService.getTaskComments(taskId)
        .stream()
        .filter(Objects::nonNull)
        .max(Comparator.comparing(Comment::getTime, Comparator.nullsFirst(Date::compareTo)))
        .map(Comment::getFullMessage)
        .orElse(null);
  }

  @Override
  public void setTaskAssignee(String taskId, String userId) {
    taskService.setAssignee(taskId, userId);
  }

  @Override
  public Try<SchemaNode> getTaskForm(String taskId) {
    var task = taskService.createTaskQuery()
        .taskId(taskId)
        .singleResult();
    if (task == null) {
      return Try.failure(new PlatformException("任务不存在"));
    }

    var formKey = task.getFormKey();
    if (formKey == null) {
      return Try.failure(new PlatformException("任务表单不存在"));
    }

    var formType = getTaskFormType(taskId);
    var formId = taskService.getVariable(taskId, Constants.TASK_FORM_ID_KEY, String.class);
    var permissions = getTaskPermissions(task);
    var actions = getTaskActions(task);
    var flowFormServiceOptional = getFlowFormService(formType);
    if (!flowFormServiceOptional.isPresent()) {
      return Try.failure(new com.ouroboros.workflow.approval.FlowFormServiceUnavailableException(formType));
    }

    var schemaTry = flowFormServiceOptional.get()
        .getTaskFormSchema(task.getProcessInstanceId(), taskId, formKey, formId, permissions, actions);
    if (schemaTry.isFailure()) {
      return Try.failure(schemaTry.getCause());
    }
    if (schemaTry.get() == null) {
      return Try.failure(new PlatformException("表单不存在"));
    }
    return schemaTry;
  }

  @Override
  public List<FlowTaskDetail> getTaskList(String userId) {
    return mapActiveTaskDetails(createActiveTaskQuery(userId, null)
        .orderByTaskCreateTime()
        .desc()
        .list());
  }

  @Override
  public List<FlowTaskDetail> getTaskList(String userId, Integer offset, Integer limit) {
    return mapActiveTaskDetails(createActiveTaskQuery(userId, null)
        .orderByTaskCreateTime()
        .desc()
        .listPage(offset, limit));
  }

  @Override
  public List<FlowTaskDetail> getTaskList(String userId, String flowKey) {
    return mapActiveTaskDetails(createActiveTaskQuery(userId, flowKey)
        .orderByTaskCreateTime()
        .desc()
        .list());
  }

  @Override
  public List<FlowTaskDetail> getTaskList(String userId, String flowKey, Integer offset, Integer limit) {
    return mapActiveTaskDetails(createActiveTaskQuery(userId, flowKey)
        .orderByTaskCreateTime()
        .desc()
        .listPage(offset, limit));
  }

  @Override
  public Long getTaskCount(String userId) {
    return createActiveTaskQuery(userId, null).count();
  }

  @Override
  public Long getTaskCount(String userId, String flowKey) {
    return createActiveTaskQuery(userId, flowKey).count();
  }

  @Override
  public List<FlowTaskDetail> getHistoryTaskList(String userId, Integer offset, Integer limit) {
    return mapHistoryTaskDetails(createHistoryTaskQuery(userId, null)
        .orderByHistoricTaskInstanceEndTime()
        .desc()
        .listPage(offset, limit));
  }

  @Override
  public List<FlowTaskDetail> getHistoryTaskList(String userId, String flowKey, Integer offset, Integer limit) {
    return mapHistoryTaskDetails(createHistoryTaskQuery(userId, flowKey)
        .orderByHistoricTaskInstanceEndTime()
        .desc()
        .listPage(offset, limit));
  }

  @Override
  public Long getHistoryTaskCount(String userId) {
    return createHistoryTaskQuery(userId, null).count();
  }

  @Override
  public Long getHistoryTaskCount(String userId, String flowKey) {
    return createHistoryTaskQuery(userId, flowKey).count();
  }

  @Override
  public List<String> getCandidateUsers(String taskId) {
    Task task = taskService.createTaskQuery()
        .taskId(taskId)
        .singleResult();
    return getCandidateUsers(taskId, task);
  }

  private List<String> getCandidateUsers(String taskId, Task task) {
    Set<String> candidateUsers = getIdentityLinkCandidateUsers(taskId).stream()
        .collect(Collectors.toCollection(LinkedHashSet::new));
    candidateUsers.addAll(getMultipleApprovalCandidateUsers(taskId, task));
    return candidateUsers.stream()
        .collect(Collectors.toList());
  }

  private List<String> getIdentityLinkCandidateUsers(String taskId) {
    return taskService.getIdentityLinksForTask(taskId)
        .stream()
        .filter(Objects::nonNull)
        .filter(link -> "candidate".equalsIgnoreCase(link.getType())
            || "assignee".equalsIgnoreCase(link.getType()))
        .map(IdentityLink::getUserId)
        .filter(Objects::nonNull)
        .distinct()
        .collect(Collectors.toList());
  }

  private List<String> getMultipleApprovalCandidateUsers(String taskId, Task task) {
    if (task == null || StringUtils.isBlank(task.getTaskDefinitionKey())) {
      return Collections.emptyList();
    }
    Object value = taskService.getVariable(
        taskId,
        Constants.Variable.getMultipleApprovalAssigneeListVariableName(task.getTaskDefinitionKey()));
    if (!(value instanceof List<?>)) {
      return Collections.emptyList();
    }
    return ((List<?>) value).stream()
        .filter(Objects::nonNull)
        .map(Object::toString)
        .filter(StringUtils::isNotBlank)
        .collect(Collectors.toList());
  }

  @Override
  public void setCandidateUsers(String taskId, List<String> userIds) {
    var normalizedUserIds = Optional.ofNullable(userIds).orElse(Collections.emptyList());
    var oldUsers = getIdentityLinkCandidateUsers(taskId);
    oldUsers.stream()
        .filter(userId -> !normalizedUserIds.contains(userId))
        .forEach(userId -> taskService.deleteCandidateUser(taskId, userId));
    normalizedUserIds.stream()
        .filter(userId -> !oldUsers.contains(userId))
        .forEach(userId -> taskService.addCandidateUser(taskId, userId));
  }

  @Override
  public String getTaskAssignee(String taskId) {
    return Optional.ofNullable(taskService.createTaskQuery()
            .taskId(taskId)
            .singleResult())
        .map(Task::getAssignee)
        .orElse(null);
  }

  @Override
  public List<FlowTaskDetail> getAllActiveTaskList() {
    return mapActiveTaskDetails(taskService.createTaskQuery()
        .active()
        .orderByTaskCreateTime()
        .desc()
        .list());
  }

  @Override
  public FlowTaskDetail getTaskDetail(String taskId) {
    return Optional.ofNullable(taskService.createTaskQuery()
            .taskId(taskId)
            .singleResult())
        .map(this::mapActiveTaskDetail)
        .orElse(null);
  }

  /**
   * 按动作名取处理器。
   *
   * <p>此处的未检查强转来自 core 的 {@code FlowTaskAction<T>} 泛型签名：装配注入的是
   * {@code List<FlowTaskAction<?>>}（各实现绑定自己的任务类型），按动作选出后要交给
   * {@code doAction(Task)} 使用。已用 {@code supportTask(task)} 过滤，运行时类型安全，
   * 但编译器无法证明。消除它需要把 {@code FlowTaskAction} 改成非泛型接口
   * （{@code doAction(Object)} + 各实现自行收窄），属 core SPI 变更，需单独决策。
   */
  @SuppressWarnings("unchecked")
  private Optional<FlowTaskAction<Task>> getAction(Task task, String action) {
    return Optional.ofNullable(flowTaskActions)
        .orElse(Collections.emptyList())
        .stream()
        .filter(flowTaskAction -> flowTaskAction.supportTask(task))
        .filter(flowTaskAction -> flowTaskAction.actionName().equalsIgnoreCase(action))
        .map(flowTaskAction -> (FlowTaskAction<Task>) flowTaskAction)
        .findFirst();
  }

  private <T> Try<Void> verifyActionAllowed(
      Task task, String actionType, TrustedFlowTaskAction.Origin origin, T payload) {
    var runtimeTaskAllowedActions = getRuntimeDerivativeTaskAllowedActions(task);
    if (runtimeTaskAllowedActions.isPresent()) {
      if (origin == null && isAllowedAction(runtimeTaskAllowedActions.get(), actionType)) {
        return Try.success(null);
      }
      if (origin == TrustedFlowTaskAction.Origin.EXTERNAL_CALLBACK
          && isExternalCallbackActionAllowed(runtimeTaskAllowedActions.get(), actionType)) {
        return Try.success(null);
      }
      return Try.failure(new PlatformException("当前节点未配置流程动作: " + actionType));
    }

    var flowNode = getTaskFlowNode(task);
    if (!flowNode.isPresent()) {
      return Try.failure(new PlatformException("未找到当前任务对应的流程节点: " + task.getId()));
    }

    if (isActionAllowed(flowNode.get(), actionType, origin, payload)) {
      return Try.success(null);
    }
    return Try.failure(new PlatformException("当前节点未配置流程动作: " + actionType));
  }

  private <T> boolean isActionAllowed(
      FlowNode flowNode, String actionType, TrustedFlowTaskAction.Origin origin, T payload) {
    if (origin == TrustedFlowTaskAction.Origin.TIMEOUT) {
      return isSystemActorActionAllowed(flowNode, actionType, payload);
    }
    if (origin == TrustedFlowTaskAction.Origin.EXTERNAL_CALLBACK) {
      return isExternalCallbackActionAllowed(getHumanTaskAllowedActions(flowNode), actionType);
    }
    return getHumanTaskAllowedActions(flowNode).stream()
        .map(this::normalizeActionType)
        .anyMatch(allowedAction -> StringUtils.equalsIgnoreCase(allowedAction, actionType));
  }

  private boolean isAllowedAction(List<String> allowedActions, String actionType) {
    return Optional.ofNullable(allowedActions)
        .orElseGet(Collections::emptyList)
        .stream()
        .map(this::normalizeActionType)
        .anyMatch(allowedAction -> StringUtils.equalsIgnoreCase(allowedAction, actionType));
  }

  private boolean isExternalCallbackActionAllowed(List<String> allowedActions, String actionType) {
    if (!Constants.Value.APPROVED.equals(actionType) && !Constants.Value.REJECTED.equals(actionType)) {
      return false;
    }
    return isAllowedAction(allowedActions, actionType);
  }

  private Optional<List<String>> getRuntimeDerivativeTaskAllowedActions(Task task) {
    if (task == null || StringUtils.isBlank(task.getId()) || taskService == null) {
      return Optional.empty();
    }

    var addSignTaskKind = taskService.getVariableLocal(
        task.getId(),
        Constants.Variable.ADD_SIGN_TASK_KIND,
        String.class);
    if (StringUtils.isBlank(addSignTaskKind)) {
      return Optional.empty();
    }
    if (Constants.Value.ADD_SIGN_TASK_KIND_ADDED.equals(addSignTaskKind)
        || Constants.Value.ADD_SIGN_TASK_KIND_RESUME.equals(addSignTaskKind)) {
      return Optional.of(Collections.singletonList(Constants.Value.APPROVED));
    }
    return Optional.of(Collections.emptyList());
  }

  private List<String> getHumanTaskAllowedActions(FlowNode flowNode) {
    if (flowNode instanceof HandleNode) {
      return Collections.singletonList(Constants.Value.COMPLETE);
    }
    if (flowNode instanceof ApprovalNode) {
      List<String> actions = new ArrayList<>();
      actions.add(Constants.Value.APPROVED);
      Optional.ofNullable(((ApprovalNode) flowNode).getActions())
          .orElseGet(Collections::emptyList)
          .stream()
          .filter(StringUtils::isNotBlank)
          .map(this::normalizeActionType)
          .filter(action -> actions.stream().noneMatch(existing -> StringUtils.equalsIgnoreCase(existing, action)))
          .forEach(actions::add);
      return actions;
    }
    return Collections.emptyList();
  }

  private <T> boolean isSystemActorActionAllowed(FlowNode flowNode, String actionType, T payload) {
    if (!(flowNode instanceof ApprovalNode)) {
      return false;
    }

    ApprovalNode.Timeout timeout = ((ApprovalNode) flowNode).getTimeout();
    if (timeout == null) {
      return false;
    }

    var timeoutAction = normalizeTimeoutAction(timeout.getAction());
    if (!timeoutAction.isPresent() || !StringUtils.equalsIgnoreCase(timeoutAction.get(), actionType)) {
      return false;
    }

    if (Constants.Value.TIMEOUT_ACTION_TRANSFER.equals(timeoutAction.get())) {
      String transferTo = StringUtils.trimToNull(timeout.getTransferTo());
      return StringUtils.isNotBlank(transferTo)
          && StringUtils.equals(transferTo, resolveTransferTargetUserId(payload));
    }
    return true;
  }

  private Optional<String> normalizeTimeoutAction(String timeoutAction) {
    if (StringUtils.equals(Constants.Value.TIMEOUT_ACTION_AUTO_APPROVE, timeoutAction)) {
      return Optional.of(Constants.Value.APPROVED);
    }
    if (StringUtils.equals(Constants.Value.TIMEOUT_ACTION_AUTO_REJECT, timeoutAction)) {
      return Optional.of(Constants.Value.REJECTED);
    }
    if (StringUtils.equals(Constants.Value.TIMEOUT_ACTION_TRANSFER, timeoutAction)) {
      return Optional.of(Constants.Value.TIMEOUT_ACTION_TRANSFER);
    }
    return Optional.empty();
  }

  private String resolveTransferTargetUserId(Object payload) {
    if (payload == null) {
      return null;
    }
    if (payload instanceof Map<?, ?>) {
      Object targetUserId = ((Map<?, ?>) payload).get("targetUserId");
      return targetUserId == null ? null : String.valueOf(targetUserId);
    }
    Object targetUserId = Json.toMap(Json.toJsonString(payload)).get("targetUserId");
    return targetUserId == null ? null : String.valueOf(targetUserId);
  }

  private String normalizeActionType(String actionType) {
    String normalizedActionType = StringUtils.trimToEmpty(actionType);
    if (StringUtils.equalsIgnoreCase("freeApproval", normalizedActionType)) {
      return Constants.Value.FREE_APPROVE;
    }
    if (StringUtils.equalsIgnoreCase(Constants.Value.APPROVED, normalizedActionType)) {
      return Constants.Value.APPROVED;
    }
    if (StringUtils.equalsIgnoreCase(Constants.Value.REJECTED, normalizedActionType)) {
      return Constants.Value.REJECTED;
    }
    if (StringUtils.equalsIgnoreCase(Constants.Value.ROLLBACK, normalizedActionType)) {
      return Constants.Value.ROLLBACK;
    }
    if (StringUtils.equalsIgnoreCase(Constants.Value.COMPLETE, normalizedActionType)) {
      return Constants.Value.COMPLETE;
    }
    if (StringUtils.equalsIgnoreCase(Constants.Value.TIMEOUT_ACTION_TRANSFER, normalizedActionType)) {
      return Constants.Value.TIMEOUT_ACTION_TRANSFER;
    }
    if (StringUtils.equalsIgnoreCase(Constants.Value.ADD_SIGN, normalizedActionType)) {
      return Constants.Value.ADD_SIGN;
    }
    if (StringUtils.equalsIgnoreCase(Constants.Value.FREE_APPROVE, normalizedActionType)) {
      return Constants.Value.FREE_APPROVE;
    }
    return normalizedActionType;
  }

  private Try<Void> submitTaskForm(Task task, String userId, Map<String, Object> formData) {
    var formKey = task.getFormKey();
    if (formKey == null) {
      return Try.success(null);
    }

    var flowFormServiceOptional = getFlowFormService(getTaskFormType(task.getId()));
    if (!flowFormServiceOptional.isPresent()) {
      return Try.failure(new com.ouroboros.workflow.approval.FlowFormServiceUnavailableException(getTaskFormType(task.getId())));
    }

    return flowFormServiceOptional.get().submitTaskForm(
        task.getProcessInstanceId(),
        task.getId(),
        formKey,
        taskService.getVariable(task.getId(), Constants.TASK_FORM_ID_KEY, String.class),
        userId,
        getTaskPermissions(task),
        formData);
  }

  private void updateCandidateFormData(String taskId, Map<String, Object> formData) {
    if (formData == null || formData.isEmpty()) {
      return;
    }
    Map<String, Object> mergedFormData = new HashMap<>();
    Object existingFormData = taskService.getVariable(taskId, Constants.CANDIDATE_FORM_DATA_KEY);
    if (existingFormData instanceof Map<?, ?>) {
      ((Map<?, ?>) existingFormData).forEach((key, value) -> {
        if (key instanceof String) {
          mergedFormData.put((String) key, value);
        }
      });
    }
    mergedFormData.putAll(formData);
    taskService.setVariable(taskId, Constants.CANDIDATE_FORM_DATA_KEY, mergedFormData);
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

  private <T> Try<T> rollbackOnFailure(Try<T> result) {
    if (result.isFailure()) {
      try {
        TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
      } catch (NoTransactionException noTransaction) {
        // Direct unit tests may invoke the service without a transaction proxy.
        return result;
      }
    }
    return result;
  }

  private String getTaskFormType(String taskId) {
    return StringUtils.defaultIfBlank(taskService.getVariable(taskId, Constants.TASK_FORM_TYPE_KEY, String.class), "default");
  }

  private Optional<FlowFormService> getFlowFormService(String formType) {
    return Optional.ofNullable(flowFormServices)
        .orElse(Collections.emptyList())
        .stream()
        .filter(flowFormService -> StringUtils.equalsIgnoreCase(flowFormService.getType(), formType))
        .findFirst();
  }

  private List<NodeFormFieldPermission> getTaskPermissions(Task task) {
    return getTaskFlowNode(task)
        .map(FlowNode::getPermission)
        .orElseGet(Collections::emptyList);
  }

  private List<String> getTaskActions(Task task) {
    return getTaskFlowNode(task)
        .map(flowNode -> {
          if (flowNode instanceof HandleNode) {
            return Collections.singletonList(Constants.Value.COMPLETE);
          }
          if (flowNode instanceof ApprovalNode) {
            return Optional.ofNullable(((ApprovalNode) flowNode).getActions())
                .map(ArrayList::new)
                .orElseGet(ArrayList::new);
          }
          return Collections.<String>emptyList();
        })
        .orElseGet(ArrayList::new);
  }

  private Optional<FlowNode> getTaskFlowNode(Task task) {
    if (repositoryService == null
        || task == null
        || StringUtils.isBlank(task.getProcessDefinitionId())
        || StringUtils.isBlank(task.getTaskDefinitionKey())) {
      return Optional.empty();
    }
    return Optional.ofNullable(BpmnUtil.getFlowNodeSupplier(
        repositoryService,
        task.getProcessDefinitionId(),
        task.getTaskDefinitionKey()
    ).get());
  }

  private TaskQuery createActiveTaskQuery(String userId, String flowKey) {
    var query = taskService.createTaskQuery()
        .active()
        .taskCandidateOrAssigned(userId);
    if (flowKey != null) {
      query = query.processDefinitionKey(flowKey);
    }
    return query;
  }

  private HistoricTaskInstanceQuery createHistoryTaskQuery(String userId, String flowKey) {
    var query = historyService.createHistoricTaskInstanceQuery()
        .taskAssignee(userId)
        .finished();
    if (flowKey != null) {
      query = query.processDefinitionKey(flowKey);
    }
    return query;
  }

  private List<FlowTaskDetail> mapActiveTaskDetails(List<Task> tasks) {
    return Optional.ofNullable(tasks).orElse(Collections.emptyList())
        .stream()
        .map(this::mapActiveTaskDetail)
        .collect(Collectors.toList());
  }

  private FlowTaskDetail mapActiveTaskDetail(Task task) {
    var historyTask = historyService.createHistoricTaskInstanceQuery()
        .taskId(task.getId())
        .singleResult();
    var definition = getProcessDefinition(task.getProcessDefinitionId());
    var taskDetail = new FlowTaskDetail();
    taskDetail.setId(task.getId());
    taskDetail.setName(task.getName());
    taskDetail.setInstanceId(task.getProcessInstanceId());
    taskDetail.setTaskDefinitionKey(task.getTaskDefinitionKey());
    taskDetail.setFlowKey(Optional.ofNullable(definition).map(ProcessDefinition::getKey).orElse(null));
    taskDetail.setFlowName(Optional.ofNullable(definition).map(ProcessDefinition::getName).orElse(null));
    taskDetail.setStarter(getProcessStarterFromList(task.getProcessInstanceId()));
    taskDetail.setAssignee(task.getAssignee());
    taskDetail.setCandidateUsers(getCandidateUsers(task.getId(), task));
    taskDetail.setActions(getTaskDetailActions(task));
    taskDetail.setFields(getTaskFieldPermissions(task));
    taskDetail.setStartTime(Optional.ofNullable(historyTask).map(HistoricTaskInstance::getStartTime).map(this::toLocalDateTime).orElse(null));
    taskDetail.setEndTime(Optional.ofNullable(historyTask).map(HistoricTaskInstance::getEndTime).map(this::toLocalDateTime).orElse(null));
    taskDetail.setActive(true);
    return taskDetail;
  }

  private List<String> getTaskDetailActions(Task task) {
    var runtimeTaskAllowedActions = getRuntimeDerivativeTaskAllowedActions(task);
    if (runtimeTaskAllowedActions.isPresent()) {
      return runtimeTaskAllowedActions.get();
    }
    return getTaskFlowNode(task)
        .map(this::getHumanTaskAllowedActions)
        .orElseGet(Collections::emptyList);
  }

  private Map<String, Object> getTaskFieldPermissions(Task task) {
    var permissions = getTaskPermissions(task);
    if (permissions.isEmpty()) {
      return Collections.emptyMap();
    }

    var fields = new LinkedHashMap<String, Object>();
    permissions.stream()
        .filter(Objects::nonNull)
        .filter(permission -> StringUtils.isNotBlank(permission.getName()))
        .forEach(permission -> {
          var field = new LinkedHashMap<String, Object>();
          field.put("readable", permission.isVisible());
          field.put("writable", permission.isWritable());
          field.put("required", permission.isRequired());
          if (StringUtils.isNotBlank(permission.getLabel())) {
            field.put("label", permission.getLabel());
          }
          fields.put(permission.getName(), field);
        });
    return fields;
  }

  private List<FlowTaskDetail> mapHistoryTaskDetails(List<HistoricTaskInstance> tasks) {
    return Optional.ofNullable(tasks).orElse(Collections.emptyList())
        .stream()
        .map(this::mapHistoryTaskDetail)
        .collect(Collectors.toList());
  }

  private FlowTaskDetail mapHistoryTaskDetail(HistoricTaskInstance task) {
    var definition = getProcessDefinition(task.getProcessDefinitionId());
    var taskDetail = new FlowTaskDetail();
    taskDetail.setId(task.getId());
    taskDetail.setName(task.getName());
    taskDetail.setInstanceId(task.getProcessInstanceId());
    taskDetail.setTaskDefinitionKey(task.getTaskDefinitionKey());
    taskDetail.setFlowKey(Optional.ofNullable(definition).map(ProcessDefinition::getKey).orElse(null));
    taskDetail.setFlowName(Optional.ofNullable(definition).map(ProcessDefinition::getName).orElse(null));
    taskDetail.setStarter(getProcessStarter(task.getProcessInstanceId()));
    taskDetail.setAssignee(task.getAssignee());
    taskDetail.setCandidateUsers(Collections.emptyList());
    taskDetail.setStartTime(toLocalDateTime(task.getStartTime()));
    taskDetail.setEndTime(toLocalDateTime(task.getEndTime()));
    taskDetail.setActive(false);
    return taskDetail;
  }

  private ProcessDefinition getProcessDefinition(String processDefinitionId) {
    if (repositoryService == null || processDefinitionId == null) {
      return null;
    }
    return repositoryService.getProcessDefinition(processDefinitionId);
  }

  private String getProcessStarterFromList(String processInstanceId) {
    if (historyService == null || processInstanceId == null) {
      return null;
    }
    return historyService.createHistoricVariableInstanceQuery()
        .processInstanceId(processInstanceId)
        .variableName(Constants.Variable.PROCESS_STARTER)
        .listPage(0, 1)
        .stream()
        .findFirst()
        .map(HistoricVariableInstance::getValue)
        .map(String::valueOf)
        .orElse(null);
  }

  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Try<Void> remind(String taskId, String userId) {
    var task = taskService.createTaskQuery().taskId(taskId).singleResult();
    if (task == null) {
      return Try.failure(new BusinessException("任务不存在或已结束: " + taskId));
    }
    // 权限：发起人或当前处理人（候选人也允许，便于候选人查看时催办）
    var starter = getProcessStarter(task.getProcessInstanceId());
    boolean assignee = Objects.equals(task.getAssignee(), userId);
    boolean candidate = taskService.getIdentityLinksForTask(taskId)
        .stream()
        .map(IdentityLink::getUserId)
        .anyMatch(userId::equals);
    if (!Objects.equals(starter, userId) && !assignee && !candidate) {
      return Try.failure(new BusinessException("无权催办该任务: " + taskId));
    }
    // 幂等：5 分钟内重复催办只发一次
    LocalDateTime lastRemindAt = taskService.getVariable(taskId, Constants.Variable.REMIND_LAST_AT, LocalDateTime.class);
    if (lastRemindAt != null && lastRemindAt.plusMinutes(5).isAfter(LocalDateTime.now())) {
      return Try.success(null);
    }
    taskService.setVariableLocal(taskId, Constants.Variable.REMIND_LAST_AT, LocalDateTime.now());

    var event = new FlowTaskEvent();
    event.setTaskId(taskId);
    event.setTaskName(task.getName());
    event.setAssignee(task.getAssignee());
    event.setInstanceId(task.getProcessInstanceId());
    event.setState(TaskState.TIMEOUT_REMIND);
    event.setEventTime(LocalDateTime.now());
    Optional.ofNullable(repositoryService)
        .map(repo -> repo.createProcessDefinitionQuery().processDefinitionId(task.getProcessDefinitionId()).singleResult())
        .ifPresent(def -> event.setNotification(BpmnUtil.readNotification(repositoryService, def.getId())));
    taskEventDispatcher.dispatchEvent(event);
    return Try.success(null);
  }

  private String getProcessStarter(String processInstanceId) {
    if (historyService == null || processInstanceId == null) {
      return null;
    }
    return Optional.ofNullable(historyService.createHistoricVariableInstanceQuery()
            .processInstanceId(processInstanceId)
            .variableName(Constants.Variable.PROCESS_STARTER)
            .singleResult())
        .map(HistoricVariableInstance::getValue)
        .map(String::valueOf)
        .orElse(null);
  }

  private LocalDateTime toLocalDateTime(Date date) {
    return date == null ? null : Dates.toLocalDateTime(date);
  }
}
