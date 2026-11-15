package com.ouroboros.workflow.approval.flowable.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ouroboros.workflow.approval.flowable.testkit.FlowableEngineCapabilitySet;
import com.ouroboros.workflow.approval.flowable.testkit.FlowableEngineTestAdapter;
import com.ouroboros.workflow.approval.model.TaskState;
import com.ouroboros.workflow.approval.testkit.adapter.EngineCapability;
import com.ouroboros.workflow.approval.testkit.adapter.EngineTestAdapter;
import com.ouroboros.workflow.approval.testkit.contract.AbstractEngineContractTest;
import com.ouroboros.workflow.approval.testkit.scenario.SharedSemanticScenario;
import com.ouroboros.workflow.approval.testkit.snapshot.RuntimeInstance;
import com.ouroboros.workflow.approval.testkit.snapshot.StartRequest;
import com.ouroboros.workflow.approval.testkit.snapshot.TaskView;
import com.ouroboros.workflow.approval.testkit.snapshot.TimelineView;

class FlowableApprovalWorkflowRegressionIntegrationTest extends AbstractEngineContractTest {
  private final FlowableEngineTestAdapter adapter = new FlowableEngineTestAdapter();

  @Override
  protected EngineTestAdapter adapter() {
    return adapter;
  }

  @Override
  protected EngineCapability capabilities() {
    return FlowableEngineCapabilitySet.DEFAULT;
  }

  @Test
  void advancedActionLifecycleMaintainsExpectedTaskStateAtEachStep() {
    RuntimeInstance transferInstance = deployAndStart(advancedActionFlowJson(), advancedActionStartRequest());
    TaskView transferTask = onlyTask(transferInstance.getInstanceId(), "approve-a");
    adapter().doAction(
        transferTask.getTaskId(),
        "transfer",
        "user-a",
        "transfer to backup",
        Collections.<String, Object>emptyMap(),
        payload("targetUserId", "user-b")
    );
    TaskView transferred = onlyTask(transferInstance.getInstanceId(), "approve-a");
    assertFalse(adapter().isFinished(transferInstance.getInstanceId()));
    assertEquals("user-b", transferred.getAssignee());

    RuntimeInstance addSignBeforeInstance = deployAndStart(advancedActionFlowJson(), advancedActionStartRequest());
    TaskView beforeTask = onlyTask(addSignBeforeInstance.getInstanceId(), "approve-a");
    adapter().doAction(
        beforeTask.getTaskId(),
        "addSign",
        "user-a",
        "before current",
        Collections.<String, Object>emptyMap(),
        payload("position", "BEFORE_CURRENT", "targetUserIds", Collections.singletonList("user-c"))
    );
    TaskView insertedBefore = onlyTaskForAssignee(addSignBeforeInstance.getInstanceId(), "user-c");
    assertFalse(adapter().isFinished(addSignBeforeInstance.getInstanceId()));
    approve(insertedBefore);
    TaskView resumedBefore = onlyTaskForAssignee(addSignBeforeInstance.getInstanceId(), "user-a");
    assertEquals("user-a", resumedBefore.getAssignee());

    RuntimeInstance addSignAfterInstance = deployAndStart(advancedActionFlowJson(), advancedActionStartRequest());
    TaskView afterTask = onlyTask(addSignAfterInstance.getInstanceId(), "approve-a");
    adapter().doAction(
        afterTask.getTaskId(),
        "addSign",
        "user-a",
        "after current",
        Collections.<String, Object>emptyMap(),
        payload("position", "AFTER_CURRENT", "targetUserIds", Collections.singletonList("user-c"))
    );
    TaskView stillCurrent = onlyTask(addSignAfterInstance.getInstanceId(), "approve-a");
    assertEquals("user-a", stillCurrent.getAssignee());
    approve(stillCurrent);
    TaskView insertedAfter = onlyTaskForAssignee(addSignAfterInstance.getInstanceId(), "user-c");
    assertEquals("user-c", insertedAfter.getAssignee());

    RuntimeInstance freeRejectInstance = deployAndStart(advancedActionFlowJson(), advancedActionStartRequest());
    approve(onlyTask(freeRejectInstance.getInstanceId(), "approve-a"));
    TaskView freeRejectTask = onlyTask(freeRejectInstance.getInstanceId(), "approve-b");
    adapter().doAction(
        freeRejectTask.getTaskId(),
        "freeReject",
        "user-b",
        "back to first approver",
        Collections.<String, Object>emptyMap(),
        payload("targetNodeId", "approve-a")
    );
    TaskView upstreamTask = onlyTask(freeRejectInstance.getInstanceId(), "approve-a");
    assertFalse(adapter().isFinished(freeRejectInstance.getInstanceId()));
    assertEquals("user-a", upstreamTask.getAssignee());

    RuntimeInstance freeApproveInstance = deployAndStart(advancedActionFlowJson(), advancedActionStartRequest());
    TaskView freeApproveTask = onlyTask(freeApproveInstance.getInstanceId(), "approve-a");
    adapter().doAction(
        freeApproveTask.getTaskId(),
        "freeApprove",
        "user-a",
        "consult user-c",
        Collections.<String, Object>emptyMap(),
        payload("consultUserIds", Collections.singletonList("user-c"))
    );
    TaskView consultTask = onlyTaskForAssignee(freeApproveInstance.getInstanceId(), "user-c");
    assertFalse(adapter().isFinished(freeApproveInstance.getInstanceId()));
    approve(consultTask);
    TaskView resumedAfterConsult = onlyTaskForAssignee(freeApproveInstance.getInstanceId(), "user-a");
    assertEquals("user-a", resumedAfterConsult.getAssignee());
  }

  @Test
  void unconfiguredAdvancedActionIsRejectedByDeployedNodeContract() {
    RuntimeInstance instance = deployAndStart(advancedActionFlowJson(), advancedActionStartRequest());
    approve(onlyTask(instance.getInstanceId(), "approve-a"));
    TaskView secondTask = onlyTask(instance.getInstanceId(), "approve-b");

    IllegalStateException failure = assertThrows(
        IllegalStateException.class,
        () -> adapter().doAction(
            secondTask.getTaskId(),
            "transfer",
            "user-b",
            "transfer is not configured on approve-b",
            Collections.<String, Object>emptyMap(),
            payload("targetUserId", "user-c")
        ));

    assertTrue(failure.getCause().getMessage().contains("当前节点未配置流程动作: transfer"));
    TaskView stillSecondTask = onlyTask(instance.getInstanceId(), "approve-b");
    assertEquals("user-b", stillSecondTask.getAssignee());
  }

  @Test
  void handleNodeOnlyAcceptsCompleteActionInRuntimeDefinition() {
    RuntimeInstance instance = deployAndStart(SharedSemanticScenario.SIMPLE_HANDLE);
    TaskView handleTask = onlyTask(instance.getInstanceId(), "handle-a");

    IllegalStateException failure = assertThrows(
        IllegalStateException.class,
        () -> adapter().doAction(
            handleTask.getTaskId(),
            "approve",
            handleTask.getAssignee(),
            "approve should not work on handle node",
            Collections.<String, Object>emptyMap(),
            null
        ));

    assertTrue(failure.getCause().getMessage().contains("当前节点未配置流程动作: approve"));
    adapter().doAction(
        handleTask.getTaskId(),
        "complete",
        handleTask.getAssignee(),
        "done",
        Collections.<String, Object>emptyMap(),
        null
    );
    assertTrue(adapter().isFinished(instance.getInstanceId()));
  }

  @Test
  void serialApprovalScenarioVerifiesStateStepByStep() {
    RuntimeInstance instance = deployAndStart(SharedSemanticScenario.SIMPLE_MULTIPLE_SEQUENTIAL);
    TaskView first = onlyTask(instance.getInstanceId(), "multiapproval");
    assertEquals("user-a", first.getAssignee());
    assertFalse(adapter().isFinished(instance.getInstanceId()));

    approve(first);
    TaskView second = onlyTask(instance.getInstanceId(), "multiapproval");
    assertEquals("user-b", second.getAssignee());
    assertFalse(adapter().isFinished(instance.getInstanceId()));

    approve(second);
    TaskView third = onlyTask(instance.getInstanceId(), "multiapproval");
    assertEquals("user-c", third.getAssignee());

    approve(third);
    assertTrue(adapter().isFinished(instance.getInstanceId()));
  }

  @Test
  void parallelApprovalScenarioVerifiesThresholdAndCancellationState() {
    RuntimeInstance instance = deployAndStart(SharedSemanticScenario.SIMPLE_MULTIPLE_PARALLEL);
    List<TaskView> tasks = adapter().activeTasks(instance.getInstanceId(), "multiapproval");
    assertEquals(Arrays.asList("user-a", "user-b", "user-c"), taskAssignees(tasks));

    approve(tasks.get(0));
    assertFalse(adapter().isFinished(instance.getInstanceId()));

    approve(tasks.get(1));
    assertTrue(adapter().isFinished(instance.getInstanceId()));

    TimelineView.NodeTimelineView nodeTimeline = timelineNode(adapter().timeline(instance.getInstanceId()), "multiapproval");
    assertEquals(TaskState.APPROVED, nodeTimeline.getState());
    assertEquals(1L, countTaskState(nodeTimeline.getTaskInstances(), TaskState.CANCELLED));
  }

  @Test
  void mixedApprovalScenarioVerifiesSerialThenParallelStateTransitions() {
    RuntimeInstance instance = deployAndStart(mixedApprovalFlowJson(), mixedApprovalStartRequest());

    TaskView serialFirst = onlyTask(instance.getInstanceId(), "multi-serial");
    assertEquals("user-a", serialFirst.getAssignee());
    approve(serialFirst);

    TaskView serialSecond = onlyTask(instance.getInstanceId(), "multi-serial");
    assertEquals("user-b", serialSecond.getAssignee());
    approve(serialSecond);

    List<TaskView> parallelTasks = adapter().activeTasks(instance.getInstanceId(), "multi-parallel");
    assertEquals(Arrays.asList("user-c", "user-d"), taskAssignees(parallelTasks));
    approve(parallelTasks.get(0));

    assertTrue(adapter().isFinished(instance.getInstanceId()));
    TimelineView.NodeTimelineView parallelNode = timelineNode(adapter().timeline(instance.getInstanceId()), "multi-parallel");
    assertEquals(TaskState.APPROVED, parallelNode.getState());
    assertEquals(1L, countTaskState(parallelNode.getTaskInstances(), TaskState.CANCELLED));
  }

  private TaskView onlyTaskForAssignee(String instanceId, String assignee) {
    List<TaskView> tasks = adapter().activeTasks(instanceId);
    for (TaskView task : tasks) {
      if (assignee.equals(task.getAssignee())) {
        return task;
      }
    }
    throw new AssertionError("No active task found for assignee " + assignee);
  }

  private void approve(TaskView task) {
    adapter().doAction(
        task.getTaskId(),
        "approve",
        task.getAssignee(),
        "approved",
        Collections.<String, Object>emptyMap(),
        null
    );
  }

  private long countTaskState(List<TaskView> tasks, TaskState state) {
    long count = 0;
    for (TaskView task : tasks) {
      if (task.getState() == state) {
        count++;
      }
    }
    return count;
  }

  private Map<String, Object> payload(Object... keyValues) {
    LinkedHashMap<String, Object> payload = new LinkedHashMap<String, Object>();
    for (int i = 0; i < keyValues.length; i += 2) {
      payload.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
    }
    return payload;
  }

  private StartRequest advancedActionStartRequest() {
    LinkedHashMap<String, List<String>> selectedAssignees = new LinkedHashMap<String, List<String>>();
    selectedAssignees.put("approve-a", Collections.singletonList("user-a"));
    selectedAssignees.put("approve-b", Collections.singletonList("user-b"));
    return new StartRequest(
        "starter-1",
        selectedAssignees,
        Collections.<String, Object>emptyMap(),
        Collections.<String, Object>emptyMap()
    );
  }

  private StartRequest mixedApprovalStartRequest() {
    LinkedHashMap<String, List<String>> selectedAssignees = new LinkedHashMap<String, List<String>>();
    selectedAssignees.put("multi-serial", Arrays.asList("user-a", "user-b"));
    selectedAssignees.put("multi-parallel", Arrays.asList("user-c", "user-d"));
    return new StartRequest(
        "starter-1",
        selectedAssignees,
        Collections.<String, Object>emptyMap(),
        Collections.<String, Object>emptyMap()
    );
  }

  private String advancedActionFlowJson() {
    return "{"
        + "\"formatVersion\":2,"
        + "\"id\":\"flow-advanced-actions-regression\","
        + "\"name\":\"高级动作回归\","
        + "\"category\":\"\","
        + "\"icon\":\"shenpi\","
        + "\"description\":\"\","
        + "\"formType\":\"default\","
        + "\"formKey\":\"runtime-form\","
        + "\"nodes\":["
        + "{\"type\":\"发起节点\",\"name\":\"发起节点\",\"id\":\"startnode\",\"permission\":[]},"
        + "{\"type\":\"审批节点\",\"name\":\"一级审批\",\"id\":\"approve-a\",\"subType\":\"单签\",\"candidates\":[],\"permission\":[],\"actions\":[\"reject\",\"rollback\",\"addSign\",\"freeApprove\",\"transfer\"]},"
        + "{\"type\":\"审批节点\",\"name\":\"二级审批\",\"id\":\"approve-b\",\"subType\":\"单签\",\"candidates\":[],\"permission\":[],\"actions\":[\"reject\",\"rollback\",\"freeReject\"]},"
        + "{\"type\":\"结束节点\",\"name\":\"结束\",\"id\":\"end-a\"}"
        + "],"
        + "\"edges\":["
        + "{\"id\":\"e1\",\"type\":\"SEQUENCE\",\"sourceId\":\"startnode\",\"targetId\":\"approve-a\"},"
        + "{\"id\":\"e2\",\"type\":\"SEQUENCE\",\"sourceId\":\"approve-a\",\"targetId\":\"approve-b\"},"
        + "{\"id\":\"e3\",\"type\":\"SEQUENCE\",\"sourceId\":\"approve-b\",\"targetId\":\"end-a\"}"
        + "]"
        + "}";
  }

  private String mixedApprovalFlowJson() {
    return "{"
        + "\"formatVersion\":2,"
        + "\"id\":\"flow-mixed-approval-regression\","
        + "\"name\":\"混合审批回归\","
        + "\"category\":\"\","
        + "\"icon\":\"shenpi\","
        + "\"description\":\"\","
        + "\"formType\":\"default\","
        + "\"formKey\":\"runtime-form\","
        + "\"nodes\":["
        + "{\"type\":\"发起节点\",\"name\":\"发起节点\",\"id\":\"startnode\",\"permission\":[]},"
        + "{\"type\":\"审批节点\",\"name\":\"串行会签\",\"id\":\"multi-serial\",\"subType\":\"会签\",\"candidates\":[],\"permission\":[],\"multipleMode\":\"串行\",\"percentage\":100,\"rejectAction\":\"reject\"},"
        + "{\"type\":\"审批节点\",\"name\":\"并行会签\",\"id\":\"multi-parallel\",\"subType\":\"会签\",\"candidates\":[],\"permission\":[],\"multipleMode\":\"并行\",\"percentage\":50,\"rejectAction\":\"reject\"},"
        + "{\"type\":\"结束节点\",\"name\":\"结束\",\"id\":\"end-a\"}"
        + "],"
        + "\"edges\":["
        + "{\"id\":\"e1\",\"type\":\"SEQUENCE\",\"sourceId\":\"startnode\",\"targetId\":\"multi-serial\"},"
        + "{\"id\":\"e2\",\"type\":\"SEQUENCE\",\"sourceId\":\"multi-serial\",\"targetId\":\"multi-parallel\"},"
        + "{\"id\":\"e3\",\"type\":\"SEQUENCE\",\"sourceId\":\"multi-parallel\",\"targetId\":\"end-a\"}"
        + "]"
        + "}";
  }
}
