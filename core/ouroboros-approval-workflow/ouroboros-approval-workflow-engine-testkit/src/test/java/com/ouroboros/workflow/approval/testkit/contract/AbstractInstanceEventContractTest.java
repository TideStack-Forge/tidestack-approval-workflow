package com.ouroboros.workflow.approval.testkit.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ouroboros.workflow.approval.testkit.scenario.SharedSemanticScenario;
import com.ouroboros.workflow.approval.testkit.snapshot.InstanceEventView;
import com.ouroboros.workflow.approval.testkit.snapshot.RuntimeInstance;
import com.ouroboros.workflow.approval.testkit.snapshot.TaskEventView;
import com.ouroboros.workflow.approval.testkit.snapshot.TaskView;

public abstract class AbstractInstanceEventContractTest extends AbstractEngineContractTest {
  private static final String HAS_PARENT_FLOW_VARIABLE = "__hasParentFlow";
  private static final String PARENT_FLOW_ID_VARIABLE = "__parentFlowId";

  @Test
  void naturalApproveCompletionEmitsStartedThenApprovedInstanceEvents() {
    assumeTrue(capabilities().supportsInstanceEvents());

    RuntimeInstance instance = deployAndStart(SharedSemanticScenario.SIMPLE_SINGLE);
    TaskView approvalTask = onlyTask(instance.getInstanceId(), "approve-a");

    adapter().doAction(approvalTask.getTaskId(), "approve", "user-a", null,
        Collections.<String, Object>emptyMap(), null);

    List<InstanceEventView> events = adapter().instanceEvents(instance.getInstanceId());

    assertEquals(Arrays.asList("STARTED", "APPROVED"), states(events));
    assertEquals(Boolean.TRUE, events.get(1).getApproved());
    assertEquals(approvalTask.getTaskId(), events.get(1).getLastTaskId());
  }

  @Test
  void naturalRejectCompletionEmitsStartedThenRejectedInstanceEvents() {
    assumeTrue(capabilities().supportsInstanceEvents());

    RuntimeInstance instance = deployAndStart(SharedSemanticScenario.SIMPLE_SINGLE);
    TaskView approvalTask = onlyTask(instance.getInstanceId(), "approve-a");

    adapter().doAction(approvalTask.getTaskId(), "reject", "user-a", "need changes",
        Collections.<String, Object>emptyMap(), null);

    List<InstanceEventView> events = adapter().instanceEvents(instance.getInstanceId());

    assertEquals(Arrays.asList("STARTED", "REJECTED"), states(events));
    assertEquals(Boolean.FALSE, events.get(1).getApproved());
    assertEquals(approvalTask.getTaskId(), events.get(1).getLastTaskId());
  }

  @Test
  void childSubflowKeepsParentInstanceMetadata() {
    assumeTrue(capabilities().supportsChildSubflowParentMetadata());
    assumeTrue(capabilities().supportsTaskEvents());

    adapter().deploy("child-smoke-flow", json(SharedSemanticScenario.SUBFLOW_CHILD));
    adapter().deploy("flow-smoke-complex", json(SharedSemanticScenario.SUBFLOW_PARENT));

    RuntimeInstance parentInstance = adapter().start("flow-smoke-complex", startRequest(SharedSemanticScenario.SUBFLOW_PARENT));
    adapter().completeStartTask(parentInstance.getInstanceId());

    TaskView parentSingleTask = onlyTask(parentInstance.getInstanceId(), "approve-a");
    List<TaskView> parentMultipleTasks = adapter().activeTasks(parentInstance.getInstanceId(), "multi-a");

    adapter().doAction(parentSingleTask.getTaskId(), "approve", "user-a", null,
        Collections.<String, Object>emptyMap(), null);
    adapter().doAction(parentMultipleTasks.get(0).getTaskId(), "approve", parentMultipleTasks.get(0).getAssignee(), null,
        Collections.<String, Object>emptyMap(), null);
    adapter().doAction(parentMultipleTasks.get(1).getTaskId(), "approve", parentMultipleTasks.get(1).getAssignee(), null,
        Collections.<String, Object>emptyMap(), null);

    String childInstanceId = childInstanceIdForNode("child-approve", parentInstance.getInstanceId());
    Map<String, Object> childHistory = adapter().historyVariables(childInstanceId);

    assertEquals(Boolean.TRUE, childHistory.get(HAS_PARENT_FLOW_VARIABLE));
    assertEquals(parentInstance.getInstanceId(), childHistory.get(PARENT_FLOW_ID_VARIABLE));
  }

  @Test
  void parentApprovedEventUsesLastChildTaskIdWhenSubflowFinishesNaturally() {
    assumeTrue(capabilities().supportsInstanceEvents());
    assumeTrue(capabilities().supportsTaskEvents());
    assumeTrue(capabilities().supportsChildSubflowParentMetadata());

    adapter().deploy("child-smoke-flow", json(SharedSemanticScenario.SUBFLOW_CHILD));
    adapter().deploy("flow-smoke-complex", json(SharedSemanticScenario.SUBFLOW_PARENT));

    RuntimeInstance parentInstance = adapter().start("flow-smoke-complex", startRequest(SharedSemanticScenario.SUBFLOW_PARENT));
    adapter().completeStartTask(parentInstance.getInstanceId());

    TaskView parentSingleTask = onlyTask(parentInstance.getInstanceId(), "approve-a");
    List<TaskView> parentMultipleTasks = adapter().activeTasks(parentInstance.getInstanceId(), "multi-a");

    adapter().doAction(parentSingleTask.getTaskId(), "approve", "user-a", null,
        Collections.<String, Object>emptyMap(), null);
    adapter().doAction(parentMultipleTasks.get(0).getTaskId(), "approve", parentMultipleTasks.get(0).getAssignee(), null,
        Collections.<String, Object>emptyMap(), null);
    adapter().doAction(parentMultipleTasks.get(1).getTaskId(), "approve", parentMultipleTasks.get(1).getAssignee(), null,
        Collections.<String, Object>emptyMap(), null);

    String childInstanceId = childInstanceIdForNode("child-approve", parentInstance.getInstanceId());
    TaskView childTask = onlyTask(childInstanceId, "child-approve");
    adapter().doAction(childTask.getTaskId(), "approve", "user-d", "child-approved",
        Collections.<String, Object>emptyMap(), null);

    List<InstanceEventView> parentEvents = adapter().instanceEvents(parentInstance.getInstanceId());

    assertEquals(Arrays.asList("STARTED", "APPROVED"), states(parentEvents));
    assertEquals(childTask.getTaskId(), parentEvents.get(1).getLastTaskId());
    assertEquals("child-approved", parentEvents.get(1).getComment());
  }

  @Test
  void parentRejectedEventUsesLastChildTaskIdWhenSubflowRejectsNaturally() {
    assumeTrue(capabilities().supportsInstanceEvents());
    assumeTrue(capabilities().supportsTaskEvents());
    assumeTrue(capabilities().supportsChildSubflowParentMetadata());

    adapter().deploy("child-smoke-flow", json(SharedSemanticScenario.SUBFLOW_CHILD));
    adapter().deploy("flow-smoke-complex", json(SharedSemanticScenario.SUBFLOW_PARENT));

    RuntimeInstance parentInstance = adapter().start("flow-smoke-complex", startRequest(SharedSemanticScenario.SUBFLOW_PARENT));
    adapter().completeStartTask(parentInstance.getInstanceId());

    TaskView parentSingleTask = onlyTask(parentInstance.getInstanceId(), "approve-a");
    List<TaskView> parentMultipleTasks = adapter().activeTasks(parentInstance.getInstanceId(), "multi-a");

    adapter().doAction(parentSingleTask.getTaskId(), "approve", "user-a", null,
        Collections.<String, Object>emptyMap(), null);
    adapter().doAction(parentMultipleTasks.get(0).getTaskId(), "approve", parentMultipleTasks.get(0).getAssignee(), null,
        Collections.<String, Object>emptyMap(), null);
    adapter().doAction(parentMultipleTasks.get(1).getTaskId(), "approve", parentMultipleTasks.get(1).getAssignee(), null,
        Collections.<String, Object>emptyMap(), null);

    String childInstanceId = childInstanceIdForNode("child-approve", parentInstance.getInstanceId());
    TaskView childTask = onlyTask(childInstanceId, "child-approve");
    adapter().doAction(childTask.getTaskId(), "reject", "user-d", "child-rejected",
        Collections.<String, Object>emptyMap(), null);

    List<InstanceEventView> parentEvents = adapter().instanceEvents(parentInstance.getInstanceId());

    assertEquals(Arrays.asList("STARTED", "REJECTED"), states(parentEvents));
    assertEquals(childTask.getTaskId(), parentEvents.get(1).getLastTaskId());
    assertEquals("child-rejected", parentEvents.get(1).getComment());
  }

  private List<String> states(List<InstanceEventView> events) {
    java.util.ArrayList<String> states = new java.util.ArrayList<String>();
    for (InstanceEventView event : events) {
      states.add(event.getState());
    }
    return states;
  }

  private String childInstanceIdForNode(String nodeId, String parentInstanceId) {
    for (TaskEventView event : adapter().allTaskEvents()) {
      if (nodeId.equals(event.getNodeId()) && !parentInstanceId.equals(event.getInstanceId())) {
        assertNotNull(event.getInstanceId(), "Child task event should carry instance id");
        return event.getInstanceId();
      }
    }
    throw new AssertionError("No child instance task event found for node " + nodeId);
  }
}
