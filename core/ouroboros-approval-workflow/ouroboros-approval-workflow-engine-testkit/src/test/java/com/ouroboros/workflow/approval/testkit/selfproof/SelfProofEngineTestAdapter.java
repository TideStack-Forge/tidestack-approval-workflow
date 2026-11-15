package com.ouroboros.workflow.approval.testkit.selfproof;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;

import com.ouroboros.util.Json;
import com.ouroboros.workflow.approval.definition.FlowDefinition;
import com.ouroboros.workflow.approval.definition.FlowNode;
import com.ouroboros.workflow.approval.definition.codec.CompositeFlowDefinitionCodec;
import com.ouroboros.workflow.approval.definition.condition.CombinationCondition;
import com.ouroboros.workflow.approval.definition.condition.CompareCondition;
import com.ouroboros.workflow.approval.definition.condition.Condition;
import com.ouroboros.workflow.approval.definition.graph.StructuredFlowDefinition;
import com.ouroboros.workflow.approval.definition.nodes.ApprovalNode;
import com.ouroboros.workflow.approval.definition.nodes.GatewayNode;
import com.ouroboros.workflow.approval.definition.nodes.JoinNode;
import com.ouroboros.workflow.approval.definition.nodes.MultipleApprovalNode;
import com.ouroboros.workflow.approval.definition.nodes.StartNode;
import com.ouroboros.workflow.approval.definition.nodes.SubflowNode;
import com.ouroboros.workflow.approval.definition.v2.FlowEdgeType;
import com.ouroboros.workflow.approval.definition.v2.FlowEdgeV2;
import com.ouroboros.workflow.approval.model.TaskState;
import com.ouroboros.workflow.approval.semantics.SplitType;
import com.ouroboros.workflow.approval.semantics.StableSplitSemantics;
import com.ouroboros.workflow.approval.testkit.adapter.EngineTestAdapter;
import com.ouroboros.workflow.approval.testkit.snapshot.InstanceEventView;
import com.ouroboros.workflow.approval.testkit.snapshot.RoundTripSnapshot;
import com.ouroboros.workflow.approval.testkit.snapshot.RuntimeInstance;
import com.ouroboros.workflow.approval.testkit.snapshot.StartRequest;
import com.ouroboros.workflow.approval.testkit.snapshot.TaskEventView;
import com.ouroboros.workflow.approval.testkit.snapshot.TaskView;
import com.ouroboros.workflow.approval.testkit.snapshot.TimelineView;
import com.ouroboros.workflow.approval.testkit.snapshot.TranspiledModelSnapshot;
import com.ouroboros.workflow.approval.util.StructuredFlowRuntimeNavigator;

// Self-proof adapter only validates the shared contract itself; it is not evidence of any real engine capability.
public class SelfProofEngineTestAdapter implements EngineTestAdapter {
  private final CompositeFlowDefinitionCodec codec = new CompositeFlowDefinitionCodec();
  private final StructuredFlowRuntimeNavigator navigator = new StructuredFlowRuntimeNavigator();
  private final Map<String, StructuredFlowDefinition> deployments = new LinkedHashMap<String, StructuredFlowDefinition>();
  private final Map<String, SelfProofRuntimeState> runtimeStates = new LinkedHashMap<String, SelfProofRuntimeState>();
  private int instanceSequence = 1;
  private long eventSequence = 1;

  @Override
  public FlowDefinition loadDefinition(String json) {
    return codec.encodeV1(codec.decode(json).get()).get();
  }

  public List<FlowNode> flowNodes(String json) {
    return navigator.getFlowNodes(codec.decode(json).get());
  }

  public List<ApprovalNode> candidateApprovalNodes(String json, Map<String, Object> conditionContext) {
    StructuredFlowDefinition definition = codec.decode(json).get();
    if (conditionContext == null || conditionContext.isEmpty()) {
      return navigator.getUserSelectCandidateApprovalNodes(definition);
    }
    return navigator.getUserSelectCandidateApprovalNodes(definition, edge -> matches(definition, edge, conditionContext));
  }

  @Override
  public TranspiledModelSnapshot transpile(String json) {
    return toSnapshot(codec.decode(json).get());
  }

  @Override
  public RoundTripSnapshot roundTrip(String json) {
    StructuredFlowDefinition definition = codec.decode(json).get();
    return new RoundTripSnapshot(
        json,
        Json.toJsonString(codec.encodeV2(definition).get()),
        toSnapshot(definition)
    );
  }

  @Override
  public void deploy(String flowKey, String json) {
    deployments.put(flowKey, codec.decode(json).get());
  }

  @Override
  public RuntimeInstance start(String flowKey, StartRequest request) {
    StructuredFlowDefinition definition = deployments.get(flowKey);
    if (definition == null) {
      throw new IllegalArgumentException("No deployment found for flow key: " + flowKey);
    }

    String instanceId = flowKey + "-instance-" + instanceSequence++;
    RuntimeInstance runtimeInstance = new RuntimeInstance(instanceId, flowKey);
    SelfProofRuntimeState runtimeState = new SelfProofRuntimeState(
        runtimeInstance,
        definition,
        request,
        deployments,
        this::nextEventSequence);
    runtimeState.initializeStartTask();
    runtimeStates.put(instanceId, runtimeState);
    return runtimeInstance;
  }

  @Override
  public void completeStartTask(String instanceId) {
    SelfProofRuntimeState runtimeState = getRuntimeState(instanceId);
    runtimeState.completeStartTask();
  }

  @Override
  public void doAction(String taskId, String action, String userId, String comment, Map<String, Object> formData, Object payload) {
    for (SelfProofRuntimeState runtimeState : runtimeStates.values()) {
      if (runtimeState.completeTask(taskId, action, userId, comment, formData, payload)) {
        return;
      }
    }
    throw new IllegalArgumentException("No runtime task found: " + taskId);
  }

  @Override
  public void pauseInstance(String instanceId, String userId, String comment) {
    getRuntimeState(instanceId).pause(comment);
  }

  @Override
  public void resumeInstance(String instanceId, String userId) {
    getRuntimeState(instanceId).resume();
  }

  @Override
  public void cancelInstance(String instanceId, String userId, String comment) {
    getRuntimeState(instanceId).cancel();
  }

  @Override
  public void completeInstance(String instanceId, String userId, boolean approved, String comment) {
    getRuntimeState(instanceId).complete(approved);
  }

  @Override
  public List<TaskView> activeTasks(String instanceId) {
    return getRuntimeState(instanceId).activeTasks();
  }

  @Override
  public List<TaskView> activeTasks(String instanceId, String nodeId) {
    return getRuntimeState(instanceId).activeTasks(nodeId);
  }

  @Override
  public Map<String, Object> historyVariables(String instanceId) {
    return getRuntimeState(instanceId).historyVariables();
  }

  @Override
  public List<TaskEventView> taskEvents(String instanceId) {
    return getRuntimeState(instanceId).taskEvents();
  }

  @Override
  public List<TaskEventView> allTaskEvents() {
    ArrayList<TaskEventView> events = new ArrayList<TaskEventView>();
    for (SelfProofRuntimeState runtimeState : runtimeStates.values()) {
      events.addAll(runtimeState.taskEvents());
    }
    events.sort(Comparator.comparingLong(TaskEventView::getSequence));
    return Collections.unmodifiableList(events);
  }

  @Override
  public List<InstanceEventView> instanceEvents(String instanceId) {
    return getRuntimeState(instanceId).instanceEvents();
  }

  @Override
  public TimelineView timeline(String instanceId) {
    return getRuntimeState(instanceId).timeline();
  }

  @Override
  public boolean isFinished(String instanceId) {
    return getRuntimeState(instanceId).isFinished();
  }

  @Override
  public void reset() {
    deployments.clear();
    runtimeStates.clear();
    instanceSequence = 1;
    eventSequence = 1;
  }

  private long nextEventSequence() {
    return eventSequence++;
  }

  private SelfProofRuntimeState getRuntimeState(String instanceId) {
    SelfProofRuntimeState runtimeState = runtimeStates.get(instanceId);
    if (runtimeState == null) {
      throw new IllegalArgumentException("No runtime instance found: " + instanceId);
    }
    return runtimeState;
  }

  private boolean matches(StructuredFlowDefinition definition, FlowEdgeV2 edge, Map<String, Object> conditionContext) {
    if (definition == null || edge == null || edge.getType() != FlowEdgeType.SEQUENCE) {
      return false;
    }
    if (definition.getNode(edge.getSourceId()) == null || definition.getNode(edge.getTargetId()) == null) {
      return false;
    }
    return evaluateCondition(
        edge.getCondition(),
        conditionContext == null ? Collections.<String, Object>emptyMap() : conditionContext
    );
  }

  private static boolean evaluateCondition(Condition condition, Map<String, Object> conditionContext) {
    if (condition == null) {
      return true;
    }
    if (condition instanceof CompareCondition) {
      return evaluateCompareCondition((CompareCondition) condition, conditionContext);
    }
    if (condition instanceof CombinationCondition) {
      CombinationCondition combinationCondition = (CombinationCondition) condition;
      if ("or".equalsIgnoreCase(combinationCondition.getConjunction())) {
        for (Condition child : combinationCondition.getChildren()) {
          if (evaluateCondition(child, conditionContext)) {
            return true;
          }
        }
        return false;
      }
      for (Condition child : combinationCondition.getChildren()) {
        if (!evaluateCondition(child, conditionContext)) {
          return false;
        }
      }
      return true;
    }
    return false;
  }

  private static boolean evaluateCompareCondition(CompareCondition condition, Map<String, Object> conditionContext) {
    Object leftValue = conditionContext.get(condition.getLeft().getField());
    Object rightValue = condition.getRight();
    String op = condition.getOp();

    if ("equal".equals(op)) {
      return Objects.equals(leftValue, rightValue);
    }
    if ("not_equal".equals(op)) {
      return !Objects.equals(leftValue, rightValue);
    }
    if ("greater".equals(op)) {
      return compare(leftValue, rightValue) > 0;
    }
    if ("greater_or_equal".equals(op)) {
      return compare(leftValue, rightValue) >= 0;
    }
    if ("less".equals(op)) {
      return compare(leftValue, rightValue) < 0;
    }
    if ("less_or_equal".equals(op)) {
      return compare(leftValue, rightValue) <= 0;
    }
    if ("between".equals(op)) {
      List<Object> values = asObjectList(rightValue);
      if (values == null || values.size() < 2) {
        return false;
      }
      return compare(leftValue, values.get(0)) >= 0 && compare(leftValue, values.get(1)) <= 0;
    }
    if ("not_between".equals(op)) {
      List<Object> values = asObjectList(rightValue);
      if (values == null || values.size() < 2) {
        return false;
      }
      return compare(leftValue, values.get(0)) < 0 || compare(leftValue, values.get(1)) > 0;
    }
    if ("is_empty".equals(op)) {
      return leftValue == null;
    }
    if ("is_not_empty".equals(op)) {
      return leftValue != null;
    }
    if ("like".equals(op)) {
      return leftValue != null && String.valueOf(leftValue).contains(String.valueOf(rightValue));
    }
    if ("not_like".equals(op)) {
      return leftValue != null && !String.valueOf(leftValue).contains(String.valueOf(rightValue));
    }
    if ("starts_with".equals(op)) {
      return leftValue != null && String.valueOf(leftValue).startsWith(String.valueOf(rightValue));
    }
    if ("ends_with".equals(op)) {
      return leftValue != null && String.valueOf(leftValue).endsWith(String.valueOf(rightValue));
    }
    return false;
  }

  private static int compare(Object leftValue, Object rightValue) {
    if (leftValue == null || rightValue == null) {
      return -1;
    }
    if (leftValue instanceof Number && rightValue instanceof Number) {
      double diff = ((Number) leftValue).doubleValue() - ((Number) rightValue).doubleValue();
      if (diff > 0) {
        return 1;
      }
      if (diff < 0) {
        return -1;
      }
      return 0;
    }
    if (sameType(leftValue, rightValue)) {
      if (leftValue instanceof String) {
        return ((String) leftValue).compareTo((String) rightValue);
      }
      if (leftValue instanceof Boolean) {
        return ((Boolean) leftValue).compareTo((Boolean) rightValue);
      }
    }
    return String.valueOf(leftValue).compareTo(String.valueOf(rightValue));
  }

  private static List<Object> asObjectList(Object value) {
    if (!(value instanceof List<?>)) {
      return null;
    }
    return ((List<?>) value).stream().collect(Collectors.toList());
  }

  private static boolean sameType(Object left, Object right) {
    return left != null && right != null && left.getClass().equals(right.getClass());
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
          edge.getCondition() == null ? null : edge.getCondition().toExpression()
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

  private static final class SelfProofRuntimeState {
    private final RuntimeInstance runtimeInstance;
    private final StructuredFlowDefinition definition;
    private final StartRequest request;
    private final Map<String, StructuredFlowDefinition> deployments;
    private final LongSupplier sequenceSupplier;
    private final Map<String, MutableTaskRecord> activeTasks = new LinkedHashMap<String, MutableTaskRecord>();
    private final Map<String, NodeRuntime> nodeRuntimes = new LinkedHashMap<String, NodeRuntime>();
    private final Map<String, JoinRuntime> joinRuntimes = new LinkedHashMap<String, JoinRuntime>();
    private final Map<String, Object> historyVariables = new LinkedHashMap<String, Object>();
    private final List<TaskEventView> taskEvents = new ArrayList<TaskEventView>();
    private final List<InstanceEventView> instanceEvents = new ArrayList<InstanceEventView>();
    private final Map<String, Integer> taskSequenceByNode = new HashMap<String, Integer>();
    private boolean finished;
    private boolean suspended;
    private String lastCompletedTaskId;

    private SelfProofRuntimeState(
        RuntimeInstance runtimeInstance,
        StructuredFlowDefinition definition,
        StartRequest request,
        Map<String, StructuredFlowDefinition> deployments,
        LongSupplier sequenceSupplier) {
      this.runtimeInstance = runtimeInstance;
      this.definition = definition;
      this.request = request;
      this.deployments = deployments;
      this.sequenceSupplier = sequenceSupplier;
      instanceEvents.add(new InstanceEventView(
          runtimeInstance.getInstanceId(),
          runtimeInstance.getFlowKey(),
          "STARTED",
          null,
          null,
          null,
          nextSequence()
      ));
    }

    private void initializeStartTask() {
      StartNode startNode = (StartNode) definition.getStartFlowNode().orElseThrow(() -> new IllegalStateException("Missing start node"));
      MutableTaskRecord task = new MutableTaskRecord(
          nextTaskId(startNode.getId()),
          startNode.getId(),
          request.getStarter(),
          Collections.<String>emptyList(),
          TaskState.CREATED,
          null,
          null
      );
      activeTasks.put(task.taskId, task);
    }

    private void completeStartTask() {
      MutableTaskRecord startTask = activeTasks.values().isEmpty()
          ? null
          : activeTasks.values().iterator().next();
      if (startTask != null) {
        lastCompletedTaskId = startTask.taskId;
      }
      activeTasks.clear();
      StartNode startNode = (StartNode) definition.getStartFlowNode().orElseThrow(() -> new IllegalStateException("Missing start node"));
      activateFromSourceNode(startNode.getId());
    }

    private boolean completeTask(
        String taskId,
        String action,
        String userId,
        String comment,
        Map<String, Object> formData,
        Object payload) {
      MutableTaskRecord task = activeTasks.remove(taskId);
      if (task == null) {
        return false;
      }
      lastCompletedTaskId = taskId;

      if (formData != null) {
        historyVariables.put(taskHistoryKey(taskId, "formData"), formData);
      }
      if (payload != null) {
        historyVariables.put(taskHistoryKey(taskId, "payload"), payload);
      }
      if (comment != null) {
        historyVariables.put(taskHistoryKey(taskId, "comment"), comment);
      }

      FlowNode flowNode = definition.getNode(task.nodeId);
      if (!(flowNode instanceof ApprovalNode)) {
        throw new IllegalStateException("Task node is not approval node: " + task.nodeId);
      }

      if (flowNode instanceof MultipleApprovalNode) {
        completeMultipleApproval((MultipleApprovalNode) flowNode, task, action, userId, comment);
      } else {
        completeSingleApproval((ApprovalNode) flowNode, task, action, userId, comment);
      }
      return true;
    }

    private boolean isFinished() {
      return finished;
    }

    private void pause(String comment) {
      if (finished || suspended) {
        return;
      }
      suspended = true;
      instanceEvents.add(new InstanceEventView(
          runtimeInstance.getInstanceId(),
          runtimeInstance.getFlowKey(),
          "SUSPENDED",
          null,
          null,
          null,
          nextSequence()
      ));
    }

    private void resume() {
      if (finished || !suspended) {
        return;
      }
      suspended = false;
      instanceEvents.add(new InstanceEventView(
          runtimeInstance.getInstanceId(),
          runtimeInstance.getFlowKey(),
          "RESUMED",
          null,
          null,
          null,
          nextSequence()
      ));
    }

    private void cancel() {
      finishInstance("CANCELLED", null);
    }

    private void complete(boolean approved) {
      finishInstance(approved ? "APPROVED" : "REJECTED", approved ? Boolean.TRUE : Boolean.FALSE);
    }

    private List<TaskView> activeTasks() {
      ArrayList<TaskView> result = new ArrayList<TaskView>();
      for (MutableTaskRecord task : activeTasks.values()) {
        result.add(task.toView());
      }
      return Collections.unmodifiableList(result);
    }

    private List<TaskView> activeTasks(String nodeId) {
      List<TaskView> result = new ArrayList<TaskView>();
      for (MutableTaskRecord task : activeTasks.values()) {
        if (Objects.equals(nodeId, task.nodeId)) {
          result.add(task.toView());
        }
      }
      return Collections.unmodifiableList(result);
    }

    private Map<String, Object> historyVariables() {
      return Collections.unmodifiableMap(new LinkedHashMap<String, Object>(historyVariables));
    }

    private List<TaskEventView> taskEvents() {
      return Collections.unmodifiableList(new ArrayList<TaskEventView>(taskEvents));
    }

    private List<InstanceEventView> instanceEvents() {
      return Collections.unmodifiableList(new ArrayList<InstanceEventView>(instanceEvents));
    }

    private TimelineView timeline() {
      ArrayList<TimelineView.NodeTimelineView> nodes = new ArrayList<TimelineView.NodeTimelineView>();
      for (FlowNode flowNode : definition.getNodes()) {
        NodeRuntime nodeRuntime = nodeRuntimes.get(flowNode.getId());
        if (nodeRuntime != null && !nodeRuntime.tasks.isEmpty()) {
          nodes.add(nodeRuntime.toTimelineView(suspended));
        }
      }
      return new TimelineView(runtimeInstance.getInstanceId(), nodes);
    }

    private void activateFrom(FlowNode node) {
      activateFrom(node, null);
    }

    private void activateFrom(FlowNode node, String fromNodeId) {
      if (node == null) {
        finish(true, null);
        return;
      }
      if (node instanceof JoinNode) {
        if (!arriveJoin((JoinNode) node, fromNodeId)) {
          return;
        }
        activateNextNodeOrCompleteBranch(node);
        return;
      }
      if (node instanceof GatewayNode) {
        for (FlowEdgeV2 edge : definition.getOutgoingEdges(node.getId())) {
          if (edge.getType() == FlowEdgeType.SEQUENCE) {
            activateFrom(definition.getNode(edge.getTargetId()), node.getId());
          }
        }
        return;
      }
      if (node instanceof ApprovalNode) {
        activateApprovalNode((ApprovalNode) node);
        return;
      }
      if (node instanceof SubflowNode) {
        activateSubflow((SubflowNode) node);
        return;
      }
      activateFromSourceNode(node.getId());
    }

    private void activateFromSourceNode(String sourceId) {
      List<FlowEdgeV2> outgoing = definition.getOutgoingEdges(sourceId).stream()
          .filter(edge -> edge.getType() == FlowEdgeType.SEQUENCE)
          .collect(Collectors.toList());
      if (outgoing.isEmpty()) {
        finish(true, null);
        return;
      }
      if (outgoing.size() == 1) {
        activateFrom(definition.getNode(outgoing.get(0).getTargetId()), sourceId);
        return;
      }
      
      SplitType splitType = StableSplitSemantics.inferSplitType(definition, outgoing);
      if (splitType == SplitType.OR) {
        Map<String, Object> conditionContext = buildConditionContext();
        List<String> activatedTargets = new java.util.ArrayList<String>();
        for (FlowEdgeV2 edge : outgoing) {
          if (edge.getCondition() == null || evaluateCondition(edge.getCondition(), conditionContext)) {
            activatedTargets.add(edge.getTargetId());
          }
        }
        
        JoinNode correspondingJoin = findCorrespondingJoin(activatedTargets);
        if (correspondingJoin != null) {
          registerOrSplitActivation(correspondingJoin.getId(), activatedTargets);
        }
        
        for (String targetId : activatedTargets) {
          activateFrom(definition.getNode(targetId), sourceId);
        }
        return;
      }
      
      FlowEdgeV2 selected = selectXorEdge(sourceId);
      if (selected == null) {
        finish(false, null);
        return;
      }
      activateFrom(definition.getNode(selected.getTargetId()), sourceId);
    }
    
    private JoinNode findCorrespondingJoin(List<String> branchStartIds) {
      for (FlowNode node : definition.getNodes()) {
        if (node instanceof JoinNode) {
          JoinNode joinNode = (JoinNode) node;
          List<FlowEdgeV2> incoming = definition.getIncomingEdges(joinNode.getId());
          for (FlowEdgeV2 edge : incoming) {
            if (branchStartIds.contains(edge.getSourceId())) {
              return joinNode;
            }
          }
        }
      }
      return null;
    }
    
    private void registerOrSplitActivation(String joinNodeId, List<String> activatedBranchStarts) {
      JoinRuntime runtime = joinRuntimes.get(joinNodeId);
      if (runtime == null) {
        JoinNode joinNode = (JoinNode) definition.getNode(joinNodeId);
        String joinType = joinNode.getJoinType();
        boolean isOrJoin = joinType != null && joinType.equalsIgnoreCase("OR");
        runtime = new JoinRuntime(definition.getIncomingEdges(joinNodeId).size(), isOrJoin);
        joinRuntimes.put(joinNodeId, runtime);
      }
      runtime.registerActivatedBranches(activatedBranchStarts);
    }

    private Map<String, Object> buildConditionContext() {
      Map<String, Object> conditionContext = new LinkedHashMap<String, Object>();
      conditionContext.putAll(request.getInstanceVariables());
      conditionContext.putAll(request.getFormData());
      return conditionContext;
    }

    private boolean arriveJoin(JoinNode joinNode, String fromNodeId) {
      JoinRuntime runtime = joinRuntimes.get(joinNode.getId());
      if (runtime == null) {
        String joinType = joinNode.getJoinType();
        boolean isOrJoin = joinType != null && joinType.equalsIgnoreCase("OR");
        runtime = new JoinRuntime(definition.getIncomingEdges(joinNode.getId()).size(), isOrJoin);
        joinRuntimes.put(joinNode.getId(), runtime);
      }
      if (fromNodeId == null) {
        return runtime.markArrived("__default");
      }
      return runtime.markArrived(fromNodeId);
    }

    private FlowEdgeV2 selectXorEdge(String sourceId) {
      List<FlowEdgeV2> outgoing = definition.getOutgoingEdges(sourceId).stream()
          .filter(edge -> edge.getType() == FlowEdgeType.SEQUENCE)
          .collect(Collectors.toList());
      if (outgoing.isEmpty()) {
        return null;
      }
      Map<String, Object> conditionContext = new LinkedHashMap<String, Object>();
      conditionContext.putAll(request.getInstanceVariables());
      conditionContext.putAll(request.getFormData());

      for (FlowEdgeV2 edge : outgoing) {
        if (edge.getCondition() != null && evaluateCondition(edge.getCondition(), conditionContext)) {
          return edge;
        }
      }
      for (FlowEdgeV2 edge : outgoing) {
        if (edge.getCondition() == null) {
          return edge;
        }
      }
      outgoing.sort(Comparator
          .comparing((FlowEdgeV2 edge) -> edge.getOrder() == null ? Integer.MAX_VALUE : edge.getOrder())
          .thenComparing(edge -> edge.getId() == null ? "" : edge.getId()));
      return outgoing.get(0);
    }

    private void activateApprovalNode(ApprovalNode node) {
      if (node instanceof MultipleApprovalNode) {
        activateMultipleApproval((MultipleApprovalNode) node);
        return;
      }
      activateSingleApproval(node);
    }

    private FlowNode sequenceTarget(String sourceId) {
      for (FlowEdgeV2 edge : definition.getOutgoingEdges(sourceId)) {
        if (edge.getType() == FlowEdgeType.SEQUENCE) {
          return definition.getNode(edge.getTargetId());
        }
      }
      return null;
    }

    private String nextTaskId(String nodeId) {
      Integer current = taskSequenceByNode.get(nodeId);
      int next = current == null ? 1 : current + 1;
      taskSequenceByNode.put(nodeId, next);
      return runtimeInstance.getInstanceId() + ":" + nodeId + ":" + next;
    }

    private void activateSingleApproval(ApprovalNode node) {
      List<String> selectedUsers = request.getSelectedAssignees().get(node.getId());
      if (selectedUsers == null || selectedUsers.isEmpty()) {
        if (node.getNoCandidateMode() != null) {
          handleNoCandidateSingleApproval(node);
          return;
        }
        selectedUsers = Collections.singletonList(request.getStarter());
      }
      List<String> candidateUsers = selectedUsers;
      MutableTaskRecord task = new MutableTaskRecord(
          nextTaskId(node.getId()),
          node.getId(),
          null,
          candidateUsers,
          TaskState.CREATED,
          null,
          null
      );
      nodeRuntime(node).tasks.add(task);
      activeTasks.put(task.taskId, task);
      taskEvents.add(new TaskEventView(
          runtimeInstance.getInstanceId(),
          runtimeInstance.getFlowKey(),
          task.taskId,
          node.getId(),
          TaskState.CREATED,
          null,
          candidateUsers,
          null,
          null,
          nextSequence()
      ));
    }

    private void handleNoCandidateSingleApproval(ApprovalNode node) {
      if ("approved".equalsIgnoreCase(node.getNoCandidateMode().name())) {
        activateNextNodeOrCompleteBranch(node);
        return;
      }
      if ("reAssignUser".equalsIgnoreCase(node.getNoCandidateMode().name())) {
        List<String> fallbackUsers = fallbackAssignees(node);
        if (fallbackUsers.isEmpty()) {
          finish(false, null);
          return;
        }
        MutableTaskRecord task = new MutableTaskRecord(
            nextTaskId(node.getId()),
            node.getId(),
            null,
            fallbackUsers,
            TaskState.CREATED,
            null,
            null
        );
        nodeRuntime(node).tasks.add(task);
        activeTasks.put(task.taskId, task);
        taskEvents.add(new TaskEventView(
            runtimeInstance.getInstanceId(),
            runtimeInstance.getFlowKey(),
            task.taskId,
            node.getId(),
            TaskState.CREATED,
            null,
            fallbackUsers,
            null,
            null,
            nextSequence()
        ));
        return;
      }
      finish(false, null);
    }

    private void activateMultipleApproval(MultipleApprovalNode node) {
      NodeRuntime nodeRuntime = nodeRuntime(node);
      List<String> assignees = request.getSelectedAssignees().get(node.getId());
      if (assignees == null || assignees.isEmpty()) {
        assignees = fallbackAssignees(node);
      }

      nodeRuntime.plannedAssignees.clear();
      nodeRuntime.plannedAssignees.addAll(assignees);

      if (!nodeRuntime.uncreatedEventEmitted) {
        taskEvents.add(new TaskEventView(
            runtimeInstance.getInstanceId(),
            runtimeInstance.getFlowKey(),
            null,
            node.getId(),
            TaskState.UNCREATED,
            null,
            assignees,
            null,
            null,
            nextSequence()
        ));
        nodeRuntime.uncreatedEventEmitted = true;
      }

      if (assignees.isEmpty()) {
        handleNoCandidateMode(node, nodeRuntime);
        return;
      }

      nodeRuntime.state = TaskState.PROCESSING;
      if (isSequential(node)) {
        if (nodeRuntime.tasks.isEmpty()) {
          for (String assignee : assignees) {
            nodeRuntime.tasks.add(new MutableTaskRecord(
                null,
                node.getId(),
                assignee,
                assignees,
                TaskState.UNCREATED,
                null,
                null
            ));
          }
        }
        activateNextSequentialTask(node, nodeRuntime);
      } else {
        for (String assignee : assignees) {
          MutableTaskRecord task = new MutableTaskRecord(
              nextTaskId(node.getId()),
              node.getId(),
              assignee,
              assignees,
              TaskState.CREATED,
              null,
              null
          );
          nodeRuntime.tasks.add(task);
          activeTasks.put(task.taskId, task);
          taskEvents.add(new TaskEventView(
              runtimeInstance.getInstanceId(),
              runtimeInstance.getFlowKey(),
              task.taskId,
              node.getId(),
              TaskState.CREATED,
              assignee,
              task.candidateUsers,
              null,
              null,
              nextSequence()
          ));
        }
      }
    }

    private void handleNoCandidateMode(MultipleApprovalNode node, NodeRuntime nodeRuntime) {
      if (node.getNoCandidateMode() == null || "rejected".equalsIgnoreCase(node.getNoCandidateMode().name())) {
        nodeRuntime.state = TaskState.REJECTED;
        rejectMultipleApproval(node, null);
        return;
      }
      if ("approved".equalsIgnoreCase(node.getNoCandidateMode().name())) {
        nodeRuntime.state = TaskState.APPROVED;
        activateNextNodeOrCompleteBranch(node);
        return;
      }
      if ("reAssignUser".equalsIgnoreCase(node.getNoCandidateMode().name())) {
        List<String> fallbackUsers = node.getReAssignUsers() == null
            ? Collections.<String>emptyList()
            : node.getReAssignUsers();
        if (fallbackUsers.isEmpty()) {
          nodeRuntime.state = TaskState.REJECTED;
          rejectMultipleApproval(node, null);
          return;
        }
        nodeRuntime.plannedAssignees.clear();
        nodeRuntime.plannedAssignees.addAll(fallbackUsers);
        for (String assignee : fallbackUsers) {
          MutableTaskRecord task = new MutableTaskRecord(
              nextTaskId(node.getId()),
              node.getId(),
              assignee,
              fallbackUsers,
              TaskState.CREATED,
              null,
              null
          );
          nodeRuntime.tasks.add(task);
          activeTasks.put(task.taskId, task);
          taskEvents.add(new TaskEventView(
              runtimeInstance.getInstanceId(),
              runtimeInstance.getFlowKey(),
              task.taskId,
              node.getId(),
              TaskState.CREATED,
              assignee,
              task.candidateUsers,
              null,
              null,
              nextSequence()
        ));
      }
    }
    }

    private void activateNextSequentialTask(MultipleApprovalNode node, NodeRuntime nodeRuntime) {
      if (nodeRuntime.nextSequentialIndex >= nodeRuntime.tasks.size()) {
        return;
      }
      MutableTaskRecord task = nodeRuntime.tasks.get(nodeRuntime.nextSequentialIndex);
      task.taskId = nextTaskId(node.getId());
      task.state = nodeRuntime.nextSequentialIndex == 0 ? TaskState.CREATED : TaskState.PROCESSING;
      activeTasks.put(task.taskId, task);
      taskEvents.add(new TaskEventView(
          runtimeInstance.getInstanceId(),
          runtimeInstance.getFlowKey(),
          task.taskId,
          node.getId(),
          TaskState.CREATED,
          task.assignee,
          task.candidateUsers,
          null,
          null,
          nextSequence()
      ));
      nodeRuntime.nextSequentialIndex++;
    }

    private void completeSingleApproval(ApprovalNode node, MutableTaskRecord task, String action, String userId, String comment) {
      task.actionName = action;
      task.comment = comment;
      task.state = isApprovedAction(action) ? TaskState.APPROVED : TaskState.REJECTED;
      nodeRuntime(node).state = task.state;
      taskEvents.add(new TaskEventView(
          runtimeInstance.getInstanceId(),
          runtimeInstance.getFlowKey(),
          task.taskId,
          node.getId(),
          task.state,
          userId,
          task.candidateUsers,
          action,
          comment,
          nextSequence()
      ));
      if (isApprovedAction(action)) {
        activateNextNodeOrCompleteBranch(node);
        return;
      }
      if ("rollback".equalsIgnoreCase(action)) {
        ApprovalNode previous = previousApprovalNode(node.getId());
        if (previous != null) {
          activateSingleApproval(previous);
          return;
        }
      }
      finish(false, comment);
    }

    private void completeMultipleApproval(MultipleApprovalNode node, MutableTaskRecord task, String action, String userId, String comment) {
      NodeRuntime nodeRuntime = nodeRuntime(node);
      task.actionName = action;
      task.comment = comment;
      task.state = isApprovedAction(action) ? TaskState.APPROVED : TaskState.REJECTED;
      if (isApprovedAction(action)) {
        nodeRuntime.approvedCount++;
      } else {
        nodeRuntime.rejectedCount++;
      }

      boolean completed = false;
      boolean approved = false;

      if (isSequential(node)) {
        if (!isApprovedAction(action)) {
          completed = true;
          approved = false;
        } else if (nodeRuntime.approvedCount >= approvalThreshold(nodeRuntime.plannedAssignees.size(), node.getPercentage())) {
          completed = true;
          approved = true;
        }
      } else {
        int threshold = approvalThreshold(nodeRuntime.plannedAssignees.size(), node.getPercentage());
        int remaining = nodeRuntime.plannedAssignees.size() - nodeRuntime.approvedCount - nodeRuntime.rejectedCount;
        if (nodeRuntime.approvedCount >= threshold) {
          completed = true;
          approved = true;
        } else if (nodeRuntime.approvedCount + remaining < threshold) {
          completed = true;
          approved = false;
        }
      }

      if (completed) {
        nodeRuntime.state = approved ? TaskState.APPROVED : TaskState.REJECTED;
        cancelRemainingTasks(nodeRuntime, approved ? TaskState.CANCELLED : TaskState.CANCELLED);
        taskEvents.add(new TaskEventView(
            runtimeInstance.getInstanceId(),
            runtimeInstance.getFlowKey(),
            task.taskId,
            node.getId(),
            approved ? TaskState.APPROVED : TaskState.REJECTED,
            userId,
            task.candidateUsers,
            action,
            comment,
            nextSequence()
        ));
        if (approved) {
          activateNextNodeOrCompleteBranch(node);
        } else {
          rejectMultipleApproval(node, comment);
        }
        return;
      }

      nodeRuntime.state = TaskState.PROCESSING;
      taskEvents.add(new TaskEventView(
          runtimeInstance.getInstanceId(),
          runtimeInstance.getFlowKey(),
          task.taskId,
          node.getId(),
          TaskState.PROCESSING,
          userId,
          task.candidateUsers,
          action,
          comment,
          nextSequence()
      ));
      if (isSequential(node)) {
        activateNextSequentialTask(node, nodeRuntime);
      }
    }

    private void cancelRemainingTasks(NodeRuntime nodeRuntime, TaskState cancelledState) {
      ArrayList<String> activeTaskIds = new ArrayList<String>(activeTasks.keySet());
      for (String taskId : activeTaskIds) {
        MutableTaskRecord task = activeTasks.get(taskId);
        if (task != null && nodeRuntime.nodeId.equals(task.nodeId)) {
          task.state = cancelledState;
          activeTasks.remove(taskId);
        }
      }
    }

    private ApprovalNode previousApprovalNode(String nodeId) {
      for (FlowEdgeV2 edge : definition.getIncomingEdges(nodeId)) {
        FlowNode source = definition.getNode(edge.getSourceId());
        if (source instanceof ApprovalNode) {
          return (ApprovalNode) source;
        }
        if (source != null) {
          ApprovalNode previous = previousApprovalNode(source.getId());
          if (previous != null) {
            return previous;
          }
        }
      }
      return null;
    }

    private List<String> fallbackAssignees(ApprovalNode node) {
      if (node.getReAssignUsers() != null && !node.getReAssignUsers().isEmpty()) {
        return node.getReAssignUsers();
      }
      return Collections.emptyList();
    }

    private void activateSubflow(SubflowNode node) {
      StructuredFlowDefinition childDefinition = deployments.get(node.getSubFlowKey());
      if (childDefinition == null || containsApprovalNodes(childDefinition)) {
        return;
      }
      activateNextNodeOrCompleteBranch(node);
    }

    private boolean containsApprovalNodes(StructuredFlowDefinition flowDefinition) {
      for (FlowNode flowNode : flowDefinition.getNodes()) {
        if (flowNode instanceof ApprovalNode) {
          return true;
        }
      }
      return false;
    }

    private void activateNextNodeOrCompleteBranch(FlowNode node) {
      activateFromSourceNode(node.getId());
    }

    private boolean isSequential(MultipleApprovalNode node) {
      return "串行".equalsIgnoreCase(node.getMultipleMode());
    }

    private boolean isApprovedAction(String action) {
      return "approve".equalsIgnoreCase(action);
    }

    private void rejectMultipleApproval(MultipleApprovalNode node, String comment) {
      if ("rollback".equalsIgnoreCase(node.getRejectAction())) {
        ApprovalNode previous = previousApprovalNode(node.getId());
        if (previous != null) {
          activateSingleApproval(previous);
          return;
        }
      }
      finish(false, comment);
    }

    private int approvalThreshold(int total, Integer percentage) {
      int value = percentage == null ? 100 : percentage.intValue();
      return (int) Math.ceil(total * value / 100.0d);
    }

    private NodeRuntime nodeRuntime(ApprovalNode node) {
      NodeRuntime nodeRuntime = nodeRuntimes.get(node.getId());
      if (nodeRuntime == null) {
        nodeRuntime = new NodeRuntime(node.getId());
        nodeRuntimes.put(node.getId(), nodeRuntime);
      }
      return nodeRuntime;
    }

    private long nextSequence() {
      return sequenceSupplier.getAsLong();
    }

    private void finish(boolean approved, String comment) {
      if (finished) {
        return;
      }
      finished = true;
      activeTasks.clear();
      instanceEvents.add(new InstanceEventView(
          runtimeInstance.getInstanceId(),
          runtimeInstance.getFlowKey(),
          approved ? "APPROVED" : "REJECTED",
          approved,
          comment,
          lastCompletedTaskId,
          nextSequence()
      ));
    }

    private void finishInstance(String state, Boolean approved) {
      if (finished) {
        return;
      }
      finished = true;
      suspended = false;
      activeTasks.clear();
      instanceEvents.add(new InstanceEventView(
          runtimeInstance.getInstanceId(),
          runtimeInstance.getFlowKey(),
          state,
          approved,
          null,
          lastCompletedTaskId,
          nextSequence()
      ));
    }

    private String taskHistoryKey(String taskId, String field) {
      return "task." + taskId + "." + field;
    }
  }

  private static final class NodeRuntime {
    private final String nodeId;
    private final List<MutableTaskRecord> tasks = new ArrayList<MutableTaskRecord>();
    private final List<String> plannedAssignees = new ArrayList<String>();
    private boolean uncreatedEventEmitted;
    private int approvedCount;
    private int rejectedCount;
    private int nextSequentialIndex;
    private TaskState state = TaskState.CREATED;

    private NodeRuntime(String nodeId) {
      this.nodeId = nodeId;
    }

    private TimelineView.NodeTimelineView toTimelineView(boolean suspended) {
      ArrayList<TaskView> taskViews = new ArrayList<TaskView>();
      for (MutableTaskRecord task : tasks) {
        taskViews.add(task.toView(suspended));
      }
      return new TimelineView.NodeTimelineView(nodeId, visibleState(state, suspended), taskViews);
    }

    private TaskState visibleState(TaskState state, boolean suspended) {
      if (!suspended) {
        return state;
      }
      return state == TaskState.CREATED || state == TaskState.PROCESSING ? TaskState.SUSPENDED : state;
    }
  }

  private static final class JoinRuntime {
    private final int totalIncoming;
    private final boolean isOrJoin;
    private final java.util.Set<String> arrivedSourceIds = new java.util.LinkedHashSet<String>();
    private final java.util.Set<String> activatedBranchStarts = new java.util.LinkedHashSet<String>();

    private JoinRuntime(int totalIncoming, boolean isOrJoin) {
      this.totalIncoming = totalIncoming;
      this.isOrJoin = isOrJoin;
    }
    
    private void registerActivatedBranches(List<String> branchStartIds) {
      activatedBranchStarts.addAll(branchStartIds);
    }

    private boolean markArrived(String sourceId) {
      arrivedSourceIds.add(sourceId);
      
      if (isOrJoin && !activatedBranchStarts.isEmpty()) {
        return arrivedSourceIds.size() >= activatedBranchStarts.size();
      } else {
        return arrivedSourceIds.size() >= Math.max(1, totalIncoming);
      }
    }
  }

  private static final class MutableTaskRecord {
    private String taskId;
    private final String nodeId;
    private final String assignee;
    private final List<String> candidateUsers;
    private TaskState state;
    private String actionName;
    private String comment;

    private MutableTaskRecord(
        String taskId,
        String nodeId,
        String assignee,
        List<String> candidateUsers,
        TaskState state,
        String actionName,
        String comment) {
      this.taskId = taskId;
      this.nodeId = nodeId;
      this.assignee = assignee;
      this.candidateUsers = candidateUsers == null ? Collections.<String>emptyList() : candidateUsers;
      this.state = state;
      this.actionName = actionName;
      this.comment = comment;
    }

    private TaskView toView() {
      return toView(false);
    }

    private TaskView toView(boolean suspended) {
      TaskState visibleState = suspended && (state == TaskState.CREATED || state == TaskState.PROCESSING)
          ? TaskState.SUSPENDED
          : state;
      return new TaskView(taskId, nodeId, assignee, candidateUsers, visibleState, actionName, comment);
    }
  }
}
