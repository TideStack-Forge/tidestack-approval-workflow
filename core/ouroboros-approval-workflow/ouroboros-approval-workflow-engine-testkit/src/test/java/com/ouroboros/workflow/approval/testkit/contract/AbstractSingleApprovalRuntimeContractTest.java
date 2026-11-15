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
import com.ouroboros.workflow.approval.testkit.scenario.SharedSemanticScenario;
import com.ouroboros.workflow.approval.testkit.snapshot.InstanceEventView;
import com.ouroboros.workflow.approval.testkit.snapshot.RuntimeInstance;
import com.ouroboros.workflow.approval.testkit.snapshot.StartRequest;
import com.ouroboros.workflow.approval.testkit.snapshot.TaskEventView;
import com.ouroboros.workflow.approval.testkit.snapshot.TaskView;

public abstract class AbstractSingleApprovalRuntimeContractTest extends AbstractEngineContractTest {
  @Test
  void approveCompletesSimpleSingleScenarioAndEmitsApprovedEvents() {
    assumeTrue(capabilities().supportsSingleApprovalRuntime());
    assumeTrue(capabilities().supportsTaskEvents());
    assumeTrue(capabilities().supportsInstanceEvents());

    RuntimeInstance instance = deployAndStart(SharedSemanticScenario.SIMPLE_SINGLE);
    TaskView approvalTask = onlyTask(instance.getInstanceId(), "approve-a");

    adapter().doAction(
        approvalTask.getTaskId(),
        "approve",
        "user-a",
        null,
        Collections.<String, Object>emptyMap(),
        null
    );

    assertTrue(adapter().isFinished(instance.getInstanceId()));
    assertTrue(adapter().activeTasks(instance.getInstanceId()).isEmpty());
    assertEquals(
        Arrays.asList(TaskState.CREATED, TaskState.APPROVED),
        taskStates(adapter().taskEvents(instance.getInstanceId()), "approve-a")
    );
    assertEquals(
        Arrays.asList("STARTED", "APPROVED"),
        instanceStates(adapter().instanceEvents(instance.getInstanceId()))
    );
    assertEquals(Boolean.TRUE, adapter().instanceEvents(instance.getInstanceId()).get(1).getApproved());
  }

  @Test
  void rejectCompletesSimpleSingleScenarioAndEmitsRejectedEvents() {
    assumeTrue(capabilities().supportsSingleApprovalRuntime());
    assumeTrue(capabilities().supportsTaskEvents());
    assumeTrue(capabilities().supportsInstanceEvents());

    RuntimeInstance instance = deployAndStart(SharedSemanticScenario.SIMPLE_SINGLE);
    TaskView approvalTask = onlyTask(instance.getInstanceId(), "approve-a");

    adapter().doAction(
        approvalTask.getTaskId(),
        "reject",
        "user-a",
        "need changes",
        Collections.<String, Object>emptyMap(),
        null
    );

    assertTrue(adapter().isFinished(instance.getInstanceId()));
    assertEquals(
        Arrays.asList(TaskState.CREATED, TaskState.REJECTED),
        taskStates(adapter().taskEvents(instance.getInstanceId()), "approve-a")
    );
    assertEquals("need changes", lastTaskEvent(adapter().taskEvents(instance.getInstanceId()), "approve-a").getComment());
    assertEquals(
        Arrays.asList("STARTED", "REJECTED"),
        instanceStates(adapter().instanceEvents(instance.getInstanceId()))
    );
    assertEquals(Boolean.FALSE, adapter().instanceEvents(instance.getInstanceId()).get(1).getApproved());
  }

  @Test
  void rollbackReturnsExecutionToPreviousApprovalNode() {
    assumeTrue(capabilities().supportsSingleApprovalRuntime());
    assumeTrue(capabilities().supportsTaskEvents());

    RuntimeInstance instance = deployAndStart(singleRollbackFlowJson(), singleRollbackRequest());

    TaskView firstApprovalTask = onlyTask(instance.getInstanceId(), "approvalnodea");
    adapter().doAction(
        firstApprovalTask.getTaskId(),
        "approve",
        "user-a",
        null,
        Collections.<String, Object>emptyMap(),
        null
    );

    TaskView secondApprovalTask = onlyTask(instance.getInstanceId(), "approvalnodeb");
    adapter().doAction(
        secondApprovalTask.getTaskId(),
        "rollback",
        "user-b",
        "rollback to previous",
        Collections.<String, Object>emptyMap(),
        null
    );

    TaskView rollbackTask = onlyTask(instance.getInstanceId(), "approvalnodea");

    assertFalse(adapter().isFinished(instance.getInstanceId()));
    assertEquals(Arrays.asList("user-a"), rollbackTask.getCandidateUsers());
    assertEquals(
        Arrays.asList(TaskState.CREATED, TaskState.APPROVED, TaskState.CREATED),
        taskStates(adapter().taskEvents(instance.getInstanceId()), "approvalnodea")
    );
    assertEquals("rollback", lastTaskEvent(adapter().taskEvents(instance.getInstanceId()), "approvalnodeb").getAction());
    assertEquals(TaskState.REJECTED, lastTaskEvent(adapter().taskEvents(instance.getInstanceId()), "approvalnodeb").getState());
  }

  @Test
  void hyphenNodeIdsRemainAddressableThroughRuntimeActions() {
    assumeTrue(capabilities().supportsSingleApprovalRuntime());
    assumeTrue(capabilities().supportsTaskEvents());

    RuntimeInstance instance = deployAndStart(
        SharedSemanticScenario.HYPHEN_NODE_ID,
        startRequestForNode("approve-a", Collections.singletonList("user-a"))
    );
    TaskView approvalTask = onlyTask(instance.getInstanceId(), "approve-a");

    adapter().doAction(
        approvalTask.getTaskId(),
        "approve",
        "user-a",
        null,
        Collections.<String, Object>emptyMap(),
        null
    );

    assertTrue(adapter().isFinished(instance.getInstanceId()));
    assertEquals(
        Arrays.asList(TaskState.CREATED, TaskState.APPROVED),
        taskStates(adapter().taskEvents(instance.getInstanceId()), "approve-a")
    );
  }

  @Test
  void noCandidateRejectedModeFailsProcessWithoutCreatingTasks() {
    assumeTrue(capabilities().supportsSingleApprovalRuntime());

    RuntimeInstance instance = deployAndStart(
        SharedSemanticScenario.SINGLE_NO_CANDIDATE_REJECT,
        emptyStartRequest()
    );

    assertTrue(adapter().isFinished(instance.getInstanceId()));
    assertTrue(adapter().activeTasks(instance.getInstanceId()).isEmpty());
    assertEquals(
        Arrays.asList("STARTED", "REJECTED"),
        instanceStates(adapter().instanceEvents(instance.getInstanceId()))
    );
  }

  @Test
  void noCandidateApprovedModeCompletesProcessDirectly() {
    assumeTrue(capabilities().supportsSingleApprovalRuntime());

    RuntimeInstance instance = deployAndStart(
        SharedSemanticScenario.SINGLE_NO_CANDIDATE_APPROVE,
        emptyStartRequest()
    );

    assertTrue(adapter().isFinished(instance.getInstanceId()));
    assertTrue(adapter().activeTasks(instance.getInstanceId()).isEmpty());
    assertEquals(
        Arrays.asList("STARTED", "APPROVED"),
        instanceStates(adapter().instanceEvents(instance.getInstanceId()))
    );
  }

  @Test
  void noCandidateApprovedModeInsideForkWaitsForSiblingBranch() {
    assumeTrue(capabilities().supportsSingleApprovalRuntime());

    RuntimeInstance instance = deployAndStart(
        singleForkNoCandidateApprovedFlowJson(),
        singleForkNoCandidateApprovedRequest()
    );

    assertFalse(adapter().isFinished(instance.getInstanceId()));
    assertTrue(adapter().activeTasks(instance.getInstanceId(), "approvalnodea").isEmpty());

    TaskView manualTask = onlyTask(instance.getInstanceId(), "approvalnodeb");
    adapter().doAction(
        manualTask.getTaskId(),
        "approve",
        taskActor(manualTask),
        null,
        Collections.<String, Object>emptyMap(),
        null
    );

    assertTrue(adapter().isFinished(instance.getInstanceId()));
    assertEquals(Arrays.asList("STARTED", "APPROVED"), instanceStates(adapter().instanceEvents(instance.getInstanceId())));
  }

  @Test
  void noCandidateReassignModeCreatesFallbackTask() {
    assumeTrue(capabilities().supportsSingleApprovalRuntime());

    RuntimeInstance instance = deployAndStart(
        SharedSemanticScenario.SINGLE_NO_CANDIDATE_REASSIGN,
        emptyStartRequest()
    );

    assertFalse(adapter().isFinished(instance.getInstanceId()));
    assertEquals(
        Arrays.asList("fallback-a", "fallback-b"),
        onlyTask(instance.getInstanceId(), "approve-a").getCandidateUsers()
    );
  }

  private List<TaskState> taskStates(List<TaskEventView> events, String nodeId) {
    java.util.ArrayList<TaskState> states = new java.util.ArrayList<TaskState>();
    for (TaskEventView event : events) {
      if (nodeId.equals(event.getNodeId())) {
        states.add(event.getState());
      }
    }
    return states;
  }

  private List<String> instanceStates(List<InstanceEventView> events) {
    java.util.ArrayList<String> states = new java.util.ArrayList<String>();
    for (InstanceEventView event : events) {
      states.add(event.getState());
    }
    return states;
  }

  private TaskEventView lastTaskEvent(List<TaskEventView> events, String nodeId) {
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

  private StartRequest emptyStartRequest() {
    return new StartRequest(
        "starter-1",
        Collections.<String, List<String>>emptyMap(),
        Collections.<String, Object>emptyMap(),
        Collections.<String, Object>emptyMap()
    );
  }

  private StartRequest startRequestForNode(String nodeId, List<String> users) {
    LinkedHashMap<String, List<String>> selectedAssignees = new LinkedHashMap<String, List<String>>();
    selectedAssignees.put(nodeId, users);
    return new StartRequest(
        "starter-1",
        selectedAssignees,
        Collections.<String, Object>emptyMap(),
        Collections.<String, Object>emptyMap()
    );
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

  private StartRequest singleForkNoCandidateApprovedRequest() {
    return startRequestForNode("approvalnodeb", Collections.singletonList("user-b"));
  }

  private String singleRollbackFlowJson() {
    return """
        {
          "formatVersion": 2,
          "id": "flowsinglerollbackshared",
          "name": "单签回退共享场景",
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

  private String singleForkNoCandidateApprovedFlowJson() {
    return """
        {
          "formatVersion": 2,
          "id": "flowsingleforknocandidateapprovedshared",
          "name": "单签无候选人并行通过共享场景",
          "category": "",
          "icon": "shenpi",
          "description": "",
          "formType": "default",
          "formKey": "runtime-form",
          "nodes": [
            { "type": "发起节点", "name": "发起节点", "id": "startnode", "permission": [] },
            {
              "type": "审批节点",
              "name": "自动通过审批",
              "id": "approvalnodea",
              "subType": "单签",
              "candidates": [],
              "permission": [],
              "actions": ["reject"],
              "noCandidateMode": "approved"
            },
            {
              "type": "审批节点",
              "name": "人工审批",
              "id": "approvalnodeb",
              "subType": "单签",
              "candidates": [],
              "permission": [],
              "actions": ["reject"]
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
            { "id": "e1", "type": "SEQUENCE", "sourceId": "startnode", "targetId": "approvalnodea", "name": "自动通过分支", "order": 1 },
            { "id": "e2", "type": "SEQUENCE", "sourceId": "startnode", "targetId": "approvalnodeb", "name": "人工审批分支", "order": 2 },
            { "id": "e3", "type": "SEQUENCE", "sourceId": "approvalnodea", "targetId": "join-a" },
            { "id": "e4", "type": "SEQUENCE", "sourceId": "approvalnodeb", "targetId": "join-a" },
            { "id": "e5", "type": "SEQUENCE", "sourceId": "join-a", "targetId": "end-a" }
          ]
        }
        """;
  }
}
