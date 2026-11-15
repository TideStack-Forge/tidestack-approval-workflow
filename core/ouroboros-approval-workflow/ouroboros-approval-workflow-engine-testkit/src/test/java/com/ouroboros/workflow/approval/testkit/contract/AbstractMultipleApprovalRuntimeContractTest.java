package com.ouroboros.workflow.approval.testkit.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ouroboros.workflow.approval.model.TaskState;
import com.ouroboros.workflow.approval.testkit.snapshot.InstanceEventView;
import com.ouroboros.workflow.approval.testkit.scenario.SharedSemanticScenario;
import com.ouroboros.workflow.approval.testkit.snapshot.RuntimeInstance;
import com.ouroboros.workflow.approval.testkit.snapshot.StartRequest;
import com.ouroboros.workflow.approval.testkit.snapshot.TaskView;
import com.ouroboros.workflow.approval.testkit.snapshot.TimelineView;

public abstract class AbstractMultipleApprovalRuntimeContractTest extends AbstractEngineContractTest {
  @Test
  void parallelMultipleApprovalCompletesAfterThresholdIsReached() {
    assumeTrue(capabilities().supportsMultipleApprovalRuntime());
    assumeTrue(capabilities().supportsInstanceServiceRuntime());

    RuntimeInstance instance = deployAndStart(SharedSemanticScenario.SIMPLE_MULTIPLE_PARALLEL);
    List<TaskView> tasks = adapter().activeTasks(instance.getInstanceId(), "multiapproval");

    assertEquals(Arrays.asList("user-a", "user-b", "user-c"), taskAssignees(tasks));

    adapter().doAction(tasks.get(0).getTaskId(), "approve", tasks.get(0).getAssignee(), null,
        Collections.<String, Object>emptyMap(), null);
    adapter().doAction(tasks.get(1).getTaskId(), "approve", tasks.get(1).getAssignee(), null,
        Collections.<String, Object>emptyMap(), null);

    assertTrue(adapter().isFinished(instance.getInstanceId()));
    TimelineView.NodeTimelineView node = timelineNode(adapter().timeline(instance.getInstanceId()), "multiapproval");
    assertEquals(TaskState.APPROVED, node.getState());
    assertEquals(1L, countTaskState(node.getTaskInstances(), TaskState.CANCELLED));
  }

  @Test
  void parallelMultipleApprovalApprovedInstanceEventUsesThresholdTaskId() {
    assumeTrue(capabilities().supportsMultipleApprovalRuntime());
    assumeTrue(capabilities().supportsInstanceEvents());

    RuntimeInstance instance = deployAndStart(SharedSemanticScenario.SIMPLE_MULTIPLE_PARALLEL);
    List<TaskView> tasks = adapter().activeTasks(instance.getInstanceId(), "multiapproval");

    adapter().doAction(tasks.get(0).getTaskId(), "approve", tasks.get(0).getAssignee(), null,
        Collections.<String, Object>emptyMap(), null);
    adapter().doAction(tasks.get(1).getTaskId(), "approve", tasks.get(1).getAssignee(), null,
        Collections.<String, Object>emptyMap(), null);

    List<InstanceEventView> events = adapter().instanceEvents(instance.getInstanceId());

    assertEquals(Arrays.asList("STARTED", "APPROVED"), instanceStates(events));
    assertEquals(tasks.get(1).getTaskId(), events.get(1).getLastTaskId());
  }

  @Test
  void sequentialMultipleApprovalRejectEndsProcessEarly() {
    assumeTrue(capabilities().supportsMultipleApprovalRuntime());
    assumeTrue(capabilities().supportsInstanceServiceRuntime());

    RuntimeInstance instance = deployAndStart(SharedSemanticScenario.SIMPLE_MULTIPLE_SEQUENTIAL);
    TaskView firstTask = onlyTask(instance.getInstanceId(), "multiapproval");

    adapter().doAction(firstTask.getTaskId(), "approve", firstTask.getAssignee(), null,
        Collections.<String, Object>emptyMap(), null);

    TaskView secondTask = onlyTask(instance.getInstanceId(), "multiapproval");
    assertEquals("user-b", secondTask.getAssignee());

    adapter().doAction(secondTask.getTaskId(), "reject", secondTask.getAssignee(), "rejected",
        Collections.<String, Object>emptyMap(), null);

    assertTrue(adapter().isFinished(instance.getInstanceId()));
    assertEquals(Arrays.asList("STARTED", "REJECTED"), instanceStates(instance.getInstanceId()));
  }

  @Test
  void sequentialMultipleApprovalRejectedInstanceEventUsesRejectingTaskId() {
    assumeTrue(capabilities().supportsMultipleApprovalRuntime());
    assumeTrue(capabilities().supportsInstanceEvents());

    RuntimeInstance instance = deployAndStart(SharedSemanticScenario.SIMPLE_MULTIPLE_SEQUENTIAL);
    TaskView firstTask = onlyTask(instance.getInstanceId(), "multiapproval");

    adapter().doAction(firstTask.getTaskId(), "approve", firstTask.getAssignee(), null,
        Collections.<String, Object>emptyMap(), null);

    TaskView secondTask = onlyTask(instance.getInstanceId(), "multiapproval");
    adapter().doAction(secondTask.getTaskId(), "reject", secondTask.getAssignee(), "rejected",
        Collections.<String, Object>emptyMap(), null);

    List<InstanceEventView> events = adapter().instanceEvents(instance.getInstanceId());

    assertEquals(Arrays.asList("STARTED", "REJECTED"), instanceStates(events));
    assertEquals(secondTask.getTaskId(), events.get(1).getLastTaskId());
  }

  @Test
  void parallelMultipleApprovalNodesKeepBranchStateIsolated() {
    assumeTrue(capabilities().supportsMultipleApprovalRuntime());
    assumeTrue(capabilities().supportsInstanceServiceRuntime());
    assumeTrue(capabilities().supportsInstanceEvents());

    RuntimeInstance instance = deployAndStart(SharedSemanticScenario.PARALLEL_DOUBLE_MULTIPLE);
    List<TaskView> tasksA = adapter().activeTasks(instance.getInstanceId(), "multi-a");
    List<TaskView> tasksB = adapter().activeTasks(instance.getInstanceId(), "multi-b");

    assertEquals(Arrays.asList("user-a", "user-b"), taskAssignees(tasksA));
    assertEquals(Arrays.asList("user-c", "user-d"), taskAssignees(tasksB));

    TaskView thresholdTaskA = tasksA.get(0);
    adapter().doAction(thresholdTaskA.getTaskId(), "approve", thresholdTaskA.getAssignee(), null,
        Collections.<String, Object>emptyMap(), null);

    assertFalse(adapter().isFinished(instance.getInstanceId()));
    assertTrue(adapter().activeTasks(instance.getInstanceId(), "multi-a").isEmpty());
    assertEquals(Arrays.asList("user-c", "user-d"), taskAssignees(adapter().activeTasks(instance.getInstanceId(), "multi-b")));

    TaskView thresholdTaskB = adapter().activeTasks(instance.getInstanceId(), "multi-b").get(0);
    adapter().doAction(thresholdTaskB.getTaskId(), "approve", thresholdTaskB.getAssignee(), null,
        Collections.<String, Object>emptyMap(), null);

    assertTrue(adapter().isFinished(instance.getInstanceId()));
    assertEquals(Arrays.asList("STARTED", "APPROVED"), instanceStates(instance.getInstanceId()));
    assertEquals(thresholdTaskB.getTaskId(), adapter().instanceEvents(instance.getInstanceId()).get(1).getLastTaskId());

    TimelineView timeline = adapter().timeline(instance.getInstanceId());
    assertEquals(TaskState.APPROVED, timelineNode(timeline, "multi-a").getState());
    assertEquals(TaskState.APPROVED, timelineNode(timeline, "multi-b").getState());
    assertEquals(1L, countTaskState(timelineNode(timeline, "multi-a").getTaskInstances(), TaskState.CANCELLED));
    assertEquals(1L, countTaskState(timelineNode(timeline, "multi-b").getTaskInstances(), TaskState.CANCELLED));
  }

  @Test
  void noCandidateRejectedModeFailsProcessWithoutCreatingTasks() {
    assumeTrue(capabilities().supportsMultipleApprovalRuntime());

    RuntimeInstance instance = deployAndStart(
        SharedSemanticScenario.NO_CANDIDATE_REJECT,
        emptyStartRequest()
    );

    assertTrue(adapter().isFinished(instance.getInstanceId()));
    assertTrue(adapter().activeTasks(instance.getInstanceId()).isEmpty());
    assertEquals(Arrays.asList("STARTED", "REJECTED"), instanceStates(instance.getInstanceId()));
  }

  @Test
  void noCandidateApprovedModeCompletesProcessDirectly() {
    assumeTrue(capabilities().supportsMultipleApprovalRuntime());

    RuntimeInstance instance = deployAndStart(
        SharedSemanticScenario.NO_CANDIDATE_APPROVE,
        emptyStartRequest()
    );

    assertTrue(adapter().isFinished(instance.getInstanceId()));
    assertTrue(adapter().activeTasks(instance.getInstanceId()).isEmpty());
    assertEquals(Arrays.asList("STARTED", "APPROVED"), instanceStates(instance.getInstanceId()));
  }

  @Test
  void noCandidateApprovedModeInsideForkWaitsForSiblingBranch() {
    assumeTrue(capabilities().supportsMultipleApprovalRuntime());

    RuntimeInstance instance = deployAndStart(
        multipleForkNoCandidateApprovedFlowJson(),
        multipleForkNoCandidateApprovedRequest()
    );

    assertFalse(adapter().isFinished(instance.getInstanceId()));
    assertTrue(adapter().activeTasks(instance.getInstanceId(), "multi-auto").isEmpty());
    assertEquals(Arrays.asList("user-a", "user-b"), taskAssignees(adapter().activeTasks(instance.getInstanceId(), "multi-manual")));

    TaskView thresholdTask = adapter().activeTasks(instance.getInstanceId(), "multi-manual").get(0);
    adapter().doAction(
        thresholdTask.getTaskId(),
        "approve",
        thresholdTask.getAssignee(),
        null,
        Collections.<String, Object>emptyMap(),
        null
    );

    assertTrue(adapter().isFinished(instance.getInstanceId()));
    assertEquals(Arrays.asList("STARTED", "APPROVED"), instanceStates(instance.getInstanceId()));
  }

  @Test
  void noCandidateReassignModeCreatesFallbackTasks() {
    assumeTrue(capabilities().supportsMultipleApprovalRuntime());

    RuntimeInstance instance = deployAndStart(
        SharedSemanticScenario.NO_CANDIDATE_REASSIGN,
        emptyStartRequest()
    );

    assertFalse(adapter().isFinished(instance.getInstanceId()));
    assertEquals(
        Arrays.asList("fallback-a", "fallback-b"),
        taskAssignees(adapter().activeTasks(instance.getInstanceId(), "multiapproval"))
    );
  }

  @Test
  void noCandidateRejectedModeWithRollbackReturnsExecutionToPreviousApprovalNode() {
    assumeTrue(capabilities().supportsMultipleApprovalRuntime());

    RuntimeInstance instance = deployAndStart(
        multipleNoCandidateRollbackFlowJson(),
        multipleNoCandidateRollbackRequest()
    );

    TaskView firstApprovalTask = onlyTask(instance.getInstanceId(), "approvalnodea");
    adapter().doAction(
        firstApprovalTask.getTaskId(),
        "approve",
        "user-a",
        null,
        Collections.<String, Object>emptyMap(),
        null
    );

    TaskView rollbackTask = onlyTask(instance.getInstanceId(), "approvalnodea");

    assertFalse(adapter().isFinished(instance.getInstanceId()));
    assertTrue(adapter().activeTasks(instance.getInstanceId(), "multiapproval").isEmpty());
    assertEquals(Arrays.asList("user-a"), rollbackTask.getCandidateUsers());
    assertEquals(Arrays.asList("STARTED"), instanceStates(instance.getInstanceId()));
  }

  @Test
  void rollbackRejectInMultipleApprovalReturnsExecutionToPreviousApprovalNode() {
    assumeTrue(capabilities().supportsMultipleApprovalRuntime());

    RuntimeInstance instance = deployAndStart(multipleRollbackFlowJson(), multipleRollbackRequest());

    TaskView firstApprovalTask = onlyTask(instance.getInstanceId(), "approvalnodea");
    adapter().doAction(
        firstApprovalTask.getTaskId(),
        "approve",
        "user-a",
        null,
        Collections.<String, Object>emptyMap(),
        null
    );

    List<TaskView> multipleApprovalTasks = adapter().activeTasks(instance.getInstanceId(), "multiapproval");
    assertEquals(Arrays.asList("user-b", "user-c"), taskAssignees(multipleApprovalTasks));

    TaskView rejectTask = multipleApprovalTasks.get(0);
    adapter().doAction(
        rejectTask.getTaskId(),
        "rollback",
        rejectTask.getAssignee(),
        "rollback to previous",
        Collections.<String, Object>emptyMap(),
        null
    );

    TaskView rollbackTask = onlyTask(instance.getInstanceId(), "approvalnodea");

    assertFalse(adapter().isFinished(instance.getInstanceId()));
    assertTrue(adapter().activeTasks(instance.getInstanceId(), "multiapproval").isEmpty());
    assertEquals(Arrays.asList("user-a"), rollbackTask.getCandidateUsers());
    assertEquals(Arrays.asList("STARTED"), instanceStates(instance.getInstanceId()));
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

  private List<String> instanceStates(String instanceId) {
    return instanceStates(adapter().instanceEvents(instanceId));
  }

  private List<String> instanceStates(List<InstanceEventView> events) {
    java.util.ArrayList<String> states = new java.util.ArrayList<String>();
    events.forEach(event -> states.add(event.getState()));
    return states;
  }

  private StartRequest emptyStartRequest() {
    return new StartRequest(
        "starter-1",
        Collections.<String, List<String>>emptyMap(),
        Collections.<String, Object>emptyMap(),
        Collections.<String, Object>emptyMap()
    );
  }

  private StartRequest multipleRollbackRequest() {
    LinkedHashMap<String, List<String>> selectedAssignees = new LinkedHashMap<String, List<String>>();
    selectedAssignees.put("approvalnodea", Collections.singletonList("user-a"));
    selectedAssignees.put("multiapproval", Arrays.asList("user-b", "user-c"));
    return new StartRequest(
        "starter-1",
        selectedAssignees,
        Collections.<String, Object>emptyMap(),
        Collections.<String, Object>emptyMap()
    );
  }

  private StartRequest multipleForkNoCandidateApprovedRequest() {
    LinkedHashMap<String, List<String>> selectedAssignees = new LinkedHashMap<String, List<String>>();
    selectedAssignees.put("multi-manual", Arrays.asList("user-a", "user-b"));
    return new StartRequest(
        "starter-1",
        selectedAssignees,
        Collections.<String, Object>emptyMap(),
        Collections.<String, Object>emptyMap()
    );
  }

  private StartRequest multipleNoCandidateRollbackRequest() {
    LinkedHashMap<String, List<String>> selectedAssignees = new LinkedHashMap<String, List<String>>();
    selectedAssignees.put("approvalnodea", Collections.singletonList("user-a"));
    return new StartRequest(
        "starter-1",
        selectedAssignees,
        Collections.<String, Object>emptyMap(),
        Collections.<String, Object>emptyMap()
    );
  }

  private String multipleRollbackFlowJson() {
    return """
        {
          "formatVersion": 2,
          "id": "flowmultiplerollbackshared",
          "name": "会签回退共享场景",
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
              "name": "会签审批",
              "id": "multiapproval",
              "subType": "会签",
              "candidates": [],
              "permission": [],
              "multipleMode": "并行",
              "percentage": 100,
              "rejectAction": "rollback"
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
            { "id": "e2", "type": "SEQUENCE", "sourceId": "approvalnodea", "targetId": "multiapproval" },
            { "id": "e3", "type": "SEQUENCE", "sourceId": "multiapproval", "targetId": "end-a" }
          ]
        }
        """;
  }

  private String multipleForkNoCandidateApprovedFlowJson() {
    return """
        {
          "formatVersion": 2,
          "id": "flowmultipleforknocandidateapprovedshared",
          "name": "会签无候选人并行通过共享场景",
          "category": "",
          "icon": "shenpi",
          "description": "",
          "formType": "default",
          "formKey": "runtime-form",
          "nodes": [
            { "type": "发起节点", "name": "发起节点", "id": "startnode", "permission": [] },
            {
              "type": "审批节点",
              "name": "自动通过会签",
              "id": "multi-auto",
              "subType": "会签",
              "candidates": [],
              "permission": [],
              "multipleMode": "并行",
              "percentage": 50,
              "rejectAction": "reject",
              "noCandidateMode": "approved"
            },
            {
              "type": "审批节点",
              "name": "人工会签",
              "id": "multi-manual",
              "subType": "会签",
              "candidates": [],
              "permission": [],
              "multipleMode": "并行",
              "percentage": 50,
              "rejectAction": "reject"
            },
            {
              "type": "JoinNode",
              "name": "并行汇聚",
              "id": "join-a",
              "joinType": "OR"
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
            { "id": "e1", "type": "SEQUENCE", "sourceId": "startnode", "targetId": "multi-auto", "name": "自动通过分支", "order": 1 },
            { "id": "e2", "type": "SEQUENCE", "sourceId": "startnode", "targetId": "multi-manual", "name": "人工审批分支", "order": 2 },
            { "id": "e3", "type": "SEQUENCE", "sourceId": "multi-auto", "targetId": "join-a" },
            { "id": "e4", "type": "SEQUENCE", "sourceId": "multi-manual", "targetId": "join-a" },
            { "id": "e5", "type": "SEQUENCE", "sourceId": "join-a", "targetId": "end-a" }
          ]
        }
        """;
  }

  private String multipleNoCandidateRollbackFlowJson() {
    return """
        {
          "formatVersion": 2,
          "id": "flowmultiplenocandidaterollbackshared",
          "name": "会签无候选人回退共享场景",
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
              "name": "自动回退会签",
              "id": "multiapproval",
              "subType": "会签",
              "candidates": [],
              "permission": [],
              "multipleMode": "并行",
              "percentage": 100,
              "rejectAction": "rollback",
              "noCandidateMode": "rejected"
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
            { "id": "e2", "type": "SEQUENCE", "sourceId": "approvalnodea", "targetId": "multiapproval" },
            { "id": "e3", "type": "SEQUENCE", "sourceId": "multiapproval", "targetId": "end-a" }
          ]
        }
        """;
  }
}
