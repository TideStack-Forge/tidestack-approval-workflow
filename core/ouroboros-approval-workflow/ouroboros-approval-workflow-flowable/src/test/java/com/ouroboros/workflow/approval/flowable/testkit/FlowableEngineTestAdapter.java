package com.ouroboros.workflow.approval.flowable.testkit;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.flowable.bpmn.converter.BpmnXMLConverter;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.Process;
import org.flowable.common.engine.api.delegate.Expression;
import org.flowable.common.engine.api.variable.VariableContainer;
import org.flowable.common.engine.impl.el.ExpressionManager;
import org.flowable.engine.HistoryService;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.ProcessEngineConfiguration;
import org.flowable.engine.ProcessEngines;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.flowable.engine.impl.el.ProcessExpressionManager;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.repository.ProcessDefinitionQuery;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.task.Comment;
import org.flowable.identitylink.api.IdentityLink;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.flowable.task.api.Task;
import org.flowable.variable.api.history.HistoricVariableInstance;

import io.vavr.control.Try;

import com.ouroboros.util.Json;
import com.ouroboros.uischema.schema.SchemaNode;
import com.ouroboros.workflow.approval.FlowFormService;
import com.ouroboros.workflow.approval.FlowTaskAction;
import com.ouroboros.workflow.approval.TrustedFlowTaskAction;
import com.ouroboros.workflow.approval.definition.FlowDefinition;
import com.ouroboros.workflow.approval.definition.FlowNode;
import com.ouroboros.workflow.approval.definition.NodeFormFieldPermission;
import com.ouroboros.workflow.approval.definition.codec.CompositeFlowDefinitionCodec;
import com.ouroboros.workflow.approval.definition.condition.Condition;
import com.ouroboros.workflow.approval.definition.graph.StructuredFlowDefinition;
import com.ouroboros.workflow.approval.definition.nodes.ApprovalNode;
import com.ouroboros.workflow.approval.definition.nodes.GatewayNode;
import com.ouroboros.workflow.approval.definition.nodes.JoinNode;
import com.ouroboros.workflow.approval.definition.nodes.MultipleApprovalNode;
import com.ouroboros.workflow.approval.definition.nodes.SubflowNode;
import com.ouroboros.workflow.approval.definition.v2.FlowEdgeV2;
import com.ouroboros.workflow.approval.event.InstanceEventDispatcher;
import com.ouroboros.workflow.approval.event.TaskEventDispatcher;
import com.ouroboros.workflow.approval.flowable.delegate.FailServiceTask;
import com.ouroboros.workflow.approval.flowable.action.FlowableApproveAction;
import com.ouroboros.workflow.approval.flowable.action.FlowableAddSignAction;
import com.ouroboros.workflow.approval.flowable.action.FlowableCompleteAction;
import com.ouroboros.workflow.approval.flowable.action.FlowableFreeApprovalAction;
import com.ouroboros.workflow.approval.flowable.action.FlowableFreeRecoverAction;
import com.ouroboros.workflow.approval.flowable.action.FlowableFreeRejectAction;
import com.ouroboros.workflow.approval.flowable.action.FlowableRejectAction;
import com.ouroboros.workflow.approval.flowable.action.FlowableRollbackAction;
import com.ouroboros.workflow.approval.flowable.action.FlowableTransferAction;
import com.ouroboros.workflow.approval.flowable.delegate.InitializeFlowServiceTask;
import com.ouroboros.workflow.approval.flowable.delegate.InitializeMultipleApprovalServiceTask;
import com.ouroboros.workflow.approval.flowable.delegate.SetProcessOutcomeServiceTask;
import com.ouroboros.workflow.approval.flowable.listener.CreateMultipleApprovalTaskListener;
import com.ouroboros.workflow.approval.flowable.listener.CreateSingleApprovalTaskListener;
import com.ouroboros.workflow.approval.flowable.listener.FlowEndExecutionListener;
import com.ouroboros.workflow.approval.flowable.listener.FlowStartExecutionListener;
import com.ouroboros.workflow.approval.flowable.listener.MultipleApprovalCompletedTaskListener;
import com.ouroboros.workflow.approval.flowable.listener.MultipleApprovalEndExecutionListener;
import com.ouroboros.workflow.approval.flowable.listener.MultipleApprovalStartExecutionListener;
import com.ouroboros.workflow.approval.flowable.listener.SingleApproveCompletedTaskListener;
import com.ouroboros.workflow.approval.flowable.listener.AddSignTaskCompletionListener;
import com.ouroboros.workflow.approval.flowable.service.FlowableFlowDefinitionService;
import com.ouroboros.workflow.approval.flowable.service.FlowableFlowInstanceService;
import com.ouroboros.workflow.approval.flowable.service.FlowableFlowTaskService;
import com.ouroboros.workflow.approval.flowable.service.FlowableAddSignTaskManager;
import com.ouroboros.workflow.approval.flowable.transpiler.Constants;
import com.ouroboros.workflow.approval.flowable.transpiler.FlowableWorkflowTranspiler;
import com.ouroboros.workflow.approval.flowable.util.BpmnUtil;
import com.ouroboros.workflow.approval.listener.FlowInstanceListener;
import com.ouroboros.workflow.approval.listener.FlowTaskListener;
import com.ouroboros.workflow.approval.model.FlowTimeline;
import com.ouroboros.workflow.approval.model.TaskState;
import com.ouroboros.workflow.approval.model.events.FlowInstanceEvent;
import com.ouroboros.workflow.approval.model.events.FlowTaskEvent;
import com.ouroboros.workflow.approval.testkit.adapter.EngineTestAdapter;
import com.ouroboros.workflow.approval.testkit.snapshot.InstanceEventView;
import com.ouroboros.workflow.approval.testkit.snapshot.RoundTripSnapshot;
import com.ouroboros.workflow.approval.testkit.snapshot.RuntimeInstance;
import com.ouroboros.workflow.approval.testkit.snapshot.StartRequest;
import com.ouroboros.workflow.approval.testkit.snapshot.TaskEventView;
import com.ouroboros.workflow.approval.testkit.snapshot.TaskView;
import com.ouroboros.workflow.approval.testkit.snapshot.TimelineView;
import com.ouroboros.workflow.approval.testkit.snapshot.TranspiledModelSnapshot;

public class FlowableEngineTestAdapter implements EngineTestAdapter {
  private final CompositeFlowDefinitionCodec codec = new CompositeFlowDefinitionCodec();
  private final Map<String, List<TaskEventView>> taskEventsByInstance = new LinkedHashMap<String, List<TaskEventView>>();
  private final List<FlowTaskEvent> taskActionFacts = new ArrayList<>();

  public List<FlowTaskEvent> taskActionFacts() { return List.copyOf(taskActionFacts); }
  private final Map<String, List<InstanceEventView>> instanceEventsByInstance = new LinkedHashMap<String, List<InstanceEventView>>();
  private TaskEventDispatcher taskEventDispatcher;
  private InstanceEventDispatcher instanceEventDispatcher;
  private ProcessEngine processEngine;
  private long sequence = 1L;

  @Override
  public FlowDefinition loadDefinition(String json) {
    String flowKey = structuredDefinition(json).getId();
    FlowableFlowDefinitionService service = buildFlowDefinitionService(flowKey, json);
    return service.getFlowDefinition(flowKey).get();
  }

  @Override
  public List<FlowNode> flowNodes(String json) {
    String flowKey = structuredDefinition(json).getId();
    FlowableFlowDefinitionService service = buildFlowDefinitionService(flowKey, json);
    return service.getFlowNodes(flowKey);
  }

  @Override
  public List<ApprovalNode> candidateApprovalNodes(String json, Map<String, Object> conditionContext) {
    String flowKey = structuredDefinition(json).getId();
    FlowableFlowDefinitionService service = buildFlowDefinitionService(flowKey, json);
    return service.getUserSelectCandidateApprovalNodes(flowKey, conditionContext);
  }

  @Override
  public TranspiledModelSnapshot transpile(String json) {
    StructuredFlowDefinition originalDefinition = structuredDefinition(json);
    BpmnModel model = new FlowableWorkflowTranspiler().transpile(originalDefinition);
    StructuredFlowDefinition recoveredDefinition = BpmnUtil.readStructuredFlowDefinition(model);
    if (recoveredDefinition == null) {
      throw new IllegalStateException("Flowable transpiler did not persist a recoverable flow definition");
    }
    return toSnapshot(originalDefinition);
  }

  @Override
  public RoundTripSnapshot roundTrip(String json) {
    StructuredFlowDefinition originalDefinition = structuredDefinition(json);
    BpmnModel model = new FlowableWorkflowTranspiler().transpile(originalDefinition);
    StructuredFlowDefinition roundTrippedDefinition = BpmnUtil.readStructuredFlowDefinition(model);
    if (roundTrippedDefinition == null) {
      throw new IllegalStateException("Flowable round-trip did not recover a structured definition");
    }
    return new RoundTripSnapshot(
        BpmnUtil.readFlowDefinitionRawJson(model),
        Json.toJsonString(codec.encodeV2(originalDefinition).get()),
        toSnapshot(originalDefinition)
    );
  }

  @Override
  public void deploy(String flowKey, String json) {
    ensureProcessEngine();
    BpmnModel model = new FlowableWorkflowTranspiler().transpile(structuredDefinition(json));
    byte[] xmlBytes = new BpmnXMLConverter().convertToXML(model);
    processEngine.getRepositoryService()
        .createDeployment()
        .addBytes(flowKey + ".bpmn20.xml", xmlBytes)
        .deploy();
  }

  @Override
  public RuntimeInstance start(String flowKey, StartRequest request) {
    ensureProcessEngine();

    Map<String, Object> variables = new LinkedHashMap<String, Object>();
    variables.put(Constants.Variable.PROCESS_STARTER, request.getStarter());
    variables.put(Constants.Variable.PROCESS_FLOW_KEY, flowKey);
    variables.put(Constants.Variable.USER_SELECTED_ASSIGNEES, request.getSelectedAssignees());
    variables.putAll(request.getInstanceVariables());

    ProcessInstance instance = processEngine.getRuntimeService().startProcessInstanceByKey(flowKey, variables);
    return new RuntimeInstance(instance.getId(), flowKey);
  }

  @Override
  public void completeStartTask(String instanceId) {
    ensureProcessEngine();

    Task startTask = processEngine.getTaskService().createTaskQuery()
        .processInstanceId(instanceId)
        .taskDefinitionKey("startnode")
        .singleResult();
    if (startTask == null) {
      throw new IllegalStateException("No start task found for instance: " + instanceId);
    }
    processEngine.getTaskService().complete(startTask.getId());
  }

  @Override
  public void doAction(String taskId, String action, String userId, String comment, Map<String, Object> formData, Object payload) {
    ensureProcessEngine();

    FlowableFlowTaskService service = buildFlowTaskService();
    Try<Void> result = service.doAction(taskId, action, userId, comment, formData, payload);
    if (result.isFailure()) {
      throw new IllegalStateException("Flowable task action failed: " + taskId, result.getCause());
    }
  }

  /** Executes the internal trusted-action port against the real in-memory Flowable engine. */
  public void executeTrustedAction(
      String taskId, String action, String comment, Map<String, Object> formData,
      Object payload, String sourceFactId) {
    ensureProcessEngine();

    Try<Void> result = buildFlowTaskService().execute(
        new TrustedFlowTaskAction(taskId, action, comment, formData, payload, sourceFactId,
            TrustedFlowTaskAction.Origin.EXTERNAL_CALLBACK));
    if (result.isFailure()) {
      throw new IllegalStateException("Flowable trusted task action failed: " + taskId, result.getCause());
    }
  }

  @Override
  public void pauseInstance(String instanceId, String userId, String comment) {
    ensureProcessEngine();

    Try<Void> result = buildFlowInstanceService().pause(instanceId, userId, comment);
    if (result.isFailure()) {
      throw new IllegalStateException("Flowable instance pause failed: " + instanceId, result.getCause());
    }
  }

  @Override
  public void resumeInstance(String instanceId, String userId) {
    ensureProcessEngine();

    Try<Void> result = buildFlowInstanceService().resume(instanceId, userId);
    if (result.isFailure()) {
      throw new IllegalStateException("Flowable instance resume failed: " + instanceId, result.getCause());
    }
  }

  @Override
  public void cancelInstance(String instanceId, String userId, String comment) {
    ensureProcessEngine();

    Try<Void> result = buildFlowInstanceService().cancel(instanceId, userId, comment);
    if (result.isFailure()) {
      throw new IllegalStateException("Flowable instance cancel failed: " + instanceId, result.getCause());
    }
  }

  @Override
  public void completeInstance(String instanceId, String userId, boolean approved, String comment) {
    ensureProcessEngine();

    Try<Void> result = buildFlowInstanceService().complete(instanceId, userId, approved, comment);
    if (result.isFailure()) {
      throw new IllegalStateException("Flowable instance complete failed: " + instanceId, result.getCause());
    }
  }

  @Override
  public List<TaskView> activeTasks(String instanceId) {
    ensureProcessEngine();
    return processEngine.getTaskService().createTaskQuery()
        .processInstanceId(instanceId)
        .list()
        .stream()
        .map(this::toTaskView)
        .collect(Collectors.toList());
  }

  @Override
  public List<TaskView> activeTasks(String instanceId, String nodeId) {
    ensureProcessEngine();
    return processEngine.getTaskService().createTaskQuery()
        .processInstanceId(instanceId)
        .taskDefinitionKey(nodeId)
        .list()
        .stream()
        .map(this::toTaskView)
        .collect(Collectors.toList());
  }

  @Override
  public Map<String, Object> historyVariables(String instanceId) {
    ensureProcessEngine();

    HistoryService historyService = processEngine.getHistoryService();
    LinkedHashMap<String, Object> history = new LinkedHashMap<String, Object>();

    historyService.createHistoricVariableInstanceQuery()
        .processInstanceId(instanceId)
        .list()
        .forEach(variable -> addHistoryVariable(history, variable));

    historyService.createHistoricTaskInstanceQuery()
        .processInstanceId(instanceId)
        .finished()
        .list()
        .forEach(task -> {
          String comment = latestComment(task.getId());
          if (comment != null) {
            history.put(taskHistoryKey(task.getId(), "comment"), comment);
          }
        });

    return Collections.unmodifiableMap(history);
  }

  @Override
  public List<TaskEventView> taskEvents(String instanceId) {
    ensureProcessEngine();

    List<TaskEventView> collected = taskEventsByInstance.get(instanceId);
    if (collected == null) {
      return Collections.emptyList();
    }
    return Collections.unmodifiableList(new ArrayList<TaskEventView>(collected));
  }

  @Override
  public List<TaskEventView> allTaskEvents() {
    ensureProcessEngine();

    ArrayList<TaskEventView> collected = new ArrayList<TaskEventView>();
    for (List<TaskEventView> events : taskEventsByInstance.values()) {
      collected.addAll(events);
    }
    collected.sort(Comparator.comparingLong(TaskEventView::getSequence));
    return Collections.unmodifiableList(collected);
  }

  @Override
  public List<InstanceEventView> instanceEvents(String instanceId) {
    ensureProcessEngine();

    List<InstanceEventView> events = instanceEventsByInstance.get(instanceId);
    if (events == null) {
      return Collections.emptyList();
    }
    return Collections.unmodifiableList(new ArrayList<InstanceEventView>(events));
  }

  @Override
  public TimelineView timeline(String instanceId) {
    ensureProcessEngine();

    FlowableFlowInstanceService service = buildFlowInstanceService();

    ArrayList<TimelineView.NodeTimelineView> nodes = new ArrayList<TimelineView.NodeTimelineView>();
    for (FlowTimeline timeline : service.getTimeLine(instanceId)) {
      if (timeline == null || timeline.getFlowNode() == null) {
        continue;
      }

      ArrayList<TaskView> taskViews = new ArrayList<TaskView>();
      for (FlowTimeline.TaskInstance taskInstance : timeline.getTaskInstances()) {
        List<String> candidateUsers = taskInstance.getCandidateUsers() == null
            ? Collections.<String>emptyList()
            : taskInstance.getCandidateUsers();
        taskViews.add(new TaskView(
            taskInstance.getTaskId(),
            timeline.getFlowNode().getId(),
            taskInstance.getAssignee(),
            candidateUsers,
            taskInstance.getState(),
            null,
            taskInstance.getComment()
        ));
      }

      nodes.add(new TimelineView.NodeTimelineView(
          timeline.getFlowNode().getId(),
          timeline.getState(),
          taskViews
      ));
    }
    return new TimelineView(instanceId, nodes);
  }

  @Override
  public boolean isFinished(String instanceId) {
    ensureProcessEngine();
    return processEngine.getRuntimeService().createProcessInstanceQuery()
        .processInstanceId(instanceId)
        .count() == 0;
  }

  @Override
  public void reset() {
    taskEventsByInstance.clear();
    taskActionFacts.clear();
    instanceEventsByInstance.clear();
    sequence = 1L;
    clearEventCollectors();
    if (processEngine != null) {
      ProcessEngines.unregister(processEngine);
      processEngine.close();
      processEngine = null;
    }
  }

  private StructuredFlowDefinition structuredDefinition(String json) {
    return codec.decode(json).get();
  }

  private FlowableFlowDefinitionService buildFlowDefinitionService(String flowKey, String rawJson) {
    FlowableFlowDefinitionService service = new FlowableFlowDefinitionService();
    setField(service, "repositoryService", buildRepositoryService(flowKey, rawJson));
    setField(service, "processEngineConfiguration", buildProcessEngineConfiguration());
    return service;
  }

  private RepositoryService buildRepositoryService(String flowKey, String rawJson) {
    RepositoryService repositoryService = mock(RepositoryService.class);
    ProcessDefinitionQuery query = mock(ProcessDefinitionQuery.class);
    ProcessDefinition processDefinition = mock(ProcessDefinition.class);

    when(repositoryService.createProcessDefinitionQuery()).thenReturn(query);
    when(query.processDefinitionKey(flowKey)).thenReturn(query);
    when(query.latestVersion()).thenReturn(query);
    when(query.singleResult()).thenReturn(processDefinition);
    when(processDefinition.getId()).thenReturn("process-definition-" + flowKey);
    when(repositoryService.getBpmnModel("process-definition-" + flowKey)).thenReturn(buildBpmnModel(rawJson));
    return repositoryService;
  }

  private SpringProcessEngineConfiguration buildProcessEngineConfiguration() {
    SpringProcessEngineConfiguration configuration = mock(SpringProcessEngineConfiguration.class);
    ExpressionManager expressionManager = mock(ExpressionManager.class);

    when(configuration.getExpressionManager()).thenReturn(expressionManager);
    when(expressionManager.createExpression(anyString())).thenAnswer(invocation -> new Expression() {
      @Override
      public Object getValue(VariableContainer variableContainer) {
        return Boolean.TRUE;
      }

      @Override
      public void setValue(Object value, VariableContainer variableContainer) {
        throw new UnsupportedOperationException();
      }

      @Override
      public String getExpressionText() {
        return invocation.getArgument(0, String.class);
      }
    });
    return configuration;
  }

  private BpmnModel buildBpmnModel(String rawJson) {
    BpmnModel model = new BpmnModel();
    Process process = new Process();
    model.addProcess(process);
    BpmnUtil.saveFlowDefinitionRawJson(process, rawJson);
    return model;
  }

  private void ensureProcessEngine() {
    if (processEngine == null) {
      installEventCollectors();
      Map<Object, Object> callbackBeans = buildCallbackBeans();
      ProcessEngineConfigurationImpl configuration = (ProcessEngineConfigurationImpl) ProcessEngineConfiguration.createStandaloneInMemProcessEngineConfiguration()
          .setEngineName(ProcessEngines.NAME_DEFAULT)
          .setDatabaseSchemaUpdate("true")
          .setAsyncExecutorActivate(false)
          .setAsyncHistoryExecutorActivate(false)
          .setHistory("full");
      configuration.setExpressionManager(new ProcessExpressionManager(callbackBeans));
      processEngine = configuration.buildProcessEngine();
      wireCallbackBeans(callbackBeans);
    }
  }

  private FlowableFlowTaskService buildFlowTaskService() {
    org.flowable.engine.TaskService taskService = processEngine.getTaskService();
    FlowableAddSignTaskManager addSignTaskManager = new FlowableAddSignTaskManager();
    setField(addSignTaskManager, "taskService", taskService);
    setField(addSignTaskManager, "runtimeService", processEngine.getRuntimeService());
    AddSignTaskCompletionListener addSignTaskCompletionListener = new AddSignTaskCompletionListener();
    setField(addSignTaskCompletionListener, "taskService", taskService);
    setField(addSignTaskCompletionListener, "addSignTaskManager", addSignTaskManager);

    FlowableApproveAction approveAction = new FlowableApproveAction();
    setField(approveAction, "taskService", taskService);
    setField(approveAction, "addSignTaskCompletionListener", addSignTaskCompletionListener);
    setField(approveAction, "addSignTaskManager", addSignTaskManager);

    FlowableAddSignAction addSignAction = new FlowableAddSignAction(processEngine.getRuntimeService());
    setField(addSignAction, "taskService", taskService);
    setField(addSignAction, "addSignTaskManager", addSignTaskManager);

    ArrayList<FlowTaskAction<?>> actions = new ArrayList<FlowTaskAction<?>>();
    actions.add(approveAction);
    actions.add(configureAction(new FlowableCompleteAction(), taskService));
    actions.add(configureAction(new FlowableRejectAction(), taskService));
    actions.add(configureAction(new FlowableRollbackAction(), taskService));
    actions.add(configureAction(new FlowableTransferAction(), taskService));
    actions.add(addSignAction);
    actions.add(configureAction(new FlowableFreeRejectAction(), taskService, processEngine.getRuntimeService()));
    actions.add(configureAction(new FlowableFreeRecoverAction(), taskService, processEngine.getRuntimeService()));
    actions.add(configureAction(new FlowableFreeApprovalAction(), taskService));

    FlowableFlowTaskService service = new FlowableFlowTaskService(taskService, actions);
    setField(service, "historyService", processEngine.getHistoryService());
    setField(service, "taskEventDispatcher", taskEventDispatcher);
    setField(service, "repositoryService", processEngine.getRepositoryService());
    setField(service, "flowFormServices", Collections.singletonList(new NoopFlowFormService()));
    setFieldIfPresent(service, "flowDefinitionService", buildRuntimeFlowDefinitionService());
    return service;
  }

  private FlowableFlowInstanceService buildFlowInstanceService() {
    return new FlowableFlowInstanceService(
        processEngine.getTaskService(),
        processEngine.getRuntimeService(),
        processEngine.getRepositoryService(),
        processEngine.getHistoryService(),
        buildRuntimeFlowDefinitionService(),
        Collections.emptyList(),
        instanceEventDispatcher
    );
  }

  private FlowableFlowDefinitionService buildRuntimeFlowDefinitionService() {
    FlowableFlowDefinitionService service = new FlowableFlowDefinitionService();
    setField(service, "repositoryService", processEngine.getRepositoryService());
    return service;
  }

  private <T> T configureAction(T action, org.flowable.engine.TaskService taskService) {
    setField(action, "taskService", taskService);
    return action;
  }

  private <T> T configureAction(
      T action,
      org.flowable.engine.TaskService taskService,
      org.flowable.engine.RuntimeService runtimeService) {
    setField(action, "taskService", taskService);
    setField(action, "runtimeService", runtimeService);
    return action;
  }

  private TaskView toTaskView(Task task) {
    List<String> candidateUsers = processEngine.getTaskService().getIdentityLinksForTask(task.getId()).stream()
        .map(IdentityLink::getUserId)
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
    return new TaskView(task.getId(), task.getTaskDefinitionKey(), task.getAssignee(), candidateUsers,
        TaskState.CREATED, null, null);
  }

  private TranspiledModelSnapshot toSnapshot(StructuredFlowDefinition definition) {
    List<TranspiledModelSnapshot.NodeSnapshot> nodes = new ArrayList<TranspiledModelSnapshot.NodeSnapshot>();
    for (FlowNode node : definition.getNodes()) {
      nodes.add(new TranspiledModelSnapshot.NodeSnapshot(
          node.getId(),
          node.getType(),
          node.getName(),
          node instanceof ApprovalNode ? ((ApprovalNode) node).getSubType() : null,
          nodeProperties(node)
      ));
    }

    List<TranspiledModelSnapshot.EdgeSnapshot> edges = new ArrayList<TranspiledModelSnapshot.EdgeSnapshot>();
    for (FlowEdgeV2 edge : definition.getEdges()) {
      edges.add(new TranspiledModelSnapshot.EdgeSnapshot(
          edge.getId(),
          edge.getType() == null ? null : edge.getType().name(),
          edge.getSourceId(),
          edge.getTargetId(),
          edge.getName(),
          edgeConditionExpression(edge)
      ));
    }

    return new TranspiledModelSnapshot(definition.getId(), definition.getName(), nodes, edges);
  }

  private Map<String, Object> nodeProperties(FlowNode node) {
    LinkedHashMap<String, Object> properties = new LinkedHashMap<String, Object>();

    if (node instanceof ApprovalNode) {
      ApprovalNode approvalNode = (ApprovalNode) node;
      if (approvalNode.getActions() != null) {
        properties.put("actions", approvalNode.getActions());
      }
      if (approvalNode.getNoCandidateMode() != null) {
        properties.put("noCandidateMode", approvalNode.getNoCandidateMode().toString());
      }
      if (approvalNode.getReAssignUsers() != null) {
        properties.put("reAssignUsers", approvalNode.getReAssignUsers());
      }
    }
    if (node instanceof MultipleApprovalNode) {
      MultipleApprovalNode multipleApprovalNode = (MultipleApprovalNode) node;
      properties.put("multipleMode", multipleApprovalNode.getMultipleMode());
      properties.put("percentage", multipleApprovalNode.getPercentage());
      properties.put("rejectAction", multipleApprovalNode.getRejectAction());
    }
    if (node instanceof GatewayNode) {
      properties.put("splitType", ((GatewayNode) node).getSplitType());
    }
    if (node instanceof JoinNode) {
      properties.put("joinType", ((JoinNode) node).getJoinType());
    }
    if (node instanceof SubflowNode) {
      SubflowNode subflowNode = (SubflowNode) node;
      properties.put("subFlowKey", subflowNode.getSubFlowKey());
      properties.put("rejectAction", subflowNode.getRejectAction());
      properties.put("inParameters", subflowNode.getInParameters().size());
      properties.put("outParameters", subflowNode.getOutParameters().size());
    }
    return properties;
  }

  private String edgeConditionExpression(FlowEdgeV2 edge) {
    Object condition = edge.getCondition();
    if (condition == null) {
      return null;
    }
    if (condition instanceof Condition) {
      return ((Condition) condition).toExpression();
    }
    return String.valueOf(condition);
  }

  private void installEventCollectors() {
    taskEventDispatcher = new TaskEventDispatcher();
    instanceEventDispatcher = new InstanceEventDispatcher();
    setField(taskEventDispatcher, "listeners", Collections.singletonList(new CapturingFlowTaskListener()));
    setField(instanceEventDispatcher, "listeners", Collections.singletonList(new CapturingFlowInstanceListener()));
  }

  private void clearEventCollectors() {
    taskEventDispatcher = null;
    instanceEventDispatcher = null;
  }

  private Map<Object, Object> buildCallbackBeans() {
    LinkedHashMap<Object, Object> beans = new LinkedHashMap<Object, Object>();
    registerCallbackBean(beans, FailServiceTask.BEAN_NAME, new FailServiceTask());
    registerCallbackBean(beans, InitializeFlowServiceTask.BEAN_NAME, new InitializeFlowServiceTask());
    registerCallbackBean(beans, InitializeMultipleApprovalServiceTask.BEAN_NAME, new InitializeMultipleApprovalServiceTask());
    registerCallbackBean(beans, SetProcessOutcomeServiceTask.BEAN_NAME, new SetProcessOutcomeServiceTask());
    registerCallbackBean(beans, CreateSingleApprovalTaskListener.BEAN_NAME, new CreateSingleApprovalTaskListener());
    registerCallbackBean(beans, SingleApproveCompletedTaskListener.BEAN_NAME, new SingleApproveCompletedTaskListener());
    registerCallbackBean(beans, CreateMultipleApprovalTaskListener.BEAN_NAME, new CreateMultipleApprovalTaskListener());
    registerCallbackBean(beans, MultipleApprovalCompletedTaskListener.BEAN_NAME, new MultipleApprovalCompletedTaskListener());
    registerCallbackBean(beans, MultipleApprovalStartExecutionListener.BEAN_NAME, new MultipleApprovalStartExecutionListener());
    registerCallbackBean(beans, MultipleApprovalEndExecutionListener.BEAN_NAME, new MultipleApprovalEndExecutionListener());
    registerCallbackBean(beans, FlowStartExecutionListener.BEAN_NAME, new FlowStartExecutionListener());
    registerCallbackBean(beans, FlowEndExecutionListener.BEAN_NAME, new FlowEndExecutionListener());
    return beans;
  }

  private void registerCallbackBean(Map<Object, Object> beans, String beanName, Object bean) {
    beans.put(beanName, bean);
  }

  private void wireCallbackBeans(Map<Object, Object> callbackBeans) {
    for (Object bean : callbackBeans.values()) {
      setFieldIfPresent(bean, "repositoryService", processEngine.getRepositoryService());
      setFieldIfPresent(bean, "historyService", processEngine.getHistoryService());
      setFieldIfPresent(bean, "taskService", processEngine.getTaskService());
      setFieldIfPresent(bean, "instanceEventDispatcher", instanceEventDispatcher);
      setFieldIfPresent(bean, "taskEventDispatcher", taskEventDispatcher);
    }
  }

  private void addHistoryVariable(Map<String, Object> history, HistoricVariableInstance variable) {
    if (variable == null) {
      return;
    }

    String variableName = variable.getVariableName();
    if (variable.getTaskId() == null) {
      history.put(variableName, variable.getValue());
      return;
    }
    if (Constants.TASK_FORM_DATA_KEY.equals(variableName)) {
      history.put(taskHistoryKey(variable.getTaskId(), "formData"), variable.getValue());
      return;
    }
    if (Constants.TASK_PAYLOAD_KEY.equals(variableName)) {
      history.put(taskHistoryKey(variable.getTaskId(), "payload"), normalizePayload(variable.getValue()));
    }
  }

  private Object normalizePayload(Object payload) {
    if (!(payload instanceof String)) {
      return payload;
    }

    String text = String.valueOf(payload);
    String trimmed = text.trim();
    if (trimmed.startsWith("{")) {
      return Json.toMap(trimmed);
    }
    if (trimmed.startsWith("[")) {
      return Json.toList(trimmed);
    }
    return text;
  }

  private String latestComment(String taskId) {
    List<Comment> comments = processEngine.getTaskService().getTaskComments(taskId);
    return comments.stream()
        .filter(Objects::nonNull)
        .max(Comparator.comparing(Comment::getTime, Comparator.nullsFirst(Date::compareTo)))
        .map(Comment::getFullMessage)
        .orElse(null);
  }

  private String taskHistoryKey(String taskId, String field) {
    return "task." + taskId + "." + field;
  }

  private long nextSequence() {
    return sequence++;
  }

  private void setField(Object target, String fieldName, Object value) {
    try {
      Field field = target.getClass().getDeclaredField(fieldName);
      field.setAccessible(true);
      field.set(target, value);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("Failed to set field " + fieldName, e);
    }
  }

  private void setFieldIfPresent(Object target, String fieldName, Object value) {
    try {
      Field field = target.getClass().getDeclaredField(fieldName);
      field.setAccessible(true);
      field.set(target, value);
    } catch (NoSuchFieldException ignored) {
      // Callback bean does not need this collaborator in the testkit runtime.
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("Failed to set field " + fieldName, e);
    }
  }

  private final class CapturingFlowTaskListener implements FlowTaskListener {
    @Override
    public void onEvent(FlowTaskEvent event) {
      if (event.getFactKind() == FlowTaskEvent.FactKind.ACTION_COMPLETED) {
        taskActionFacts.add(event);
        return;
      }
      taskEventsByInstance.computeIfAbsent(event.getInstanceId(), key -> new ArrayList<TaskEventView>())
          .add(new TaskEventView(
              event.getInstanceId(),
              event.getFlowKey(),
              event.getTaskId(),
              event.getNodeId(),
              event.getState(),
              event.getAssignee(),
              event.getCandidateUsers(),
              event.getActionName(),
              event.getComment(),
              nextSequence()
          ));
    }
  }

  private final class CapturingFlowInstanceListener implements FlowInstanceListener {
    @Override
    public void onEvent(FlowInstanceEvent event) {
      instanceEventsByInstance.computeIfAbsent(event.getInstanceId(), key -> new ArrayList<InstanceEventView>())
          .add(new InstanceEventView(
              event.getInstanceId(),
              event.getFlowKey(),
              event.getEventType().name(),
              normalizeApproved(event.getEventType()),
              resolveInstanceComment(event),
              event.getLastTaskId(),
              nextSequence()
          ));
    }

    private String resolveInstanceComment(FlowInstanceEvent event) {
      if (event.getLastTaskId() == null || isManualInstanceTermination(event.getInstanceId())) {
        return null;
      }
      return latestComment(event.getLastTaskId());
    }

    private boolean isManualInstanceTermination(String instanceId) {
      return processEngine.getHistoryService()
          .createHistoricVariableInstanceQuery()
          .processInstanceId(instanceId)
          .variableName(Constants.PROCESS_CANCEL_TIME_VARIABLE_NAME)
          .singleResult() != null;
    }

    private Boolean normalizeApproved(FlowInstanceEvent.EventType eventType) {
      if (eventType == FlowInstanceEvent.EventType.APPROVED) {
        return Boolean.TRUE;
      }
      if (eventType == FlowInstanceEvent.EventType.REJECTED) {
        return Boolean.FALSE;
      }
      return null;
    }
  }

  private static final class NoopFlowFormService implements FlowFormService {
    @Override
    public String getType() {
      return "default";
    }

    @Override
    public Try<SchemaNode> getInitialFormSchema(String formKey, List<NodeFormFieldPermission> permissions) {
      return Try.success(null);
    }

    @Override
    public Try<String> submitInitialForm(String instanceId, String formKey, String userId,
                                         List<NodeFormFieldPermission> permissions, Map<String, Object> formData) {
      return Try.success(null);
    }

    @Override
    public Try<SchemaNode> getTaskFormSchema(String instanceId, String taskId, String formKey, String formId,
                                             List<NodeFormFieldPermission> permissions, List<String> actions) {
      return Try.success(null);
    }

    @Override
    public Try<Void> submitTaskForm(String instanceId, String taskId, String formKey, String formId, String userId,
                                    List<NodeFormFieldPermission> permissions, Map<String, Object> formData) {
      return Try.success(null);
    }
  }
}
