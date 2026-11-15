package com.ouroboros.workflow.approval.testkit.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ouroboros.workflow.approval.model.TaskState;
import com.ouroboros.workflow.approval.testkit.scenario.SharedSemanticScenario;
import com.ouroboros.workflow.approval.testkit.snapshot.RuntimeInstance;
import com.ouroboros.workflow.approval.testkit.snapshot.StartRequest;
import com.ouroboros.workflow.approval.testkit.snapshot.TaskEventView;
import com.ouroboros.workflow.approval.testkit.snapshot.TaskView;

public abstract class AbstractTaskEventContractTest extends AbstractEngineContractTest {
  @Test
  void parallelMultipleApprovalEmitsStableEventCountsWithoutDuplicates() {
    assumeTrue(capabilities().supportsTaskEvents());

    RuntimeInstance instance = deployAndStart(SharedSemanticScenario.SIMPLE_MULTIPLE_PARALLEL);
    List<TaskView> tasks = adapter().activeTasks(instance.getInstanceId(), "multiapproval");

    adapter().doAction(tasks.get(0).getTaskId(), "approve", tasks.get(0).getAssignee(), null,
        java.util.Collections.<String, Object>emptyMap(), null);
    adapter().doAction(tasks.get(1).getTaskId(), "approve", tasks.get(1).getAssignee(), null,
        java.util.Collections.<String, Object>emptyMap(), null);

    List<TaskEventView> events = adapter().taskEvents(instance.getInstanceId());

    assertEquals(1L, count(events, TaskState.UNCREATED));
    assertEquals(3L, count(events, TaskState.CREATED));
    assertEquals(1L, count(events, TaskState.PROCESSING));
    assertEquals(1L, count(events, TaskState.APPROVED));
    assertEquals("approve", lastEvent(events, "multiapproval").getAction());
  }

  @Test
  void sequentialMultipleApprovalEmitsProcessingThenRejectedEvent() {
    assumeTrue(capabilities().supportsTaskEvents());

    RuntimeInstance instance = deployAndStart(SharedSemanticScenario.SIMPLE_MULTIPLE_SEQUENTIAL);
    TaskView firstTask = onlyTask(instance.getInstanceId(), "multiapproval");

    adapter().doAction(firstTask.getTaskId(), "approve", firstTask.getAssignee(), null,
        java.util.Collections.<String, Object>emptyMap(), null);

    TaskView secondTask = onlyTask(instance.getInstanceId(), "multiapproval");
    adapter().doAction(secondTask.getTaskId(), "reject", secondTask.getAssignee(), "rejected",
        java.util.Collections.<String, Object>emptyMap(), null);

    List<TaskEventView> events = adapter().taskEvents(instance.getInstanceId());

    assertEquals(1L, count(events, TaskState.UNCREATED));
    assertEquals(2L, count(events, TaskState.CREATED));
    assertEquals(1L, count(events, TaskState.PROCESSING));
    assertEquals(1L, count(events, TaskState.REJECTED));
    assertEquals("user-b", lastEvent(events, "multiapproval").getAssignee());
    assertEquals("reject", lastEvent(events, "multiapproval").getAction());
  }

  @Test
  void parallelMultipleApprovalNodesKeepTaskEventsIsolated() {
    assumeTrue(capabilities().supportsTaskEvents());

    RuntimeInstance instance = deployAndStart(SharedSemanticScenario.PARALLEL_DOUBLE_MULTIPLE);
    List<TaskView> tasksA = adapter().activeTasks(instance.getInstanceId(), "multi-a");
    List<TaskView> tasksB = adapter().activeTasks(instance.getInstanceId(), "multi-b");

    assertEventCandidates(adapter().taskEvents(instance.getInstanceId()), "multi-a", TaskState.CREATED, Arrays.asList("user-a", "user-b"), 2L);
    assertEventCandidates(adapter().taskEvents(instance.getInstanceId()), "multi-b", TaskState.CREATED, Arrays.asList("user-c", "user-d"), 2L);

    TaskView thresholdTaskA = tasksA.get(0);
    adapter().doAction(thresholdTaskA.getTaskId(), "approve", thresholdTaskA.getAssignee(), null,
        java.util.Collections.<String, Object>emptyMap(), null);

    List<TaskEventView> afterFirstApproval = adapter().taskEvents(instance.getInstanceId());
    assertEquals(TaskState.APPROVED, lastEvent(afterFirstApproval, "multi-a").getState());
    assertEquals(Arrays.asList("user-a", "user-b"), lastEvent(afterFirstApproval, "multi-a").getCandidateUsers());
    assertEquals(TaskState.CREATED, lastEvent(afterFirstApproval, "multi-b").getState());
    assertEquals(Arrays.asList("user-c", "user-d"), lastEvent(afterFirstApproval, "multi-b").getCandidateUsers());

  }

  @Test
  void subflowMultipleApprovalTaskEventsStayOnChildFlowWithoutDuplicates() {
    assumeTrue(capabilities().supportsTaskEvents());
    assumeTrue(capabilities().supportsSubFlowInMemory());

    adapter().deploy("child-multiple-events", childMultipleApprovalFlowJson());
    adapter().deploy("parent-multiple-events", parentWithChildMultipleApprovalFlowJson());

    RuntimeInstance parentInstance = adapter().start("parent-multiple-events", subflowMultipleApprovalRequest());
    adapter().completeStartTask(parentInstance.getInstanceId());

    List<TaskEventView> childCreatedEvents = eventsForFlow(
        adapter().allTaskEvents(),
        "child-multiple-events",
        "multiapproval",
        TaskState.CREATED
    );

    assertEquals(3L, childCreatedEvents.size());
    assertEquals(1L, distinctInstanceCount(childCreatedEvents));
    assertNotEquals(parentInstance.getInstanceId(), childCreatedEvents.get(0).getInstanceId());

    String childInstanceId = childCreatedEvents.get(0).getInstanceId();
    List<TaskView> childTasks = adapter().activeTasks(childInstanceId, "multiapproval");
    adapter().doAction(childTasks.get(0).getTaskId(), "approve", childTasks.get(0).getAssignee(), null,
        Collections.<String, Object>emptyMap(), null);
    adapter().doAction(childTasks.get(1).getTaskId(), "approve", childTasks.get(1).getAssignee(), null,
        Collections.<String, Object>emptyMap(), null);

    List<TaskEventView> childEvents = eventsForFlow(adapter().allTaskEvents(), "child-multiple-events", "multiapproval", null);
    assertEquals(1L, count(childEvents, TaskState.UNCREATED));
    assertEquals(3L, count(childEvents, TaskState.CREATED));
    assertEquals(1L, count(childEvents, TaskState.PROCESSING));
    assertEquals(1L, count(childEvents, TaskState.APPROVED));
  }

  @Test
  void allTaskEventsKeepSequenceUniqueAcrossInstances() {
    assumeTrue(capabilities().supportsTaskEvents());

    RuntimeInstance firstInstance = deployAndStart(SharedSemanticScenario.SIMPLE_SINGLE);
    RuntimeInstance secondInstance = deployAndStart(SharedSemanticScenario.SIMPLE_SINGLE);

    TaskView firstTask = onlyTask(firstInstance.getInstanceId(), "approve-a");
    TaskView secondTask = onlyTask(secondInstance.getInstanceId(), "approve-a");

    adapter().doAction(firstTask.getTaskId(), "approve", taskActor(firstTask), null,
        Collections.<String, Object>emptyMap(), null);
    adapter().doAction(secondTask.getTaskId(), "approve", taskActor(secondTask), null,
        Collections.<String, Object>emptyMap(), null);

    List<TaskEventView> events = adapter().allTaskEvents();

    assertEquals(events.size(), distinctSequenceCount(events));
  }

  private long count(List<TaskEventView> events, TaskState state) {
    long count = 0;
    for (TaskEventView event : events) {
      if (event.getState() == state) {
        count++;
      }
    }
    return count;
  }

  private void assertEventCandidates(
      List<TaskEventView> events,
      String nodeId,
      TaskState state,
      List<String> expectedCandidates,
      long expectedCount
  ) {
    long count = 0;
    for (TaskEventView event : events) {
      if (!nodeId.equals(event.getNodeId()) || event.getState() != state) {
        continue;
      }
      assertEquals(expectedCandidates, event.getCandidateUsers());
      count++;
    }
    assertEquals(expectedCount, count);
  }

  private TaskEventView lastEvent(List<TaskEventView> events, String nodeId) {
    TaskEventView result = null;
    for (TaskEventView event : events) {
      if (nodeId.equals(event.getNodeId())) {
        result = event;
      }
    }
    if (result == null) {
      throw new AssertionError("No task event found for node " + nodeId);
    }
    return result;
  }

  private List<TaskEventView> eventsForFlow(
      List<TaskEventView> events,
      String flowKey,
      String nodeId,
      TaskState state
  ) {
    java.util.ArrayList<TaskEventView> filtered = new java.util.ArrayList<TaskEventView>();
    for (TaskEventView event : events) {
      if (!flowKey.equals(event.getFlowKey()) || !nodeId.equals(event.getNodeId())) {
        continue;
      }
      if (state != null && event.getState() != state) {
        continue;
      }
      filtered.add(event);
    }
    return filtered;
  }

  private long distinctInstanceCount(List<TaskEventView> events) {
    java.util.LinkedHashSet<String> instanceIds = new java.util.LinkedHashSet<String>();
    for (TaskEventView event : events) {
      instanceIds.add(event.getInstanceId());
    }
    return instanceIds.size();
  }

  private long distinctSequenceCount(List<TaskEventView> events) {
    java.util.LinkedHashSet<Long> sequences = new java.util.LinkedHashSet<Long>();
    for (TaskEventView event : events) {
      sequences.add(event.getSequence());
    }
    return sequences.size();
  }

  private String taskActor(TaskView task) {
    if (task.getAssignee() != null) {
      return task.getAssignee();
    }
    if (!task.getCandidateUsers().isEmpty()) {
      return task.getCandidateUsers().get(0);
    }
    throw new AssertionError("No assignee or candidate user found for task " + task.getTaskId());
  }

  private StartRequest subflowMultipleApprovalRequest() {
    LinkedHashMap<String, List<String>> selectedAssignees = new LinkedHashMap<String, List<String>>();
    selectedAssignees.put("multiapproval", Arrays.asList("user-a", "user-b", "user-c"));
    return new StartRequest(
        "starter-1",
        selectedAssignees,
        Collections.<String, Object>emptyMap(),
        Collections.<String, Object>emptyMap()
    );
  }

  private String parentWithChildMultipleApprovalFlowJson() {
    return """
        {
          "formatVersion": 2,
          "id": "parent-multiple-events",
          "name": "父流程会签子流程事件验证",
          "category": "",
          "icon": "shenpi",
          "description": "",
          "formType": "default",
          "formKey": "runtime-form",
          "nodes": [
            { "type": "发起节点", "name": "发起节点", "id": "startnode", "permission": [] },
            {
              "type": "子流程节点",
              "name": "子流程调用",
              "id": "subflownode",
              "subFlowKey": "child-multiple-events",
              "rejectAction": "reject",
              "inParameters": [],
              "outParameters": []
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
            { "id": "e1", "type": "SEQUENCE", "sourceId": "startnode", "targetId": "subflownode" },
            { "id": "e2", "type": "SEQUENCE", "sourceId": "subflownode", "targetId": "end-a" }
          ]
        }
        """;
  }

  private String childMultipleApprovalFlowJson() {
    return """
        {
          "formatVersion": 2,
          "id": "child-multiple-events",
          "name": "子流程会签事件验证",
          "category": "",
          "icon": "shenpi",
          "description": "",
          "formType": "default",
          "formKey": "runtime-form",
          "nodes": [
            { "type": "发起节点", "name": "发起节点", "id": "startnode", "permission": [] },
            {
              "type": "审批节点",
              "name": "会签审批",
              "id": "multiapproval",
              "subType": "会签",
              "candidates": [],
              "permission": [],
              "multipleMode": "并行",
              "percentage": 66,
              "rejectAction": "reject"
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
            { "id": "e1", "type": "SEQUENCE", "sourceId": "startnode", "targetId": "multiapproval" },
            { "id": "e2", "type": "SEQUENCE", "sourceId": "multiapproval", "targetId": "end-a" }
          ]
        }
        """;
  }
}
