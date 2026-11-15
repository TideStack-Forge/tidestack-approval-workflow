package com.ouroboros.workflow.approval.testkit.adapter;

import java.util.List;
import java.util.Map;

import com.ouroboros.workflow.approval.definition.FlowDefinition;
import com.ouroboros.workflow.approval.definition.FlowNode;
import com.ouroboros.workflow.approval.definition.nodes.ApprovalNode;
import com.ouroboros.workflow.approval.testkit.snapshot.InstanceEventView;
import com.ouroboros.workflow.approval.testkit.snapshot.RoundTripSnapshot;
import com.ouroboros.workflow.approval.testkit.snapshot.RuntimeInstance;
import com.ouroboros.workflow.approval.testkit.snapshot.StartRequest;
import com.ouroboros.workflow.approval.testkit.snapshot.TaskEventView;
import com.ouroboros.workflow.approval.testkit.snapshot.TaskView;
import com.ouroboros.workflow.approval.testkit.snapshot.TimelineView;
import com.ouroboros.workflow.approval.testkit.snapshot.TranspiledModelSnapshot;

public interface EngineTestAdapter {
  FlowDefinition loadDefinition(String json);

  List<FlowNode> flowNodes(String json);

  List<ApprovalNode> candidateApprovalNodes(String json, Map<String, Object> conditionContext);

  TranspiledModelSnapshot transpile(String json);

  RoundTripSnapshot roundTrip(String json);

  void deploy(String flowKey, String json);

  RuntimeInstance start(String flowKey, StartRequest request);

  void completeStartTask(String instanceId);

  void doAction(String taskId, String action, String userId, String comment, Map<String, Object> formData, Object payload);

  void pauseInstance(String instanceId, String userId, String comment);

  void resumeInstance(String instanceId, String userId);

  void cancelInstance(String instanceId, String userId, String comment);

  void completeInstance(String instanceId, String userId, boolean approved, String comment);

  List<TaskView> activeTasks(String instanceId);

  List<TaskView> activeTasks(String instanceId, String nodeId);

  Map<String, Object> historyVariables(String instanceId);

  List<TaskEventView> taskEvents(String instanceId);

  List<TaskEventView> allTaskEvents();

  List<InstanceEventView> instanceEvents(String instanceId);

  TimelineView timeline(String instanceId);

  boolean isFinished(String instanceId);

  void reset();
}
