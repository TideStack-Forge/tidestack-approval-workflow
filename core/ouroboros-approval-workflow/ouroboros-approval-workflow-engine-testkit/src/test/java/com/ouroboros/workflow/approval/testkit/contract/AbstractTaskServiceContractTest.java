package com.ouroboros.workflow.approval.testkit.contract;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.Collections;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ouroboros.workflow.approval.model.TaskState;
import com.ouroboros.workflow.approval.testkit.scenario.SharedSemanticScenario;
import com.ouroboros.workflow.approval.testkit.snapshot.RuntimeInstance;
import com.ouroboros.workflow.approval.testkit.snapshot.StartRequest;
import com.ouroboros.workflow.approval.testkit.snapshot.TaskEventView;
import com.ouroboros.workflow.approval.testkit.snapshot.TaskView;

public abstract class AbstractTaskServiceContractTest extends AbstractEngineContractTest {
  @Test
  void doActionStoresFormDataPayloadAndCommentIntoNormalizedHistoryView() {
    assumeTrue(capabilities().supportsTaskServiceRuntime());
    assumeTrue(capabilities().supportsTaskEvents());

    RuntimeInstance instance = deployAndStart(SharedSemanticScenario.SIMPLE_SINGLE);
    TaskView approvalTask = onlyTask(instance.getInstanceId(), "approve-a");

    Map<String, Object> formData = new LinkedHashMap<String, Object>();
    formData.put("amount", 1200);
    formData.put("reason", "missing invoice");

    Map<String, Object> payload = new LinkedHashMap<String, Object>();
    payload.put("approved", Boolean.FALSE);
    payload.put("traceId", "trace-1");

    adapter().doAction(
        approvalTask.getTaskId(),
        "reject",
        "user-a",
        "need changes",
        formData,
        payload
    );

    Map<String, Object> history = adapter().historyVariables(instance.getInstanceId());

    assertEquals(formData, history.get(taskHistoryKey(approvalTask.getTaskId(), "formData")));
    assertEquals(payload, history.get(taskHistoryKey(approvalTask.getTaskId(), "payload")));
    assertEquals("need changes", history.get(taskHistoryKey(approvalTask.getTaskId(), "comment")));

    TaskEventView rejectedEvent = lastTaskEvent(adapter().taskEvents(instance.getInstanceId()), "approve-a");
    assertEquals(TaskState.REJECTED, rejectedEvent.getState());
    assertEquals("reject", rejectedEvent.getAction());
    assertEquals("need changes", rejectedEvent.getComment());
  }

  @Test
  void transferReassignsCurrentTaskToSpecifiedUser() {
    assumeTrue(capabilities().supportsTaskServiceRuntime());
    assumeTrue(capabilities().supportsSingleApprovalRuntime());
    assumeTrue(capabilities().supportsTaskTransfer());

    RuntimeInstance instance = deployAndStart(
        transferAndConsultFlowJson(),
        startRequest(selectedAssignees("approve-a", "user-a"))
    );
    TaskView approvalTask = onlyTask(instance.getInstanceId(), "approve-a");

    adapter().doAction(
        approvalTask.getTaskId(),
        "transfer",
        taskActor(approvalTask),
        "transfer to backup approver",
        Collections.<String, Object>emptyMap(),
        payload("targetUserId", "user-b")
    );

    TaskView transferredTask = onlyTask(instance.getInstanceId(), "approve-a");
    assertFalse(adapter().isFinished(instance.getInstanceId()));
    assertEquals("user-b", taskActor(transferredTask));
  }

  @Test
  void addSignBeforeCurrentRequiresInsertedApproverBeforeReturningToOriginalTask() {
    assumeTrue(capabilities().supportsTaskServiceRuntime());
    assumeTrue(capabilities().supportsSingleApprovalRuntime());
    assumeTrue(capabilities().supportsAddSign());

    RuntimeInstance instance = deployAndStart(
        transferAndConsultFlowJson(),
        startRequest(selectedAssignees("approve-a", "user-a"))
    );
    TaskView approvalTask = onlyTask(instance.getInstanceId(), "approve-a");

    adapter().doAction(
        approvalTask.getTaskId(),
        "addSign",
        taskActor(approvalTask),
        "insert approver before current",
        Collections.<String, Object>emptyMap(),
        payload("mode", "BEFORE_CURRENT", "targetUserIds", Collections.singletonList("user-b"))
    );

    TaskView beforeCurrentTask = onlyTaskForAssignee(instance.getInstanceId(), "user-b");
    assertFalse(adapter().isFinished(instance.getInstanceId()));
    assertEquals("user-b", taskActor(beforeCurrentTask));
    assertEquals("approve-a", onlyTask(instance.getInstanceId(), "approve-a").getNodeId());

    approveTask(beforeCurrentTask);

    TaskView originalTask = onlyTaskForAssignee(instance.getInstanceId(), "user-a", Boolean.TRUE);
    assertEquals("user-a", taskActor(originalTask));

    approveTask(originalTask);
    assertTrue(adapter().isFinished(instance.getInstanceId()));
  }

  @Test
  void addSignAfterCurrentSchedulesInsertedApproverAfterCurrentTaskCompletes() {
    assumeTrue(capabilities().supportsTaskServiceRuntime());
    assumeTrue(capabilities().supportsSingleApprovalRuntime());
    assumeTrue(capabilities().supportsAddSign());

    RuntimeInstance instance = deployAndStart(
        transferAndConsultFlowJson(),
        startRequest(selectedAssignees("approve-a", "user-a"))
    );
    TaskView approvalTask = onlyTask(instance.getInstanceId(), "approve-a");

    adapter().doAction(
        approvalTask.getTaskId(),
        "addSign",
        taskActor(approvalTask),
        "insert approver after current",
        Collections.<String, Object>emptyMap(),
        payload("mode", "AFTER_CURRENT", "targetUserIds", Collections.singletonList("user-b"))
    );

    TaskView currentTask = onlyTask(instance.getInstanceId(), "approve-a");
    assertFalse(adapter().isFinished(instance.getInstanceId()));
    assertEquals("user-a", taskActor(currentTask));

    approveTask(currentTask);

    TaskView afterCurrentTask = onlyTaskForAssignee(instance.getInstanceId(), "user-b");
    assertEquals("user-b", taskActor(afterCurrentTask));

    approveTask(afterCurrentTask);
    assertTrue(adapter().isFinished(instance.getInstanceId()));
  }

  @Test
  void addSignBeforeCurrentRequiresAllInsertedApproversToFinishInOrder() {
    assumeTrue(capabilities().supportsTaskServiceRuntime());
    assumeTrue(capabilities().supportsSingleApprovalRuntime());
    assumeTrue(capabilities().supportsAddSign());

    RuntimeInstance instance = deployAndStart(
        transferAndConsultFlowJson(),
        startRequest(selectedAssignees("approve-a", "user-a"))
    );
    TaskView approvalTask = onlyTask(instance.getInstanceId(), "approve-a");

    adapter().doAction(
        approvalTask.getTaskId(),
        "addSign",
        taskActor(approvalTask),
        "insert two approvers before current",
        Collections.<String, Object>emptyMap(),
        payload("mode", "BEFORE_CURRENT", "targetUserIds", Arrays.asList("user-b", "user-c"))
    );

    TaskView firstInsertedTask = onlyTaskForAssignee(instance.getInstanceId(), "user-b");
    assertEquals("user-b", taskActor(firstInsertedTask));
    approveTask(firstInsertedTask);

    TaskView secondInsertedTask = onlyTaskForAssignee(instance.getInstanceId(), "user-c");
    assertEquals("user-c", taskActor(secondInsertedTask));
    approveTask(secondInsertedTask);

    TaskView originalTask = onlyTaskForAssignee(instance.getInstanceId(), "user-a", Boolean.TRUE);
    assertEquals("user-a", taskActor(originalTask));
    approveTask(originalTask);

    assertTrue(adapter().isFinished(instance.getInstanceId()));
  }

  @Test
  void addSignWorksOnMultipleApprovalNodeWithoutMovingSingleChildExecution() {
    assumeTrue(capabilities().supportsTaskServiceRuntime());
    assumeTrue(capabilities().supportsMultipleApprovalRuntime());
    assumeTrue(capabilities().supportsAddSign());

    RuntimeInstance instance = deployAndStart(
        multipleApprovalAddSignFlowJson(),
        startRequest(selectedAssignees("approve-m", "user-a"))
    );
    TaskView approvalTask = onlyTask(instance.getInstanceId(), "approve-m");

    adapter().doAction(
        approvalTask.getTaskId(),
        "addSign",
        taskActor(approvalTask),
        "insert approver before multi-approval actor",
        Collections.<String, Object>emptyMap(),
        payload("mode", "BEFORE_CURRENT", "targetUserIds", Collections.singletonList("user-b"))
    );

    TaskView insertedTask = onlyTaskForAssignee(instance.getInstanceId(), "user-b");
    assertEquals("user-b", taskActor(insertedTask));
    approveTask(insertedTask);

    TaskView resumedTask = onlyTaskForAssignee(instance.getInstanceId(), "user-a", Boolean.TRUE);
    assertEquals("user-a", taskActor(resumedTask));
    approveTask(resumedTask);

    assertTrue(adapter().isFinished(instance.getInstanceId()));
  }

  @Test
  void rejectCanStayOnCurrentNodeWhenAllowRejectToSelfIsEnabled() {
    assumeTrue(capabilities().supportsTaskServiceRuntime());
    assumeTrue(capabilities().supportsSingleApprovalRuntime());
    assumeTrue(capabilities().supportsConfigurableRejectTarget());

    RuntimeInstance instance = deployAndStart(
        configurableRejectTargetFlowJson(),
        startRequest(selectedAssignees("approvalnodea", "user-a", "approvalnodeb", "user-b"))
    );

    approveTask(onlyTask(instance.getInstanceId(), "approvalnodea"));

    TaskView secondApprovalTask = onlyTask(instance.getInstanceId(), "approvalnodeb");
    adapter().doAction(
        secondApprovalTask.getTaskId(),
        "reject",
        taskActor(secondApprovalTask),
        "retry current approval node",
        Collections.<String, Object>emptyMap(),
        payload("targetNodeId", "approvalnodeb")
    );

    TaskView retriedTask = onlyTask(instance.getInstanceId(), "approvalnodeb");
    assertFalse(adapter().isFinished(instance.getInstanceId()));
    assertTrue(adapter().activeTasks(instance.getInstanceId(), "approvalnodea").isEmpty());
    assertEquals("user-b", taskActor(retriedTask));
  }

  @Test
  void freeRejectReturnsExecutionToSelectedUpstreamNode() {
    assumeTrue(capabilities().supportsTaskServiceRuntime());
    assumeTrue(capabilities().supportsSingleApprovalRuntime());
    assumeTrue(capabilities().supportsFreeRejection());

    RuntimeInstance instance = deployAndStart(
        configurableRejectTargetFlowJson(),
        startRequest(selectedAssignees("approvalnodea", "user-a", "approvalnodeb", "user-b"))
    );

    approveTask(onlyTask(instance.getInstanceId(), "approvalnodea"));

    TaskView secondApprovalTask = onlyTask(instance.getInstanceId(), "approvalnodeb");
    adapter().doAction(
        secondApprovalTask.getTaskId(),
        "freeReject",
        taskActor(secondApprovalTask),
        "reject to selected upstream node",
        Collections.<String, Object>emptyMap(),
        payload("targetNodeId", "approvalnodea")
    );

    TaskView upstreamTask = onlyTask(instance.getInstanceId(), "approvalnodea");
    assertFalse(adapter().isFinished(instance.getInstanceId()));
    assertTrue(adapter().activeTasks(instance.getInstanceId(), "approvalnodeb").isEmpty());
    assertEquals("user-a", taskActor(upstreamTask));
  }

  @Test
  void freeRecoverJumpsFromRejectedNodeToSelectedDownstreamNode() {
    assumeTrue(capabilities().supportsTaskServiceRuntime());
    assumeTrue(capabilities().supportsSingleApprovalRuntime());
    assumeTrue(capabilities().supportsFreeRejection());
    assumeTrue(capabilities().supportsFreeRecovery());

    RuntimeInstance instance = deployAndStart(
        freeRecoveryFlowJson(),
        startRequest(selectedAssignees("approvalnodea", "user-a", "approvalnodeb", "user-b", "approvalnodec", "user-c"))
    );

    approveTask(onlyTask(instance.getInstanceId(), "approvalnodea"));

    TaskView secondApprovalTask = onlyTask(instance.getInstanceId(), "approvalnodeb");
    adapter().doAction(
        secondApprovalTask.getTaskId(),
        "freeReject",
        taskActor(secondApprovalTask),
        "send back to first approval",
        Collections.<String, Object>emptyMap(),
        payload("targetNodeId", "approvalnodea")
    );

    TaskView recoveredSourceTask = onlyTask(instance.getInstanceId(), "approvalnodea");
    adapter().doAction(
        recoveredSourceTask.getTaskId(),
        "freeRecover",
        taskActor(recoveredSourceTask),
        "resume from selected downstream node",
        Collections.<String, Object>emptyMap(),
        payload("targetNodeId", "approvalnodec")
    );

    TaskView recoveredTargetTask = onlyTask(instance.getInstanceId(), "approvalnodec");
    assertFalse(adapter().isFinished(instance.getInstanceId()));
    assertTrue(adapter().activeTasks(instance.getInstanceId(), "approvalnodeb").isEmpty());
    assertEquals("user-c", taskActor(recoveredTargetTask));

    approveTask(recoveredTargetTask);
    assertTrue(adapter().isFinished(instance.getInstanceId()));
  }

  @Test
  void freeApproveCreatesTemporaryConsultLoopAndReturnsToOriginalApprover() {
    assumeTrue(capabilities().supportsTaskServiceRuntime());
    assumeTrue(capabilities().supportsSingleApprovalRuntime());
    assumeTrue(capabilities().supportsFreeApproval());

    RuntimeInstance instance = deployAndStart(
        transferAndConsultFlowJson(),
        startRequest(selectedAssignees("approve-a", "user-a"))
    );
    TaskView approvalTask = onlyTask(instance.getInstanceId(), "approve-a");

    adapter().doAction(
        approvalTask.getTaskId(),
        "freeApprove",
        taskActor(approvalTask),
        "consult additional approver",
        Collections.<String, Object>emptyMap(),
        payload("consultUserIds", Collections.singletonList("user-b"))
    );

    TaskView consultTask = onlyTask(instance.getInstanceId(), "approve-a");
    assertFalse(adapter().isFinished(instance.getInstanceId()));
    assertEquals("user-b", taskActor(consultTask));

    approveTask(consultTask);

    TaskView resumedTask = onlyTask(instance.getInstanceId(), "approve-a");
    assertEquals("user-a", taskActor(resumedTask));

    approveTask(resumedTask);
    assertTrue(adapter().isFinished(instance.getInstanceId()));
  }

  @Test
  void testFreeApprovalAllowsRepeatedConsultationLoops() {
    assumeTrue(capabilities().supportsTaskServiceRuntime());
    assumeTrue(capabilities().supportsSingleApprovalRuntime());
    assumeTrue(capabilities().supportsFreeApproval());

    RuntimeInstance instance = deployAndStart(
        transferAndConsultFlowJson(),
        startRequest(selectedAssignees("approve-a", "user-a"))
    );
    TaskView approvalTask = onlyTask(instance.getInstanceId(), "approve-a");

    adapter().doAction(
        approvalTask.getTaskId(),
        "freeApprove",
        taskActor(approvalTask),
        "first consultation",
        Collections.<String, Object>emptyMap(),
        payload("consultUserIds", Collections.singletonList("user-b"))
    );
    approveTask(onlyTask(instance.getInstanceId(), "approve-a"));

    TaskView resumedAfterFirstConsult = onlyTask(instance.getInstanceId(), "approve-a");
    assertEquals("user-a", taskActor(resumedAfterFirstConsult));

    adapter().doAction(
        resumedAfterFirstConsult.getTaskId(),
        "freeApprove",
        taskActor(resumedAfterFirstConsult),
        "second consultation",
        Collections.<String, Object>emptyMap(),
        payload("consultUserIds", Collections.singletonList("user-c"))
    );
    approveTask(onlyTask(instance.getInstanceId(), "approve-a"));

    TaskView resumedAfterSecondConsult = onlyTask(instance.getInstanceId(), "approve-a");
    assertEquals("user-a", taskActor(resumedAfterSecondConsult));
    approveTask(resumedAfterSecondConsult);
    assertTrue(adapter().isFinished(instance.getInstanceId()));
  }

  @Test
  void freeApprovalDoesNotCompleteCurrentNodeWithoutFinalExplicitApproval() {
    assumeTrue(capabilities().supportsTaskServiceRuntime());
    assumeTrue(capabilities().supportsSingleApprovalRuntime());
    assumeTrue(capabilities().supportsFreeApproval());

    RuntimeInstance instance = deployAndStart(
        transferAndConsultFlowJson(),
        startRequest(selectedAssignees("approve-a", "user-a"))
    );
    TaskView approvalTask = onlyTask(instance.getInstanceId(), "approve-a");

    adapter().doAction(
        approvalTask.getTaskId(),
        "freeApprove",
        taskActor(approvalTask),
        "consult before final approval",
        Collections.<String, Object>emptyMap(),
        payload("consultUserIds", Collections.singletonList("user-b"))
    );

    TaskView consultTask = onlyTask(instance.getInstanceId(), "approve-a");
    assertEquals("user-b", taskActor(consultTask));
    approveTask(consultTask);

    TaskView resumedTask = onlyTask(instance.getInstanceId(), "approve-a");
    assertEquals("user-a", taskActor(resumedTask));
    assertFalse(adapter().isFinished(instance.getInstanceId()));
    assertTrue(adapter().activeTasks(instance.getInstanceId(), "end-a").isEmpty());
  }

  protected String taskHistoryKey(String taskId, String field) {
    return "task." + taskId + "." + field;
  }

  private void approveTask(TaskView task) {
    adapter().doAction(
        task.getTaskId(),
        "approve",
        taskActor(task),
        null,
        Collections.<String, Object>emptyMap(),
        null
    );
  }

  private StartRequest startRequest(Map<String, List<String>> selectedAssignees) {
    return new StartRequest(
        "starter-1",
        selectedAssignees,
        Collections.<String, Object>emptyMap(),
        Collections.<String, Object>emptyMap()
    );
  }

  private Map<String, List<String>> selectedAssignees(String... nodeUserPairs) {
    if (nodeUserPairs.length % 2 != 0) {
      throw new IllegalArgumentException("nodeUserPairs must contain an even number of values");
    }

    LinkedHashMap<String, List<String>> selectedAssignees = new LinkedHashMap<String, List<String>>();
    for (int index = 0; index < nodeUserPairs.length; index += 2) {
      selectedAssignees.put(nodeUserPairs[index], Collections.singletonList(nodeUserPairs[index + 1]));
    }
    return selectedAssignees;
  }

  private Map<String, Object> payload(Object... keyValues) {
    if (keyValues.length % 2 != 0) {
      throw new IllegalArgumentException("keyValues must contain an even number of values");
    }

    LinkedHashMap<String, Object> payload = new LinkedHashMap<String, Object>();
    for (int index = 0; index < keyValues.length; index += 2) {
      payload.put(String.valueOf(keyValues[index]), keyValues[index + 1]);
    }
    return payload;
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

  private TaskView onlyTaskForAssignee(String instanceId, String assignee) {
    return onlyTaskForAssignee(instanceId, assignee, null);
  }

  private TaskView onlyTaskForAssignee(String instanceId, String assignee, Boolean nodeIdIsNull) {
    List<TaskView> matched = adapter().activeTasks(instanceId).stream()
        .filter(task -> assignee.equals(task.getAssignee()))
        .filter(task -> nodeIdIsNull == null
            || (Boolean.TRUE.equals(nodeIdIsNull) ? task.getNodeId() == null : task.getNodeId() != null))
        .collect(java.util.stream.Collectors.toList());
    if (matched.size() != 1) {
      throw new AssertionError("Expected exactly one active task for assignee " + assignee + " but found " + matched.size());
    }
    return matched.get(0);
  }

  private String transferAndConsultFlowJson() {
    return """
        {
          "formatVersion": 2,
          "id": "flow-task-service-enhancements-single",
          "name": "任务服务增强单签场景",
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
              "id": "approve-a",
              "subType": "单签",
              "candidates": [],
              "permission": [],
              "actions": ["reject", "transfer", "addSign", "freeApprove"]
            },
            {
              "type": "结束节点",
              "name": "结束",
              "id": "end-a",
              "end": {
                "content": "done",
                "channel": "internal"
              }
            }
          ],
          "edges": [
            { "id": "e1", "type": "SEQUENCE", "sourceId": "startnode", "targetId": "approve-a" },
            { "id": "e2", "type": "SEQUENCE", "sourceId": "approve-a", "targetId": "end-a" }
          ]
        }
        """;
  }

  private String configurableRejectTargetFlowJson() {
    return """
        {
          "formatVersion": 2,
          "id": "flow-configurable-reject-target",
          "name": "可配置拒绝目标场景",
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
              "actions": ["reject"]
            },
            {
              "type": "审批节点",
              "name": "二级审批",
              "id": "approvalnodeb",
              "subType": "单签",
              "candidates": [],
              "permission": [],
              "actions": ["reject", "freeReject"],
              "allowRejectToSelf": true
            },
            {
              "type": "结束节点",
              "name": "结束",
              "id": "end-a",
              "end": {
                "content": "done",
                "channel": "internal"
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

  private String multipleApprovalAddSignFlowJson() {
    return """
        {
          "formatVersion": 2,
          "id": "flow-task-service-add-sign-multiple",
          "name": "任务服务加签会签场景",
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
              "id": "approve-m",
              "subType": "会签",
              "candidates": [],
              "permission": [],
              "actions": ["reject", "addSign"],
              "multipleMode": "串行",
              "percentage": 100,
              "rejectAction": "reject"
            },
            {
              "type": "结束节点",
              "name": "结束",
              "id": "end-a",
              "end": {
                "content": "done",
                "channel": "internal"
              }
            }
          ],
          "edges": [
            { "id": "e1", "type": "SEQUENCE", "sourceId": "startnode", "targetId": "approve-m" },
            { "id": "e2", "type": "SEQUENCE", "sourceId": "approve-m", "targetId": "end-a" }
          ]
        }
        """;
  }

  private String freeRecoveryFlowJson() {
    return """
        {
          "formatVersion": 2,
          "id": "flow-free-recovery",
          "name": "自由恢复场景",
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
              "actions": ["reject", "freeRecover"]
            },
            {
              "type": "审批节点",
              "name": "二级审批",
              "id": "approvalnodeb",
              "subType": "单签",
              "candidates": [],
              "permission": [],
              "actions": ["reject", "freeReject"]
            },
            {
              "type": "审批节点",
              "name": "三级审批",
              "id": "approvalnodec",
              "subType": "单签",
              "candidates": [],
              "permission": [],
              "actions": ["reject"]
            },
            {
              "type": "结束节点",
              "name": "结束",
              "id": "end-a",
              "end": {
                "content": "done",
                "channel": "internal"
              }
            }
          ],
          "edges": [
            { "id": "e1", "type": "SEQUENCE", "sourceId": "startnode", "targetId": "approvalnodea" },
            { "id": "e2", "type": "SEQUENCE", "sourceId": "approvalnodea", "targetId": "approvalnodeb" },
            { "id": "e3", "type": "SEQUENCE", "sourceId": "approvalnodeb", "targetId": "approvalnodec" },
            { "id": "e4", "type": "SEQUENCE", "sourceId": "approvalnodec", "targetId": "end-a" }
          ]
        }
        """;
  }

  private TaskEventView lastTaskEvent(java.util.List<TaskEventView> events, String nodeId) {
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
}
