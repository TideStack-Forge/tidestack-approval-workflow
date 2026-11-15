package com.ouroboros.workflow.approval.testkit.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ouroboros.workflow.approval.model.TaskState;
import com.ouroboros.workflow.approval.testkit.scenario.SharedSemanticScenario;
import com.ouroboros.workflow.approval.testkit.snapshot.InstanceEventView;
import com.ouroboros.workflow.approval.testkit.snapshot.RuntimeInstance;
import com.ouroboros.workflow.approval.testkit.snapshot.StartRequest;
import com.ouroboros.workflow.approval.testkit.snapshot.TaskView;
import com.ouroboros.workflow.approval.testkit.snapshot.TimelineView;

public abstract class AbstractInstanceServiceContractTest extends AbstractEngineContractTest {
  @Test
  void parallelTimelineAggregatesCompletedAndCancelledTasksIntoSingleNode() {
    assumeTrue(capabilities().supportsInstanceServiceRuntime());

    RuntimeInstance instance = deployAndStart(SharedSemanticScenario.SIMPLE_MULTIPLE_PARALLEL);
    List<TaskView> tasks = adapter().activeTasks(instance.getInstanceId(), "multiapproval");

    adapter().doAction(tasks.get(0).getTaskId(), "approve", tasks.get(0).getAssignee(), null,
        java.util.Collections.<String, Object>emptyMap(), null);
    adapter().doAction(tasks.get(1).getTaskId(), "approve", tasks.get(1).getAssignee(), null,
        java.util.Collections.<String, Object>emptyMap(), null);

    TimelineView.NodeTimelineView node = timelineNode(adapter().timeline(instance.getInstanceId()), "multiapproval");

    assertEquals(TaskState.APPROVED, node.getState());
    assertEquals(Arrays.asList("user-a", "user-b", "user-c"), taskAssignees(node.getTaskInstances()));
    assertEquals(1L, countTaskState(node.getTaskInstances(), TaskState.CANCELLED));
  }

  @Test
  void sequentialTimelineKeepsUncreatedTailAssigneesVisible() {
    assumeTrue(capabilities().supportsInstanceServiceRuntime());

    RuntimeInstance instance = deployAndStart(SharedSemanticScenario.SIMPLE_MULTIPLE_SEQUENTIAL);
    TaskView firstTask = onlyTask(instance.getInstanceId(), "multiapproval");

    adapter().doAction(firstTask.getTaskId(), "approve", firstTask.getAssignee(), null,
        java.util.Collections.<String, Object>emptyMap(), null);

    TimelineView.NodeTimelineView node = timelineNode(adapter().timeline(instance.getInstanceId()), "multiapproval");
    Map<String, TaskState> statesByAssignee = new HashMap<String, TaskState>();
    for (TaskView task : node.getTaskInstances()) {
      statesByAssignee.put(task.getAssignee(), task.getState());
    }

    assertEquals(TaskState.PROCESSING, node.getState());
    assertEquals(TaskState.APPROVED, statesByAssignee.get("user-a"));
    assertEquals(TaskState.PROCESSING, statesByAssignee.get("user-b"));
    assertEquals(TaskState.UNCREATED, statesByAssignee.get("user-c"));
  }

  @Test
  void timelineKeepsMissingFinishedTaskCommentAsNull() {
    assumeTrue(capabilities().supportsInstanceServiceRuntime());
    assumeTrue(capabilities().supportsSingleApprovalRuntime());

    RuntimeInstance instance = deployAndStart(SharedSemanticScenario.SIMPLE_SINGLE);
    TaskView approvalTask = onlyTask(instance.getInstanceId(), "approve-a");

    adapter().doAction(approvalTask.getTaskId(), "approve", "user-a", null,
        Collections.<String, Object>emptyMap(), null);

    TimelineView.NodeTimelineView node = timelineNode(adapter().timeline(instance.getInstanceId()), "approve-a");
    assertEquals(TaskState.APPROVED, node.getState());
    assertEquals(TaskState.APPROVED, node.getTaskInstances().get(0).getState());
    assertNull(node.getTaskInstances().get(0).getComment());
  }

  @Test
  void pauseAndResumeDispatchLifecycleEventsWithoutFinishingInstance() {
    assumeTrue(capabilities().supportsInstanceServiceRuntime());
    assumeTrue(capabilities().supportsInstanceEvents());

    StartedApprovalInstance instance = startSingleApprovalAtApprovalStage();

    adapter().pauseInstance(instance.instance.getInstanceId(), "operator-1", "维护窗口");
    assertEquals(Arrays.asList("STARTED", "SUSPENDED"), instanceStates(instance.instance.getInstanceId()));
    assertEquals(1, adapter().activeTasks(instance.instance.getInstanceId(), "approve-a").size());

    adapter().resumeInstance(instance.instance.getInstanceId(), "operator-1");
    assertEquals(Arrays.asList("STARTED", "SUSPENDED", "RESUMED"), instanceStates(instance.instance.getInstanceId()));
    assertEquals(1, adapter().activeTasks(instance.instance.getInstanceId(), "approve-a").size());
    assertFalse(adapter().isFinished(instance.instance.getInstanceId()));
  }

  @Test
  void pauseMarksTimelineNodeAndTaskAsSuspended() {
    assumeTrue(capabilities().supportsInstanceServiceRuntime());
    assumeTrue(capabilities().supportsSingleApprovalRuntime());

    StartedApprovalInstance instance = startSingleApprovalAtApprovalStage();

    adapter().pauseInstance(instance.instance.getInstanceId(), "operator-1", "维护窗口");

    TimelineView.NodeTimelineView node = timelineNode(adapter().timeline(instance.instance.getInstanceId()), "approve-a");
    assertEquals(TaskState.SUSPENDED, node.getState());
    assertEquals(1, node.getTaskInstances().size());
    assertEquals(TaskState.SUSPENDED, node.getTaskInstances().get(0).getState());
  }

  @Test
  void cancelEndsInstanceAndReportsLatestFinishedTask() {
    assumeTrue(capabilities().supportsInstanceServiceRuntime());
    assumeTrue(capabilities().supportsInstanceEvents());

    StartedApprovalInstance instance = startSingleApprovalAtApprovalStage();

    adapter().cancelInstance(instance.instance.getInstanceId(), "operator-1", "主动取消");

    List<com.ouroboros.workflow.approval.testkit.snapshot.InstanceEventView> events = adapter().instanceEvents(instance.instance.getInstanceId());
    assertEquals(Arrays.asList("STARTED", "CANCELLED"), states(events));
    assertEquals(instance.startTaskId, events.get(1).getLastTaskId());
    assertNull(events.get(1).getApproved());
    assertEquals(0, adapter().activeTasks(instance.instance.getInstanceId()).size());
    assertEquals(true, adapter().isFinished(instance.instance.getInstanceId()));
  }

  @Test
  void cancelDoesNotLeakPreviousTaskCommentIntoInstanceEvent() {
    assumeTrue(capabilities().supportsInstanceServiceRuntime());
    assumeTrue(capabilities().supportsInstanceEvents());
    assumeTrue(capabilities().supportsSingleApprovalRuntime());

    StartedRollbackInstance instance = startRollbackedSingleApproval();

    adapter().cancelInstance(instance.instance.getInstanceId(), "operator-1", "主动取消");

    List<InstanceEventView> events = adapter().instanceEvents(instance.instance.getInstanceId());
    assertEquals(Arrays.asList("STARTED", "CANCELLED"), states(events));
    assertEquals(instance.lastFinishedTaskId, events.get(1).getLastTaskId());
    assertNull(events.get(1).getComment());
  }

  @Test
  void completeApprovedEndsInstanceAndReportsLatestFinishedTask() {
    assumeTrue(capabilities().supportsInstanceServiceRuntime());
    assumeTrue(capabilities().supportsInstanceEvents());

    StartedApprovalInstance instance = startSingleApprovalAtApprovalStage();

    adapter().completeInstance(instance.instance.getInstanceId(), "operator-1", true, "人工通过");

    List<com.ouroboros.workflow.approval.testkit.snapshot.InstanceEventView> events = adapter().instanceEvents(instance.instance.getInstanceId());
    assertEquals(Arrays.asList("STARTED", "APPROVED"), states(events));
    assertEquals(Boolean.TRUE, events.get(1).getApproved());
    assertEquals(instance.startTaskId, events.get(1).getLastTaskId());
    assertEquals(0, adapter().activeTasks(instance.instance.getInstanceId()).size());
    assertEquals(true, adapter().isFinished(instance.instance.getInstanceId()));
  }

  @Test
  void completeApprovedDoesNotLeakPreviousTaskCommentIntoInstanceEvent() {
    assumeTrue(capabilities().supportsInstanceServiceRuntime());
    assumeTrue(capabilities().supportsInstanceEvents());
    assumeTrue(capabilities().supportsSingleApprovalRuntime());

    StartedRollbackInstance instance = startRollbackedSingleApproval();

    adapter().completeInstance(instance.instance.getInstanceId(), "operator-1", true, "人工通过");

    List<InstanceEventView> events = adapter().instanceEvents(instance.instance.getInstanceId());
    assertEquals(Arrays.asList("STARTED", "APPROVED"), states(events));
    assertEquals(Boolean.TRUE, events.get(1).getApproved());
    assertEquals(instance.lastFinishedTaskId, events.get(1).getLastTaskId());
    assertNull(events.get(1).getComment());
  }

  @Test
  void completeRejectedEndsInstanceWithRejectedEvent() {
    assumeTrue(capabilities().supportsInstanceServiceRuntime());
    assumeTrue(capabilities().supportsInstanceEvents());

    StartedApprovalInstance instance = startSingleApprovalAtApprovalStage();

    adapter().completeInstance(instance.instance.getInstanceId(), "operator-1", false, "人工拒绝");

    List<com.ouroboros.workflow.approval.testkit.snapshot.InstanceEventView> events = adapter().instanceEvents(instance.instance.getInstanceId());
    assertEquals(Arrays.asList("STARTED", "REJECTED"), states(events));
    assertEquals(Boolean.FALSE, events.get(1).getApproved());
    assertEquals(instance.startTaskId, events.get(1).getLastTaskId());
    assertEquals(0, adapter().activeTasks(instance.instance.getInstanceId()).size());
    assertEquals(true, adapter().isFinished(instance.instance.getInstanceId()));
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

  private StartedApprovalInstance startSingleApprovalAtApprovalStage() {
    String json = json(SharedSemanticScenario.SIMPLE_SINGLE);
    String flowKey = structuredDefinition(json).getId();
    adapter().deploy(flowKey, json);

    RuntimeInstance instance = adapter().start(flowKey, startRequest(SharedSemanticScenario.SIMPLE_SINGLE));
    TaskView startTask = adapter().activeTasks(instance.getInstanceId()).get(0);
    adapter().completeStartTask(instance.getInstanceId());
    return new StartedApprovalInstance(instance, startTask.getTaskId());
  }

  private StartedRollbackInstance startRollbackedSingleApproval() {
    String json = singleRollbackFlowJson();
    String flowKey = structuredDefinition(json).getId();
    adapter().deploy(flowKey, json);

    RuntimeInstance instance = adapter().start(flowKey, singleRollbackRequest());
    adapter().completeStartTask(instance.getInstanceId());

    TaskView firstApprovalTask = onlyTask(instance.getInstanceId(), "approvalnodea");
    adapter().doAction(firstApprovalTask.getTaskId(), "approve", "user-a", null,
        Collections.<String, Object>emptyMap(), null);

    TaskView secondApprovalTask = onlyTask(instance.getInstanceId(), "approvalnodeb");
    adapter().doAction(secondApprovalTask.getTaskId(), "rollback", "user-b", "rollback to previous",
        Collections.<String, Object>emptyMap(), null);

    return new StartedRollbackInstance(instance, secondApprovalTask.getTaskId());
  }

  private List<String> instanceStates(String instanceId) {
    return states(adapter().instanceEvents(instanceId));
  }

  private List<String> states(List<com.ouroboros.workflow.approval.testkit.snapshot.InstanceEventView> events) {
    List<String> values = new java.util.ArrayList<String>();
    for (com.ouroboros.workflow.approval.testkit.snapshot.InstanceEventView event : events) {
      values.add(event.getState());
    }
    return values;
  }

  private static final class StartedApprovalInstance {
    private final RuntimeInstance instance;
    private final String startTaskId;

    private StartedApprovalInstance(RuntimeInstance instance, String startTaskId) {
      this.instance = instance;
      this.startTaskId = startTaskId;
    }
  }

  private static final class StartedRollbackInstance {
    private final RuntimeInstance instance;
    private final String lastFinishedTaskId;

    private StartedRollbackInstance(RuntimeInstance instance, String lastFinishedTaskId) {
      this.instance = instance;
      this.lastFinishedTaskId = lastFinishedTaskId;
    }
  }

  private StartRequest singleRollbackRequest() {
    LinkedHashMap<String, List<String>> selectedAssignees = new LinkedHashMap<String, List<String>>();
    selectedAssignees.put("approvalnodea", Collections.singletonList("user-a"));
    selectedAssignees.put("approvalnodeb", Collections.singletonList("user-b"));
    return new StartRequest(
        "starter-1",
        selectedAssignees,
        Collections.<String, Object>emptyMap(),
        Collections.<String, Object>emptyMap()
    );
  }

  private String singleRollbackFlowJson() {
    return """
        {
          "formatVersion": 2,
          "id": "flowsinglerollback-instance-service-shared",
          "name": "单签回退实例服务场景",
          "category": "",
          "icon": "shenpi",
          "description": "",
          "formType": "default",
          "formKey": "runtime-form",
          "nodes": [
            { "type": "发起节点", "name": "发起节点", "id": "startnode", "permission": [] },
            {
              "type": "审批节点",
              "name": "一级审批",
              "id": "approvalnodea",
              "subType": "单签",
              "candidates": [],
              "permission": [],
              "actions": ["reject", "rollback"]
            },
            {
              "type": "审批节点",
              "name": "二级审批",
              "id": "approvalnodeb",
              "subType": "单签",
              "candidates": [],
              "permission": [],
              "actions": ["reject", "rollback"]
            },
            {
              "type": "结束节点",
              "name": "结束",
              "id": "end-a",
              "end": {
                "content": "done",
                "channel": ["message"],
                "candidates": []
              }
            }
          ],
          "edges": [
            { "id": "e1", "type": "SEQUENCE", "sourceId": "startnode", "targetId": "approvalnodea" },
            { "id": "e2", "type": "SEQUENCE", "sourceId": "approvalnodea", "targetId": "approvalnodeb" },
            { "id": "e3", "type": "SEQUENCE", "sourceId": "approvalnodeb", "targetId": "end-a" }
          ]
        }
        """;
  }
}
