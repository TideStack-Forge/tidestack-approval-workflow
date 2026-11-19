package com.ouroboros.workflow.approval.flowable.service;

import static com.ouroboros.workflow.approval.flowable.util.BpmnUtil.readFlowDefinition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.history.HistoricProcessInstanceQuery;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.repository.ProcessDefinitionQuery;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.task.Comment;
import org.flowable.identitylink.api.IdentityLink;
import org.flowable.identitylink.api.history.HistoricIdentityLink;
import org.flowable.task.api.Task;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.flowable.variable.api.history.HistoricVariableInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import io.vavr.control.Try;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.uischema.schema.SchemaNode;
import com.ouroboros.util.Converter;
import com.ouroboros.util.Dates;
import com.ouroboros.workflow.approval.CurrentUserSupplier;
import com.ouroboros.workflow.approval.FlowFormService;
import com.ouroboros.workflow.approval.FlowInitialFormResolver;
import com.ouroboros.workflow.approval.definition.FlowDefinition;
import com.ouroboros.workflow.approval.definition.FlowNode;
import com.ouroboros.workflow.approval.definition.codec.FlowDefinitionV2Normalizer;
import com.ouroboros.workflow.approval.definition.graph.StructuredFlowDefinition;
import com.ouroboros.workflow.approval.definition.nodes.BuiltInFlowNodeTypes;
import com.ouroboros.workflow.approval.definition.nodes.MultipleApprovalNode;
import com.ouroboros.workflow.approval.flowable.util.BpmnUtil;
import com.ouroboros.workflow.approval.flowable.transpiler.Constants;
import com.ouroboros.workflow.approval.model.FlowDurationStatistic;
import com.ouroboros.workflow.approval.model.FlowInstanceDetail;
import com.ouroboros.workflow.approval.model.FlowInstanceStatistic;
import com.ouroboros.workflow.approval.model.FlowProcessInfo;
import com.ouroboros.workflow.approval.model.FlowTimeline;
import com.ouroboros.workflow.approval.model.TaskState;

@Service
public class FlowInstanceQueryService {
  private static final String INSTANCE_STATUS_RUNNING = "running";
  private static final String INSTANCE_STATUS_SUSPENDED = "suspended";
  private static final String INSTANCE_STATUS_FINISHED = "finished";
  private static final int RUNTIME_STATE_SCAN_BATCH_SIZE = 100;
  private final FlowDefinitionV2Normalizer flowDefinitionV2Normalizer = new FlowDefinitionV2Normalizer();

  final TaskService taskService;
  final RuntimeService runtimeService;
  final List<FlowFormService> flowFormServices;

  RepositoryService repositoryService;
  HistoryService historyService;

  @Autowired
  public FlowInstanceQueryService(
      TaskService taskService,
      RuntimeService runtimeService,
      RepositoryService repositoryService,
      HistoryService historyService,
      List<FlowFormService> flowFormServices) {
    this(taskService, runtimeService, flowFormServices);
    this.repositoryService = repositoryService;
    this.historyService = historyService;
  }

  public FlowInstanceQueryService(TaskService taskService, RuntimeService runtimeService, List<FlowFormService> flowFormServices) {
    this.taskService = taskService;
    this.runtimeService = runtimeService;
    this.flowFormServices = flowFormServices == null ? Collections.emptyList() : flowFormServices;
  }

  public List<FlowTimeline> getTimeLine(String instanceId) {
    var historicInstance = getHistoricInstance(instanceId).getOrNull();
    if (historicInstance == null) {
      return Collections.emptyList();
    }
    var suspended = isRuntimeInstanceSuspended(instanceId);

    var flowDefinition = getInstanceStructuredFlowDefinition(instanceId);
    if (flowDefinition == null || ObjectUtils.isEmpty(flowDefinition.getNodes())) {
      return Collections.emptyList();
    }

    var flowNodeMap = flowDefinition.getNodes().stream()
        .filter(Objects::nonNull)
        .collect(Collectors.toMap(FlowNode::getId, node -> node, (left, right) -> right, LinkedHashMap::new));

    var variables = historyService.createHistoricVariableInstanceQuery()
        .processInstanceId(instanceId)
        .list();
    var starter = getHistoricStringVariable(variables, Constants.Variable.PROCESS_STARTER);

    var logs = historyService.createHistoricTaskInstanceQuery()
        .processInstanceId(instanceId)
        .orderByHistoricTaskInstanceStartTime()
        .asc()
        .list();
    var runtimeTaskParticipants = getRuntimeTaskParticipants(instanceId);

    var timelines = new ArrayList<FlowTimeline>();
    var multipleSessions = new LinkedHashMap<String, FlowTimeline>();
    var multipleSessionAssignees = new HashMap<String, List<String>>();

    logs.stream()
        .map(task -> mapTimelineItem(
            task,
            flowNodeMap.get(task.getTaskDefinitionKey()),
            suspended,
            runtimeTaskParticipants.get(task.getId()),
            starter))
        .forEach(timelineItem -> appendTimelineItem(
            timelines,
            multipleSessions,
            multipleSessionAssignees,
            timelineItem,
            variables,
            suspended));

    multipleSessions.forEach((session, timeline) -> completeMultipleApprovalTimeline(instanceId, timeline, multipleSessionAssignees.get(session)));
    return timelines;
  }

  public Try<SchemaNode> getInitialForm(String flowKey) {
    var definitionTry = getProcessDefinition(flowKey);
    if (definitionTry.isFailure()) {
      return Try.failure(definitionTry.getCause());
    }

    var process = repositoryService.getBpmnModel(definitionTry.get().getId()).getMainProcess();
    var flowDefinition = BpmnUtil.readStructuredFlowDefinition(process);
    if (flowDefinition == null) {
      return Try.failure(new BusinessException("审批流定义不存在"));
    }

    return new FlowInitialFormResolver(flowFormServices).resolve(flowDefinition);
  }

  public List<FlowInstanceStatistic> getStatistic(String keyword, String status, Integer offset, Integer limit) {
    return createStatisticDefinitionQuery(keyword, status)
        .orderByProcessDefinitionKey()
        .asc()
        .listPage(offset, limit)
        .stream()
        .map(this::mapStatistic)
        .collect(Collectors.toList());
  }

  public Long getStatisticCount(String keyword, String status) {
    return createStatisticDefinitionQuery(keyword, status).count();
  }

  public FlowDurationStatistic getDurationStatistic(String flowKey, Date from, Date to) {
    if (!processDefinitionExists(flowKey)) {
      throw new BusinessException("flow_not_found");
    }

    var finishedInstances = historyService.createHistoricProcessInstanceQuery()
        .processDefinitionKey(flowKey)
        .finished()
        .list()
        .stream()
        .filter(instance -> isWithinRange(instance.getStartTime(), instance.getEndTime(), from, to))
        .collect(Collectors.toList());

    var instanceIds = finishedInstances.stream()
        .map(HistoricProcessInstance::getId)
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());

    var tasks = instanceIds.isEmpty()
        ? Collections.<HistoricTaskInstance>emptyList()
        : historyService.createHistoricTaskInstanceQuery()
            .processDefinitionKey(flowKey)
            .finished()
            .list()
            .stream()
            .filter(task -> instanceIds.contains(task.getProcessInstanceId()))
            .filter(task -> isWithinRange(task.getStartTime(), task.getEndTime(), from, to))
            .collect(Collectors.toList());

    var statistic = new FlowDurationStatistic();
    statistic.setFlowKey(flowKey);
    statistic.setInstanceCount((long) finishedInstances.size());
    statistic.setAvgDurationMillis(averageDuration(finishedInstances));
    statistic.setNodes(buildNodeDurationStatistics(tasks));
    statistic.setApprovers(buildApproverDurationStatistics(tasks));
    return statistic;
  }

  public List<FlowInstanceDetail> getInstances(String flowKey, String status, Integer offset, Integer limit) {
    if (isRuntimeStatus(status)) {
      return getRuntimeStateInstances(flowKey, status, offset, limit);
    }

    var historicInstances = createHistoricProcessInstanceQuery(flowKey, status)
        .listPage(offset, limit);
    var runtimeStatuses = resolveRuntimeStatuses(historicInstances, status);
    return historicInstances
        .stream()
        .map(instance -> mapInstanceDetail(instance, resolveHistoricStatus(instance, status, runtimeStatuses)))
        .collect(Collectors.toList());
  }

  public Long getInstancesCount(String flowKey, String status) {
    if (isRuntimeStatus(status)) {
      return countRuntimeStateInstances(flowKey, status);
    }
    return createHistoricProcessInstanceQuery(flowKey, status).count();
  }

  public Map<String, FlowProcessInfo> getProcessInfoByApprovalUrls(String flowKey, List<String> approvalUrls) {
    if (StringUtils.isBlank(flowKey) || ObjectUtils.isEmpty(approvalUrls)) {
      return Collections.emptyMap();
    }

    Map<String, FlowProcessInfo> result = new LinkedHashMap<String, FlowProcessInfo>();
    approvalUrls.stream()
        .filter(StringUtils::isNotBlank)
        .distinct()
        .forEach(approvalUrl -> findLatestProcessByApprovalUrl(flowKey, approvalUrl)
            .ifPresent(instance -> result.put(approvalUrl, mapProcessInfo(instance))));
    return result;
  }

  public FlowDefinition getInstanceFlowDefinition(String instanceId) {
    var historicInstance = getHistoricInstance(instanceId).getOrNull();
    if (historicInstance == null) {
      return null;
    }
    return readFlowDefinition(repositoryService.getBpmnModel(historicInstance.getProcessDefinitionId()));
  }

  private StructuredFlowDefinition getInstanceStructuredFlowDefinition(String instanceId) {
    var historicInstance = getHistoricInstance(instanceId).getOrNull();
    if (historicInstance == null) {
      return null;
    }
    return BpmnUtil.readStructuredFlowDefinition(repositoryService.getBpmnModel(historicInstance.getProcessDefinitionId()));
  }

  public Try<JsonNode> getInstanceFlowDefinitionV2(String instanceId) {
    return getHistoricInstance(instanceId)
        .flatMap(instance -> {
          if (instance == null) {
            return Try.failure(new BusinessException("流程实例不存在"));
          }
          return Try.success(instance);
        })
        .map(instance -> repositoryService.getBpmnModel(instance.getProcessDefinitionId()))
        .map(BpmnUtil::readFlowDefinitionRawJson)
        .flatMap(flowDefinitionV2Normalizer::normalizeToJsonNode);
  }

  public List<String> getActiveActivityIds(String instanceId) {
    var runtimeInstance = runtimeService.createProcessInstanceQuery()
        .processInstanceId(instanceId)
        .singleResult();
    if (runtimeInstance == null) {
      return Collections.emptyList();
    }
    return runtimeService.getActiveActivityIds(instanceId);
  }

  private Optional<HistoricProcessInstance> findLatestProcessByApprovalUrl(String flowKey, String approvalUrl) {
    return historyService.createHistoricVariableInstanceQuery()
        .variableValueEquals("approvalUrl", approvalUrl)
        .list()
        .stream()
        .map(HistoricVariableInstance::getProcessInstanceId)
        .filter(StringUtils::isNotBlank)
        .distinct()
        .map(instanceId -> getHistoricInstance(instanceId).getOrNull())
        .filter(Objects::nonNull)
        .filter(instance -> StringUtils.equals(flowKey, instance.getProcessDefinitionKey()))
        .max(Comparator.comparing(HistoricProcessInstance::getStartTime, Comparator.nullsFirst(Date::compareTo)));
  }

  public Try<FlowProcessInfo> getProcessInfo(String instanceId) {
    return getHistoricInstance(instanceId).flatMap(instance -> instance == null
        ? Try.failure(new BusinessException("流程实例不存在: " + instanceId))
        : Try.of(() -> mapProcessInfo(instance)));
  }

  private FlowProcessInfo mapProcessInfo(HistoricProcessInstance instance) {
    var runtimeStatuses = resolveRuntimeStatuses(Collections.singletonList(instance), null);
    var info = new FlowProcessInfo();
    info.setInstanceId(instance.getId());
    info.setFlowKey(instance.getProcessDefinitionKey());
    info.setStarter(getProcessStringVariable(instance.getId(), Constants.Variable.PROCESS_STARTER));
    info.setStartTime(Optional.ofNullable(instance.getStartTime()).map(Dates::toLocalDateTime).orElse(null));
    info.setEndTime(Optional.ofNullable(instance.getEndTime()).map(Dates::toLocalDateTime).orElse(null));
    info.setStatus(resolveHistoricStatus(instance, null, runtimeStatuses));
    info.setDiagramAvailable(true);
    info.setTimelineAvailable(true);
    info.setCancellable("running".equals(info.getStatus()) || "suspended".equals(info.getStatus()));
    info.setOutcome(getProcessStringVariable(instance.getId(), Constants.Variable.PROCESS_OUTCOME));
    var tasks = mapActiveProcessTasks(instance);
    info.setTasks(tasks);
    applyCurrentUserProcessableTask(info, tasks);
    return info;
  }

  private String getProcessStringVariable(String instanceId, String variableName) {
    return Optional.ofNullable(historyService.createHistoricVariableInstanceQuery()
            .processInstanceId(instanceId)
            .variableName(variableName)
            .singleResult())
        .map(HistoricVariableInstance::getValue)
        .map(String::valueOf)
        .orElse(null);
  }

  private List<FlowProcessInfo.TaskInfo> mapActiveProcessTasks(HistoricProcessInstance instance) {
    var runtimeInstance = runtimeService.createProcessInstanceQuery()
        .processInstanceId(instance.getId())
        .singleResult();
    if (runtimeInstance == null) {
      return Collections.emptyList();
    }

    var nodeTypes = resolveNodeTypes(instance.getProcessDefinitionId());
    return taskService.createTaskQuery()
        .processInstanceId(instance.getId())
        .active()
        .orderByTaskCreateTime()
        .asc()
        .list()
        .stream()
        .map(task -> mapActiveProcessTask(task, nodeTypes))
        .collect(Collectors.toList());
  }

  private FlowProcessInfo.TaskInfo mapActiveProcessTask(Task task, Map<String, String> nodeTypes) {
    var taskDefinitionKey = task.getTaskDefinitionKey();
    var taskInfo = new FlowProcessInfo.TaskInfo();
    taskInfo.setTaskId(task.getId());
    taskInfo.setTaskName(task.getName());
    taskInfo.setTaskDefinitionKey(taskDefinitionKey);
    taskInfo.setNodeType(Optional.ofNullable(nodeTypes.get(taskDefinitionKey)).orElse(taskDefinitionKey));
    taskInfo.setAssignee(task.getAssignee());
    taskInfo.setStartTime(Optional.ofNullable(task.getCreateTime()).map(Dates::toLocalDateTime).orElse(null));
    taskInfo.setActive(true);
    return taskInfo;
  }

  private void applyCurrentUserProcessableTask(FlowProcessInfo info, List<FlowProcessInfo.TaskInfo> tasks) {
    var currentUserId = CurrentUserSupplier.getCurrentUserId().orElse(null);
    if (StringUtils.isBlank(currentUserId) || ObjectUtils.isEmpty(tasks)) {
      info.setProcessable(false);
      return;
    }

    tasks.stream()
        .filter(this::isUserProcessableTask)
        .filter(task -> isTaskProcessableByUser(task, currentUserId))
        .findFirst()
        .ifPresentOrElse(task -> {
          info.setProcessable(true);
          info.setProcessableTaskId(task.getTaskId());
        }, () -> info.setProcessable(false));
  }

  private boolean isUserProcessableTask(FlowProcessInfo.TaskInfo task) {
    if (task == null || !Boolean.TRUE.equals(task.isActive())) {
      return false;
    }
    var nodeType = StringUtils.defaultString(task.getNodeType());
    return !StringUtils.equalsAnyIgnoreCase(nodeType, "START", "StartNode", "发起节点");
  }

  private boolean isTaskProcessableByUser(FlowProcessInfo.TaskInfo task, String currentUserId) {
    if (StringUtils.equals(task.getAssignee(), currentUserId)) {
      return true;
    }
    return taskService.getIdentityLinksForTask(task.getTaskId())
        .stream()
        .filter(Objects::nonNull)
        .filter(link -> "candidate".equalsIgnoreCase(link.getType()) || "assignee".equalsIgnoreCase(link.getType()))
        .map(IdentityLink::getUserId)
        .filter(Objects::nonNull)
        .anyMatch(userId -> StringUtils.equals(userId, currentUserId));
  }

  private Map<String, String> resolveNodeTypes(String processDefinitionId) {
    if (StringUtils.isBlank(processDefinitionId)) {
      return Collections.emptyMap();
    }
    var flowDefinition = BpmnUtil.readStructuredFlowDefinition(repositoryService.getBpmnModel(processDefinitionId));
    if (flowDefinition == null || ObjectUtils.isEmpty(flowDefinition.getNodes())) {
      return Collections.emptyMap();
    }
    return flowDefinition.getNodes()
        .stream()
        .filter(Objects::nonNull)
        .filter(node -> StringUtils.isNotBlank(node.getId()))
        .collect(Collectors.toMap(
            FlowNode::getId,
            node -> Optional.ofNullable(node.getType()).map(this::toProcessInfoNodeType).orElse(node.getId()),
            (left, right) -> right,
            LinkedHashMap::new));
  }

  private String toProcessInfoNodeType(String nodeType) {
    if (BuiltInFlowNodeTypes.START.equals(nodeType)) {
      return "发起节点";
    }
    if (BuiltInFlowNodeTypes.APPROVAL.equals(nodeType)) {
      return "审批节点";
    }
    if (BuiltInFlowNodeTypes.HANDLE.equals(nodeType)) {
      return "办理节点";
    }
    if (BuiltInFlowNodeTypes.CC.equals(nodeType)) {
      return "抄送节点";
    }
    if (BuiltInFlowNodeTypes.SUBFLOW.equals(nodeType)) {
      return "子流程节点";
    }
    if (BuiltInFlowNodeTypes.JOIN.equals(nodeType)) {
      return "合并节点";
    }
    if (BuiltInFlowNodeTypes.END.equals(nodeType)) {
      return "结束节点";
    }
    return nodeType;
  }

  private TimelineItem mapTimelineItem(
      HistoricTaskInstance task,
      FlowNode flowNode,
      boolean suspended,
      RuntimeTaskParticipants runtimeTaskParticipants,
      String starter) {
    if (flowNode == null) {
      return null;
    }

    var taskInstance = buildTaskInstance(task, suspended, runtimeTaskParticipants);
    applyStarterParticipant(flowNode, taskInstance, starter);
    return new TimelineItem(task, flowNode, taskInstance);
  }

  private String getHistoricStringVariable(List<HistoricVariableInstance> variables, String name) {
    return Optional.ofNullable(variables)
        .orElseGet(Collections::emptyList)
        .stream()
        .filter(Objects::nonNull)
        .filter(variable -> StringUtils.equals(variable.getVariableName(), name))
        .findFirst()
        .map(HistoricVariableInstance::getValue)
        .map(String::valueOf)
        .orElse(null);
  }

  private void applyStarterParticipant(FlowNode flowNode, FlowTimeline.TaskInstance taskInstance, String starter) {
    if (!isStartFlowNode(flowNode) || taskInstance == null || StringUtils.isBlank(starter)) {
      return;
    }
    taskInstance.setAssignee(starter);
    taskInstance.setCandidateUsers(Collections.singletonList(starter));
  }

  private boolean isStartFlowNode(FlowNode flowNode) {
    return flowNode != null
        && BuiltInFlowNodeTypes.START.equals(BuiltInFlowNodeTypes.normalize(flowNode.getType()));
  }

  private Map<String, RuntimeTaskParticipants> getRuntimeTaskParticipants(String instanceId) {
    if (StringUtils.isBlank(instanceId) || taskService == null) {
      return Collections.emptyMap();
    }
    var taskQuery = taskService.createTaskQuery();
    if (taskQuery == null) {
      return Collections.emptyMap();
    }
    var instanceTaskQuery = taskQuery.processInstanceId(instanceId);
    if (instanceTaskQuery == null) {
      return Collections.emptyMap();
    }
    var tasks = instanceTaskQuery.list();
    if (ObjectUtils.isEmpty(tasks)) {
      return Collections.emptyMap();
    }
    return tasks.stream()
        .filter(Objects::nonNull)
        .filter(task -> StringUtils.isNotBlank(task.getId()))
        .collect(Collectors.toMap(
            Task::getId,
            task -> new RuntimeTaskParticipants(task.getAssignee(), getRuntimeCandidateUsers(task.getId(), task.getAssignee())),
            (left, right) -> right,
            LinkedHashMap::new));
  }

  private void appendTimelineItem(
      List<FlowTimeline> timelines,
      Map<String, FlowTimeline> multipleSessions,
      Map<String, List<String>> multipleSessionAssignees,
      TimelineItem timelineItem,
      List<HistoricVariableInstance> variables,
      boolean suspended
  ) {
    if (timelineItem == null) {
      return;
    }

    if (!(timelineItem.flowNode instanceof MultipleApprovalNode)) {
      var timeline = new FlowTimeline();
      timeline.setFlowNode(timelineItem.flowNode);
      timeline.setTaskInstances(Collections.singletonList(timelineItem.taskInstance));
      timeline.setState(timelineItem.taskInstance.getState());
      timelines.add(timeline);
      return;
    }

    var session = getMultipleApprovalSession(timelineItem.task, variables);
    var timeline = multipleSessions.get(session);
    if (timeline == null) {
      timeline = new FlowTimeline();
      timeline.setFlowNode(timelineItem.flowNode);
      timeline.setTaskInstances(new ArrayList<FlowTimeline.TaskInstance>());
      timeline.setState(getMultipleTaskStatus(session, variables, suspended));
      multipleSessions.put(session, timeline);
      multipleSessionAssignees.put(session, getMultipleApprovalAssignees(session, variables));
      timelines.add(timeline);
    }

    timeline.getTaskInstances().add(timelineItem.taskInstance);
  }

  private void completeMultipleApprovalTimeline(
      String instanceId,
      FlowTimeline timeline,
      List<String> approvalAssignees
  ) {
    var taskInstances = timeline.getTaskInstances();
    var assignees = approvalAssignees == null ? Collections.<String>emptyList() : approvalAssignees;

    assignees.stream()
        .filter(assignee -> taskInstances.stream().noneMatch(task -> StringUtils.equals(task.getAssignee(), assignee)))
        .map(assignee -> buildUncreatedTaskInstance(instanceId, assignee))
        .forEach(taskInstances::add);

    if (timeline.getState() != TaskState.APPROVED && timeline.getState() != TaskState.REJECTED) {
      return;
    }

    taskInstances.stream()
        .filter(task -> task.getState() == TaskState.UNCREATED || task.getState() == TaskState.CREATED)
        .forEach(task -> task.setState(TaskState.CANCELLED));
  }

  private FlowTimeline.TaskInstance buildTaskInstance(
      HistoricTaskInstance task,
      boolean suspended,
      RuntimeTaskParticipants runtimeTaskParticipants) {
    String assignee = StringUtils.defaultIfBlank(
        task.getAssignee(),
        Optional.ofNullable(runtimeTaskParticipants).map(RuntimeTaskParticipants::assignee).orElse(null));
    var taskInstance = new FlowTimeline.TaskInstance();
    taskInstance.setInstanceId(task.getProcessInstanceId());
    taskInstance.setTaskId(task.getId());
    taskInstance.setAssignee(assignee);
    taskInstance.setStartTime(Optional.ofNullable(task.getStartTime()).map(Dates::toLocalDateTime).orElse(null));
    taskInstance.setEndTime(Optional.ofNullable(task.getEndTime()).map(Dates::toLocalDateTime).orElse(null));
    taskInstance.setState(getTaskState(task, suspended, assignee));
    taskInstance.setCandidateUsers(getCandidateUsers(task, runtimeTaskParticipants, assignee));
    taskInstance.setComment(getTaskComment(task.getId()));
    return taskInstance;
  }

  private TaskState getTaskState(HistoricTaskInstance task, boolean suspended, String assignee) {
    if (task.getEndTime() == null) {
      if (suspended) {
        return TaskState.SUSPENDED;
      }
      return StringUtils.isNotBlank(assignee) ? TaskState.PROCESSING : TaskState.CREATED;
    }

    return Optional.ofNullable(historyService.createHistoricVariableInstanceQuery()
            .processInstanceId(task.getProcessInstanceId())
            .taskId(task.getId())
            .variableName(Constants.Variable.TASK_APPROVED)
            .singleResult())
        .map(HistoricVariableInstance::getValue)
        .map(Converter::toBoolean)
        .map(approved -> approved ? TaskState.APPROVED : TaskState.REJECTED)
        .orElseGet(() -> isCompleteAction(task) ? TaskState.HANDLED : TaskState.CREATED);
  }

  private boolean isCompleteAction(HistoricTaskInstance task) {
    return Optional.ofNullable(historyService.createHistoricVariableInstanceQuery()
            .processInstanceId(task.getProcessInstanceId())
            .taskId(task.getId())
            .variableName(Constants.Variable.LOCAL_APPROVAL_ACTION)
            .singleResult())
        .map(HistoricVariableInstance::getValue)
        .map(String::valueOf)
        .filter(Constants.Value.COMPLETE::equalsIgnoreCase)
        .isPresent();
  }

  private String getMultipleApprovalSession(HistoricTaskInstance task, List<HistoricVariableInstance> variables) {
    return variables.stream()
        .filter(variable -> StringUtils.equals(task.getId(), variable.getTaskId()))
        .filter(variable -> StringUtils.equals(Constants.Variable.MULTIPLE_APPROVAL_SESSION, variable.getVariableName()))
        .findFirst()
        .map(HistoricVariableInstance::getValue)
        .map(String::valueOf)
        .orElse("NO_SESSION_FOUND");
  }

  private List<String> getMultipleApprovalAssignees(String session, List<HistoricVariableInstance> variables) {
    return variables.stream()
        .filter(variable -> StringUtils.equals(
            Constants.Variable.MULTIPLE_APPROVAL_SESSION + session + Constants.Variable.MULTIPLE_APPROVAL_ASSIGNEE_LIST,
            variable.getVariableName()))
        .map(HistoricVariableInstance::getValue)
        .findFirst()
        // 逐元素类型校验集中在 BpmnUtil.readStringListVariable，这里不再需要未检查强转
        .map(value -> BpmnUtil.readStringListVariable(
            value, Constants.Variable.MULTIPLE_APPROVAL_ASSIGNEE_LIST))
        .orElseGet(ArrayList::new);
  }

  private TaskState getMultipleTaskStatus(String session, List<HistoricVariableInstance> variables, boolean suspended) {
    var sessionVariables = variables.stream()
        .filter(variable -> StringUtils.startsWith(variable.getVariableName(), Constants.Variable.MULTIPLE_APPROVAL_SESSION + session))
        .collect(Collectors.toList());

    var completed = sessionVariables.stream()
        .filter(variable -> StringUtils.endsWith(variable.getVariableName(), Constants.Variable.MULTIPLE_APPROVAL_COMPLETED_SUFFIX))
        .findFirst()
        .map(HistoricVariableInstance::getValue)
        .map(Converter::toBoolean)
        .orElse(false);
    var approved = sessionVariables.stream()
        .filter(variable -> StringUtils.endsWith(variable.getVariableName(), Constants.Variable.APPROVED_SUFFIX))
        .findFirst()
        .map(HistoricVariableInstance::getValue)
        .map(Converter::toBoolean)
        .orElse(false);

    if (!completed) {
      if (suspended) {
        return TaskState.SUSPENDED;
      }
      return TaskState.PROCESSING;
    }
    return approved ? TaskState.APPROVED : TaskState.REJECTED;
  }

  private boolean isRuntimeInstanceSuspended(String instanceId) {
    return runtimeService != null
        && runtimeService.createProcessInstanceQuery()
        .processInstanceId(instanceId)
        .suspended()
        .count() > 0;
  }

  private FlowTimeline.TaskInstance buildUncreatedTaskInstance(String instanceId, String assignee) {
    var taskInstance = new FlowTimeline.TaskInstance();
    taskInstance.setInstanceId(instanceId);
    taskInstance.setAssignee(assignee);
    taskInstance.setState(TaskState.UNCREATED);
    return taskInstance;
  }

  private List<String> getCandidateUsers(
      HistoricTaskInstance task,
      RuntimeTaskParticipants runtimeTaskParticipants,
      String assignee) {
    var candidateUsers = historyService.getHistoricIdentityLinksForTask(task.getId())
        .stream()
        .filter(Objects::nonNull)
        .filter(link -> "candidate".equalsIgnoreCase(link.getType()))
        .map(HistoricIdentityLink::getUserId)
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
    Optional.ofNullable(runtimeTaskParticipants)
        .map(RuntimeTaskParticipants::candidateUsers)
        .orElseGet(Collections::emptyList)
        .stream()
        .filter(StringUtils::isNotBlank)
        .filter(candidate -> !candidateUsers.contains(candidate))
        .forEach(candidateUsers::add);
    if (StringUtils.isNotBlank(assignee) && !candidateUsers.contains(assignee)) {
      candidateUsers.add(assignee);
    }
    return candidateUsers;
  }

  private List<String> getRuntimeCandidateUsers(String taskId, String assignee) {
    var candidateUsers = taskService.getIdentityLinksForTask(taskId)
        .stream()
        .filter(Objects::nonNull)
        .filter(link -> "candidate".equalsIgnoreCase(link.getType()))
        .map(IdentityLink::getUserId)
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
    if (StringUtils.isNotBlank(assignee) && !candidateUsers.contains(assignee)) {
      candidateUsers.add(assignee);
    }
    return candidateUsers;
  }

  private record RuntimeTaskParticipants(String assignee, List<String> candidateUsers) {
  }

  private String getTaskComment(String taskId) {
    return taskService.getTaskComments(taskId)
        .stream()
        .filter(Objects::nonNull)
        .max(Comparator.comparing(Comment::getTime, Comparator.nullsFirst(Date::compareTo)))
        .map(Comment::getFullMessage)
        .orElse(null);
  }

  private ProcessDefinitionQuery createStatisticDefinitionQuery(String keyword, String status) {
    var query = repositoryService.createProcessDefinitionQuery()
        .latestVersion()
        .processDefinitionCategoryLike(Constants.OUROBOROS_NAMESPACE + "/%");
    if (StringUtils.isNotBlank(keyword)) {
      query = query.processDefinitionNameLike("%" + keyword + "%");
    }
    if ("deployed".equalsIgnoreCase(status)) {
      query = query.active();
    } else if ("offline".equalsIgnoreCase(status)) {
      query = query.suspended();
    }
    return query;
  }

  private FlowInstanceStatistic mapStatistic(ProcessDefinition definition) {
    var totalQuery = historyService.createHistoricProcessInstanceQuery()
        .processDefinitionKey(definition.getKey());
    var total = totalQuery.count();
    var running = countRuntimeInstances(definition.getKey(), false);
    var suspended = countRuntimeInstances(definition.getKey(), true);
    var duration = getDurationStatistic(definition.getKey());
    var statistic = new FlowInstanceStatistic();
    statistic.setFlowKey(definition.getKey());
    statistic.setDisplayName(definition.getName());
    statistic.setCategory(StringUtils.removeStart(definition.getCategory(), Constants.OUROBOROS_NAMESPACE + "/"));
    statistic.setDescription(definition.getDescription());
    statistic.setTotal(total);
    statistic.setRunning(running);
    statistic.setSuspended(suspended);
    statistic.setFinished(total - running - suspended);
    statistic.setAvgDurationMillis(duration.avgDurationMillis);
    statistic.setNodeCount(duration.nodeCount);
    statistic.setAvgNodeDurationMillis(duration.avgNodeDurationMillis);
    statistic.setHandledCount(duration.handledCount);
    statistic.setAvgHandleMillis(duration.avgHandleMillis);
    statistic.setStatus(definition.isSuspended() ? "offline" : "deployed");
    return statistic;
  }

  private DurationStatistic getDurationStatistic(String flowKey) {
    var finishedInstances = historyService.createHistoricProcessInstanceQuery()
        .processDefinitionKey(flowKey)
        .finished()
        .list();
    var tasks = historyService.createHistoricTaskInstanceQuery()
        .processDefinitionKey(flowKey)
        .finished()
        .list();

    long instanceCount = finishedInstances.stream()
        .filter(instance -> instance.getStartTime() != null && instance.getEndTime() != null)
        .count();
    long instanceDuration = finishedInstances.stream()
        .filter(instance -> instance.getStartTime() != null && instance.getEndTime() != null)
        .mapToLong(instance -> instance.getEndTime().getTime() - instance.getStartTime().getTime())
        .sum();

    long nodeCount = tasks.stream()
        .filter(task -> task.getStartTime() != null && task.getEndTime() != null)
        .count();
    long nodeDuration = tasks.stream()
        .filter(task -> task.getStartTime() != null && task.getEndTime() != null)
        .mapToLong(task -> task.getEndTime().getTime() - task.getStartTime().getTime())
        .sum();

    long handledCount = tasks.stream()
        .filter(task -> StringUtils.isNotBlank(task.getAssignee()))
        .filter(task -> task.getStartTime() != null && task.getEndTime() != null)
        .count();
    long handleDuration = tasks.stream()
        .filter(task -> StringUtils.isNotBlank(task.getAssignee()))
        .filter(task -> task.getStartTime() != null && task.getEndTime() != null)
        .mapToLong(task -> task.getEndTime().getTime() - task.getStartTime().getTime())
        .sum();

    var statistic = new DurationStatistic();
    statistic.avgDurationMillis = average(instanceDuration, instanceCount);
    statistic.nodeCount = nodeCount;
    statistic.avgNodeDurationMillis = average(nodeDuration, nodeCount);
    statistic.handledCount = handledCount;
    statistic.avgHandleMillis = average(handleDuration, handledCount);
    return statistic;
  }

  private long average(long total, long count) {
    if (count == 0) {
      return 0L;
    }
    return total / count;
  }

  private static class DurationStatistic {
    long avgDurationMillis;
    long nodeCount;
    long avgNodeDurationMillis;
    long handledCount;
    long avgHandleMillis;
  }

  private boolean processDefinitionExists(String flowKey) {
    return repositoryService.createProcessDefinitionQuery()
        .processDefinitionKey(flowKey)
        .latestVersion()
        .singleResult() != null;
  }

  private boolean isWithinRange(Date startTime, Date endTime, Date from, Date to) {
    if (startTime == null || endTime == null) {
      return false;
    }
    if (from != null && startTime.before(from)) {
      return false;
    }
    return to == null || !endTime.after(to);
  }

  private long averageDuration(List<HistoricProcessInstance> instances) {
    long total = instances.stream()
        .mapToLong(instance -> instance.getEndTime().getTime() - instance.getStartTime().getTime())
        .sum();
    return average(total, instances.size());
  }

  private List<FlowDurationStatistic.NodeDurationStatistic> buildNodeDurationStatistics(List<HistoricTaskInstance> tasks) {
    Map<String, DurationAggregate> durationByNode = new LinkedHashMap<String, DurationAggregate>();
    Map<String, String> nameByNode = new HashMap<String, String>();
    for (var task : tasks) {
      var nodeId = task.getTaskDefinitionKey();
      if (StringUtils.isBlank(nodeId)) {
        continue;
      }
      durationByNode.computeIfAbsent(nodeId, key -> new DurationAggregate())
          .add(task.getEndTime().getTime() - task.getStartTime().getTime());
      nameByNode.putIfAbsent(nodeId, task.getName());
    }

    var result = new ArrayList<FlowDurationStatistic.NodeDurationStatistic>();
    durationByNode.forEach((nodeId, duration) -> {
      var nodeStatistic = new FlowDurationStatistic.NodeDurationStatistic();
      nodeStatistic.setNodeId(nodeId);
      nodeStatistic.setNodeName(nameByNode.get(nodeId));
      nodeStatistic.setTaskCount(duration.count);
      nodeStatistic.setAvgDurationMillis(average(duration.totalMillis, duration.count));
      result.add(nodeStatistic);
    });
    return result;
  }

  private List<FlowDurationStatistic.ApproverDurationStatistic> buildApproverDurationStatistics(List<HistoricTaskInstance> tasks) {
    Map<String, DurationAggregate> durationByApprover = new LinkedHashMap<String, DurationAggregate>();
    for (var task : tasks) {
      var userId = task.getAssignee();
      if (StringUtils.isBlank(userId)) {
        continue;
      }
      durationByApprover.computeIfAbsent(userId, key -> new DurationAggregate())
          .add(task.getEndTime().getTime() - task.getStartTime().getTime());
    }

    var result = new ArrayList<FlowDurationStatistic.ApproverDurationStatistic>();
    durationByApprover.forEach((userId, duration) -> {
      var approverStatistic = new FlowDurationStatistic.ApproverDurationStatistic();
      approverStatistic.setUserId(userId);
      approverStatistic.setUserName(userId);
      approverStatistic.setHandledCount(duration.count);
      approverStatistic.setAvgHandleMillis(average(duration.totalMillis, duration.count));
      result.add(approverStatistic);
    });
    return result;
  }

  private static class DurationAggregate {
    long count;
    long totalMillis;

    void add(long durationMillis) {
      count++;
      totalMillis += durationMillis;
    }
  }

  private long countRuntimeInstances(String flowKey, boolean suspended) {
    var runtimeQuery = runtimeService.createProcessInstanceQuery()
        .processDefinitionKey(flowKey);
    return suspended ? runtimeQuery.suspended().count() : runtimeQuery.active().count();
  }

  private HistoricProcessInstanceQuery createHistoricProcessInstanceQuery(String flowKey, String status) {
    var query = historyService.createHistoricProcessInstanceQuery()
        .processDefinitionKey(flowKey)
        .orderByProcessInstanceStartTime()
        .desc();
    if (INSTANCE_STATUS_RUNNING.equalsIgnoreCase(status)) {
      query = query.unfinished();
    } else if (INSTANCE_STATUS_FINISHED.equalsIgnoreCase(status)) {
      query = query.finished();
    }
    return query;
  }

  private List<FlowInstanceDetail> getRuntimeStateInstances(String flowKey, String status, Integer offset, Integer limit) {
    int remainingSkip = offset == null ? 0 : Math.max(0, offset);
    if (limit != null && limit <= 0) {
      return Collections.emptyList();
    }

    long remaining = limit == null ? Long.MAX_VALUE : limit.longValue();
    int batchSize = limit == null ? RUNTIME_STATE_SCAN_BATCH_SIZE : Math.max(RUNTIME_STATE_SCAN_BATCH_SIZE, limit);
    int historyOffset = 0;
    String normalizedStatus = normalizeInstanceStatus(status);
    var historicQuery = createHistoricProcessInstanceQuery(flowKey, INSTANCE_STATUS_RUNNING);
    var details = new ArrayList<FlowInstanceDetail>();

    while (remaining > 0) {
      var historicInstances = historicQuery.listPage(historyOffset, batchSize);
      if (historicInstances.isEmpty()) {
        break;
      }

      Set<String> runtimeInstanceIds = listRuntimeInstanceIds(flowKey, status, historicInstances);
      for (var instance : historicInstances) {
        if (!runtimeInstanceIds.contains(instance.getId())) {
          continue;
        }
        if (remainingSkip > 0) {
          remainingSkip--;
          continue;
        }
        details.add(mapInstanceDetail(instance, normalizedStatus));
        remaining--;
        if (remaining == 0) {
          return details;
        }
      }

      historyOffset += historicInstances.size();
      if (historicInstances.size() < batchSize) {
        break;
      }
    }

    return details;
  }

  private Long countRuntimeStateInstances(String flowKey, String status) {
    var runtimeQuery = runtimeService.createProcessInstanceQuery()
        .processDefinitionKey(flowKey);
    return isSuspendedStatus(status)
        ? runtimeQuery.suspended().count()
        : runtimeQuery.active().count();
  }

  private Set<String> listRuntimeInstanceIds(String flowKey, String status, List<HistoricProcessInstance> historicInstances) {
    if (historicInstances.isEmpty()) {
      return Collections.emptySet();
    }

    var runtimeQuery = runtimeService.createProcessInstanceQuery()
        .processDefinitionKey(flowKey);

    runtimeQuery = isSuspendedStatus(status)
        ? runtimeQuery.suspended()
        : runtimeQuery.active();

    Set<String> instanceIds = historicInstances.stream()
        .map(HistoricProcessInstance::getId)
        .collect(Collectors.toSet());
    var runtimeInstances = runtimeQuery.processInstanceIds(instanceIds).list();
    return runtimeInstances.stream()
        .map(ProcessInstance::getId)
        .collect(Collectors.toSet());
  }

  private FlowInstanceDetail mapInstanceDetail(HistoricProcessInstance instance, String status) {
    var instanceId = instance.getId();
    var latestTask = historyService.createHistoricTaskInstanceQuery()
        .processInstanceId(instanceId)
        .orderByHistoricTaskInstanceStartTime()
        .desc()
        .listPage(0, 1)
        .stream()
        .findFirst()
        .orElse(null);
    var starter = Optional.ofNullable(historyService.createHistoricVariableInstanceQuery()
            .processInstanceId(instanceId)
            .variableName(Constants.Variable.PROCESS_STARTER)
            .singleResult())
        .map(HistoricVariableInstance::getValue)
        .map(String::valueOf)
        .orElse(null);
    var outcome = Optional.ofNullable(historyService.createHistoricVariableInstanceQuery()
            .processInstanceId(instanceId)
            .variableName(Constants.Variable.PROCESS_OUTCOME)
            .singleResult())
        .map(HistoricVariableInstance::getValue)
        .map(String::valueOf)
        .orElse(null);

    var detail = new FlowInstanceDetail();
    detail.setInstanceId(instanceId);
    detail.setFlowKey(instance.getProcessDefinitionKey());
    detail.setStarter(starter);
    detail.setStartTime(Optional.ofNullable(instance.getStartTime()).map(Dates::toLocalDateTime).orElse(null));
    detail.setEndTime(Optional.ofNullable(instance.getEndTime()).map(Dates::toLocalDateTime).orElse(null));
    detail.setStatus(status);
    detail.setCancellable("running".equals(status) || "suspended".equals(status));
    detail.setOutcome(outcome);
    detail.setTaskId(Optional.ofNullable(latestTask).map(HistoricTaskInstance::getId).orElse(null));
    detail.setTaskName(Optional.ofNullable(latestTask).map(HistoricTaskInstance::getName).orElse(null));
    return detail;
  }

  private boolean isRuntimeStatus(String status) {
    return INSTANCE_STATUS_RUNNING.equalsIgnoreCase(status)
        || INSTANCE_STATUS_SUSPENDED.equalsIgnoreCase(status);
  }

  private boolean isSuspendedStatus(String status) {
    return INSTANCE_STATUS_SUSPENDED.equalsIgnoreCase(status);
  }

  private Map<String, String> resolveRuntimeStatuses(List<HistoricProcessInstance> historicInstances, String requestedStatus) {
    if (StringUtils.isNotBlank(requestedStatus) || historicInstances.isEmpty()) {
      return Collections.emptyMap();
    }

    Set<String> unfinishedIds = historicInstances.stream()
        .filter(instance -> instance.getEndTime() == null)
        .map(HistoricProcessInstance::getId)
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());
    if (unfinishedIds.isEmpty()) {
      return Collections.emptyMap();
    }

    Map<String, String> statuses = new HashMap<String, String>();
    runtimeService.createProcessInstanceQuery()
        .processInstanceIds(unfinishedIds)
        .active()
        .list()
        .forEach(instance -> statuses.put(instance.getId(), INSTANCE_STATUS_RUNNING));
    runtimeService.createProcessInstanceQuery()
        .processInstanceIds(unfinishedIds)
        .suspended()
        .list()
        .forEach(instance -> statuses.put(instance.getId(), INSTANCE_STATUS_SUSPENDED));
    return statuses;
  }

  private String resolveHistoricStatus(HistoricProcessInstance instance, String requestedStatus, Map<String, String> runtimeStatuses) {
    if (INSTANCE_STATUS_FINISHED.equalsIgnoreCase(requestedStatus)) {
      return INSTANCE_STATUS_FINISHED;
    }
    if (INSTANCE_STATUS_RUNNING.equalsIgnoreCase(requestedStatus)) {
      return INSTANCE_STATUS_RUNNING;
    }
    if (INSTANCE_STATUS_SUSPENDED.equalsIgnoreCase(requestedStatus)) {
      return INSTANCE_STATUS_SUSPENDED;
    }
    var runtimeStatus = runtimeStatuses.get(instance.getId());
    if (runtimeStatus != null) {
      return runtimeStatus;
    }
    return instance.getEndTime() == null ? INSTANCE_STATUS_RUNNING : INSTANCE_STATUS_FINISHED;
  }

  private String normalizeInstanceStatus(String status) {
    return isSuspendedStatus(status) ? INSTANCE_STATUS_SUSPENDED : INSTANCE_STATUS_RUNNING;
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

  private Try<HistoricProcessInstance> getHistoricInstance(String instanceId) {
    return Try.of(() -> historyService.createHistoricProcessInstanceQuery()
        .processInstanceId(instanceId)
        .singleResult());
  }

  private static class TimelineItem {
    final HistoricTaskInstance task;
    final FlowNode flowNode;
    final FlowTimeline.TaskInstance taskInstance;

    TimelineItem(HistoricTaskInstance task, FlowNode flowNode, FlowTimeline.TaskInstance taskInstance) {
      this.task = task;
      this.flowNode = flowNode;
      this.taskInstance = taskInstance;
    }
  }
}
