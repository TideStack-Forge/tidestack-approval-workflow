package com.ouroboros.workflow.approval.flowable.transpiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.stream.Collectors;

import org.flowable.bpmn.model.BoundaryEvent;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.CallActivity;
import org.flowable.bpmn.model.ExclusiveGateway;
import org.flowable.bpmn.model.FieldExtension;
import org.flowable.bpmn.model.FlowableListener;
import org.flowable.bpmn.model.IOParameter;
import org.flowable.bpmn.model.ImplementationType;
import org.flowable.bpmn.model.InclusiveGateway;
import org.flowable.bpmn.model.Process;
import org.flowable.bpmn.model.SequenceFlow;
import org.flowable.bpmn.model.ServiceTask;
import org.flowable.bpmn.model.StartEvent;
import org.flowable.bpmn.model.TimerEventDefinition;
import org.flowable.bpmn.model.UserTask;
import org.flowable.engine.delegate.ExecutionListener;
import org.flowable.task.service.delegate.TaskListener;
import org.junit.jupiter.api.Test;

import com.ouroboros.workflow.approval.definition.codec.CompositeFlowDefinitionCodec;
import com.ouroboros.workflow.approval.flowable.util.BpmnUtil;

class FlowableWorkflowTranspilerAdvancedNodeTest {
  private final CompositeFlowDefinitionCodec codec = new CompositeFlowDefinitionCodec();

  @Test
  void transpileStructuredDefinitionUsesDelegateExpressionCallbacks() {
    Process process = transpile(multipleApprovalFlowJson()).getMainProcess();

    StartEvent startEvent = (StartEvent) process.getFlowElement(Constants.SpecialNode.START_EVENT_ID);
    assertNotNull(startEvent);
    assertNotNull(startEvent.getExecutionListeners());
    assertEquals(1, startEvent.getExecutionListeners().size());
    assertDelegateListener(
        startEvent.getExecutionListeners().get(0),
        ExecutionListener.EVENTNAME_START,
        "${flowStartExecutionListener}");

    ServiceTask initializeFlowTask = (ServiceTask) process.getFlowElement(Constants.SpecialNode.INITIALIZE_FLOW_ID);
    assertNotNull(initializeFlowTask);
    assertEquals(ImplementationType.IMPLEMENTATION_TYPE_DELEGATEEXPRESSION, initializeFlowTask.getImplementationType());
    assertEquals("${initializeFlowServiceTask}", initializeFlowTask.getImplementation());

    ServiceTask failServiceTask = (ServiceTask) process.getFlowElement(Constants.SpecialNode.FAIL_NODE_ID);
    assertNotNull(failServiceTask);
    assertEquals(ImplementationType.IMPLEMENTATION_TYPE_DELEGATEEXPRESSION, failServiceTask.getImplementationType());
    assertEquals("${failServiceTask}", failServiceTask.getImplementation());
    assertNotNull(failServiceTask.getExecutionListeners());
    assertEquals(1, failServiceTask.getExecutionListeners().size());
    assertDelegateListener(
        failServiceTask.getExecutionListeners().get(0),
        ExecutionListener.EVENTNAME_END,
        "${flowEndExecutionListener}");
  }

  @Test
  void transpileMultipleApprovalAttachesDelegateExpressionLifecycleListeners() {
    Process process = transpile(multipleApprovalFlowJson()).getMainProcess();

    UserTask approvalTask = (UserTask) process.getFlowElement("multi-a");
    assertNotNull(approvalTask);
    assertNotNull(approvalTask.getExecutionListeners());
    assertEquals(2, approvalTask.getExecutionListeners().size());
    assertNotNull(approvalTask.getTaskListeners());
    assertEquals(2, approvalTask.getTaskListeners().size());

    Map<String, FlowableListener> executionListeners = approvalTask.getExecutionListeners().stream()
        .collect(Collectors.toMap(FlowableListener::getEvent, listener -> listener));
    Map<String, FlowableListener> taskListeners = approvalTask.getTaskListeners().stream()
        .collect(Collectors.toMap(FlowableListener::getEvent, listener -> listener));

    assertDelegateListener(
        executionListeners.get(ExecutionListener.EVENTNAME_START),
        ExecutionListener.EVENTNAME_START,
        "${multipleApprovalStartExecutionListener}");
    assertDelegateListener(
        executionListeners.get(ExecutionListener.EVENTNAME_END),
        ExecutionListener.EVENTNAME_END,
        "${multipleApprovalEndExecutionListener}");
    assertDelegateListener(
        taskListeners.get(TaskListener.EVENTNAME_CREATE),
        TaskListener.EVENTNAME_CREATE,
        "${createMultipleApprovalTaskListener}");
    assertDelegateListener(
        taskListeners.get(TaskListener.EVENTNAME_COMPLETE),
        TaskListener.EVENTNAME_COMPLETE,
        "${multipleApprovalCompletedTaskListener}");
  }

  @Test
  void transpileHandleNodeBuildsUserTaskWithHandleKindAndCreateListener() {
    Process process = transpile(handleFlowJson()).getMainProcess();

    UserTask handleTask = (UserTask) process.getFlowElement("handle-a");
    assertNotNull(handleTask);
    assertEquals("办理任务", handleTask.getName());
    assertEquals("advanced-form", handleTask.getFormKey());
    assertEquals(Constants.Value.TASK_KIND_HANDLE, BpmnUtil.readTaskKind(handleTask));
    assertEquals(1, handleTask.getTaskListeners().size());
    assertDelegateListener(
        handleTask.getTaskListeners().get(0),
        TaskListener.EVENTNAME_CREATE,
        "${createSingleApprovalTaskListener}");
    assertEquals(1, BpmnUtil.readCandidates(handleTask).size());
    assertEquals(1, handleTask.getFormProperties().size());
  }

  @Test
  void transpileEndNodeBuildsOutcomeTaskBeforeSharedEndEvent() {
    Process process = transpile(rejectedEndFlowJson()).getMainProcess();

    ServiceTask outcomeTask = (ServiceTask) process.getFlowElement(
        "end-rejected" + Constants.SpecialNode.OUTCOME_ID_SUFFIX);

    assertNotNull(outcomeTask);
    assertEquals("${setProcessOutcomeServiceTask}", outcomeTask.getImplementation());
    assertEquals("rejected", readFieldExtension(outcomeTask, Constants.Property.PROCESS_OUTCOME));
    assertNotNull(findSequenceFlow(process, outcomeTask.getId(), Constants.SpecialNode.END_EVENT_ID));
  }

  @Test
  void transpileStructuredDefinitionPersistsOnlyCanonicalDefinitionPayloadByDefault() {
    Process process = transpile(multipleApprovalFlowJson()).getMainProcess();

    assertNotNull(BpmnUtil.readFlowDefinitionRawJson(process));
    assertNull(BpmnUtil.getExtensionElementTexts(process, "flowDefinition").stream().findFirst().orElse(null));
  }

  @Test
  void transpileMultipleOutgoingWithoutJoinDefaultsToExclusiveGateway() {
    Process process = transpile(xorBranchFlowJson()).getMainProcess();

    assertNotNull(process.getFlowElement("start-a__xorSplit"));
    assertEquals(ExclusiveGateway.class, process.getFlowElement("start-a__xorSplit").getClass());
    assertNull(process.getFlowElement("start-a__andSplit"));
    assertNull(process.getFlowElement("start-a__orSplit"));
  }

  @Test
  void transpileMultipleOutgoingWithExplicitOrJoinUsesInclusiveGateway() {
    Process process = transpile(orJoinBranchFlowJson()).getMainProcess();

    assertNotNull(process.getFlowElement("start-a__orSplit"));
    assertEquals(InclusiveGateway.class, process.getFlowElement("start-a__orSplit").getClass());
    assertNull(process.getFlowElement("start-a__xorSplit"));
    assertNull(process.getFlowElement("start-a__andSplit"));
  }

  @Test
  void transpileSubflowPassesParentFormContextToCalledProcess() {
    Process process = transpile(subflowJson()).getMainProcess();

    CallActivity callActivity = (CallActivity) process.getFlowElement("subflow-a");

    assertNotNull(callActivity);
    assertEquals("child-flow", callActivity.getCalledElement());
    assertInParameter(callActivity, Constants.TASK_FORM_TYPE_KEY, Constants.TASK_FORM_TYPE_KEY);
    assertInParameter(callActivity, Constants.TASK_FORM_ID_KEY, Constants.TASK_FORM_ID_KEY);
    assertInParameter(callActivity, Constants.CANDIDATE_FORM_DATA_KEY, Constants.CANDIDATE_FORM_DATA_KEY);
  }

  @Test
  void transpileApprovalTimeoutBuildsBoundaryTimerAndDelegateTask() {
    Process process = transpile(timeoutFlowJson()).getMainProcess();

    BoundaryEvent remindBoundary = (BoundaryEvent) process.getFlowElement(
        "approve-remind" + Constants.SpecialNode.TIMEOUT_BOUNDARY_ID_SUFFIX);
    ServiceTask remindTask = (ServiceTask) process.getFlowElement(
        "approve-remind" + Constants.SpecialNode.TIMEOUT_SERVICE_TASK_ID_SUFFIX);
    BoundaryEvent approveBoundary = (BoundaryEvent) process.getFlowElement(
        "approve-auto" + Constants.SpecialNode.TIMEOUT_BOUNDARY_ID_SUFFIX);
    ServiceTask approveTask = (ServiceTask) process.getFlowElement(
        "approve-auto" + Constants.SpecialNode.TIMEOUT_SERVICE_TASK_ID_SUFFIX);

    assertNotNull(remindBoundary);
    assertFalse(remindBoundary.isCancelActivity());
    assertEquals("approve-remind", remindBoundary.getAttachedToRefId());
    assertEquals("PT2H", ((TimerEventDefinition) remindBoundary.getEventDefinitions().get(0)).getTimeDuration());
    assertNotNull(remindTask);
    assertEquals("${approvalTimeoutDelegate}", remindTask.getImplementation());
    assertEquals("approve-remind", readFieldExtension(remindTask, Constants.Property.APPROVAL_TASK_ID));
    assertEquals(Constants.Value.TIMEOUT_ACTION_REMIND, readFieldExtension(remindTask, Constants.Property.TIMEOUT_ACTION));

    assertNotNull(approveBoundary);
    assertFalse(approveBoundary.isCancelActivity());
    assertEquals("approve-auto", approveBoundary.getAttachedToRefId());
    assertEquals("PT30M", ((TimerEventDefinition) approveBoundary.getEventDefinitions().get(0)).getTimeDuration());
    assertNotNull(approveTask);
    assertEquals(Constants.Value.TIMEOUT_ACTION_AUTO_APPROVE, readFieldExtension(approveTask, Constants.Property.TIMEOUT_ACTION));
  }

  @Test
  void transpileApprovalTimeoutSkipsUnsupportedAction() {
    Process process = transpile(timeoutWithoutActionFlowJson()).getMainProcess();

    assertNull(process.getFlowElement("approve-missing-action" + Constants.SpecialNode.TIMEOUT_BOUNDARY_ID_SUFFIX));
    assertNull(process.getFlowElement("approve-missing-action" + Constants.SpecialNode.TIMEOUT_SERVICE_TASK_ID_SUFFIX));
    assertNull(process.getFlowElement("approve-bad-action" + Constants.SpecialNode.TIMEOUT_BOUNDARY_ID_SUFFIX));
    assertNull(process.getFlowElement("approve-bad-action" + Constants.SpecialNode.TIMEOUT_SERVICE_TASK_ID_SUFFIX));
    assertNull(process.getFlowElement("approve-bad-duration" + Constants.SpecialNode.TIMEOUT_BOUNDARY_ID_SUFFIX));
    assertNull(process.getFlowElement("approve-bad-duration" + Constants.SpecialNode.TIMEOUT_SERVICE_TASK_ID_SUFFIX));
    assertNull(process.getFlowElement("approve-blank-transfer" + Constants.SpecialNode.TIMEOUT_BOUNDARY_ID_SUFFIX));
    assertNull(process.getFlowElement("approve-blank-transfer" + Constants.SpecialNode.TIMEOUT_SERVICE_TASK_ID_SUFFIX));
  }

  private void assertDelegateListener(FlowableListener listener, String event, String implementation) {
    assertNotNull(listener);
    assertEquals(event, listener.getEvent());
    assertEquals(ImplementationType.IMPLEMENTATION_TYPE_DELEGATEEXPRESSION, listener.getImplementationType());
    assertEquals(implementation, listener.getImplementation());
  }

  private BpmnModel transpile(String json) {
    return new FlowableWorkflowTranspiler().transpile(codec.decode(json).get());
  }

  private String readFieldExtension(ServiceTask task, String fieldName) {
    return task.getFieldExtensions()
        .stream()
        .filter(fieldExtension -> fieldName.equals(fieldExtension.getFieldName()))
        .map(FieldExtension::getStringValue)
        .findFirst()
        .orElse(null);
  }

  private void assertInParameter(CallActivity callActivity, String source, String target) {
    IOParameter parameter = callActivity.getInParameters()
        .stream()
        .filter(candidate -> source.equals(candidate.getSource()) && target.equals(candidate.getTarget()))
        .findFirst()
        .orElse(null);
    assertNotNull(parameter);
  }

  private SequenceFlow findSequenceFlow(Process process, String sourceRef, String targetRef) {
    return process.findFlowElementsOfType(SequenceFlow.class).stream()
        .filter(sequenceFlow -> sourceRef.equals(sequenceFlow.getSourceRef()) && targetRef.equals(sequenceFlow.getTargetRef()))
        .findFirst()
        .orElse(null);
  }

  private String multipleApprovalFlowJson() {
    return """
        {
          "formatVersion": 2,
          "id": "flow-multiple",
          "name": "会签监听器",
          "category": "",
          "icon": "shenpi",
          "description": "",
          "formType": "default",
          "formKey": "advanced-form",
          "nodes": [
            { "type": "发起节点", "name": "发起节点", "id": "start-a", "permission": [] },
            { "type": "审批节点", "name": "多人会签", "id": "multi-a", "subType": "会签", "candidates": [], "permission": [], "multipleMode": "并行", "percentage": 75, "rejectAction": "reject" },
            { "type": "结束节点", "name": "结束", "id": "end-a" }
          ],
          "edges": [
            { "id": "e1", "type": "SEQUENCE", "sourceId": "start-a", "targetId": "multi-a" },
            { "id": "e2", "type": "SEQUENCE", "sourceId": "multi-a", "targetId": "end-a" }
          ]
        }
        """;
  }

  private String handleFlowJson() {
    return """
        {
          "formatVersion": 2,
          "id": "flow-handle",
          "name": "办理节点流程",
          "category": "",
          "icon": "shenpi",
          "description": "",
          "formType": "default",
          "formKey": "advanced-form",
          "nodes": [
            { "type": "发起节点", "name": "发起节点", "id": "start-a", "permission": [] },
            {
              "type": "HandleNode",
              "name": "办理任务",
              "id": "handle-a",
              "candidates": [{ "type": "User", "users": ["user-a"] }],
              "permission": [{ "name": "amount", "label": "金额", "visible": true, "writable": true, "required": false }]
            },
            { "type": "结束节点", "name": "结束", "id": "end-a" }
          ],
          "edges": [
            { "id": "e1", "type": "SEQUENCE", "sourceId": "start-a", "targetId": "handle-a" },
            { "id": "e2", "type": "SEQUENCE", "sourceId": "handle-a", "targetId": "end-a" }
          ]
        }
        """;
  }

  private String rejectedEndFlowJson() {
    return """
        {
          "formatVersion": 2,
          "id": "flow-rejected-end",
          "name": "拒绝结束流程",
          "nodes": [
            { "type": "发起节点", "name": "发起节点", "id": "start-a", "permission": [] },
            { "type": "结束节点", "name": "拒绝结束", "id": "end-rejected", "outcome": "rejected" }
          ],
          "edges": [
            { "id": "e1", "type": "SEQUENCE", "sourceId": "start-a", "targetId": "end-rejected" }
          ]
        }
        """;
  }

  private String timeoutFlowJson() {
    return """
        {
          "formatVersion": 2,
          "id": "flow-timeout",
          "name": "超时策略流程",
          "nodes": [
            { "type": "发起节点", "name": "发起节点", "id": "start-a", "permission": [] },
            {
              "type": "审批节点",
              "name": "提醒审批",
              "id": "approve-remind",
              "subType": "单签",
              "candidates": [],
              "timeout": { "duration": "PT2H", "action": "remind" }
            },
            {
              "type": "审批节点",
              "name": "自动通过审批",
              "id": "approve-auto",
              "subType": "单签",
              "candidates": [],
              "timeout": { "duration": "PT30M", "action": "autoApprove" }
            },
            { "type": "结束节点", "name": "结束", "id": "end-a" }
          ],
          "edges": [
            { "id": "e1", "type": "SEQUENCE", "sourceId": "start-a", "targetId": "approve-remind" },
            { "id": "e2", "type": "SEQUENCE", "sourceId": "approve-remind", "targetId": "approve-auto" },
            { "id": "e3", "type": "SEQUENCE", "sourceId": "approve-auto", "targetId": "end-a" }
          ]
        }
        """;
  }

  private String timeoutWithoutActionFlowJson() {
    return """
        {
          "formatVersion": 2,
          "id": "flow-timeout-without-action",
          "name": "超时策略缺少动作流程",
          "nodes": [
            { "type": "发起节点", "name": "发起节点", "id": "start-a", "permission": [] },
            {
              "type": "审批节点",
              "name": "缺少动作审批",
              "id": "approve-missing-action",
              "subType": "单签",
              "candidates": [],
              "timeout": { "duration": "PT2H" }
            },
            {
              "type": "审批节点",
              "name": "非法动作审批",
              "id": "approve-bad-action",
              "subType": "单签",
              "candidates": [],
              "timeout": { "duration": "PT2H", "action": "badAction" }
            },
            {
              "type": "审批节点",
              "name": "非法时长审批",
              "id": "approve-bad-duration",
              "subType": "单签",
              "candidates": [],
              "timeout": { "duration": "two hours", "action": "autoApprove" }
            },
            {
              "type": "审批节点",
              "name": "空转办人审批",
              "id": "approve-blank-transfer",
              "subType": "单签",
              "candidates": [],
              "timeout": { "duration": "PT2H", "action": "transfer", "transferTo": "   " }
            },
            { "type": "结束节点", "name": "结束", "id": "end-a" }
          ],
          "edges": [
            { "id": "e1", "type": "SEQUENCE", "sourceId": "start-a", "targetId": "approve-missing-action" },
            { "id": "e2", "type": "SEQUENCE", "sourceId": "approve-missing-action", "targetId": "approve-bad-action" },
            { "id": "e3", "type": "SEQUENCE", "sourceId": "approve-bad-action", "targetId": "approve-bad-duration" },
            { "id": "e4", "type": "SEQUENCE", "sourceId": "approve-bad-duration", "targetId": "approve-blank-transfer" },
            { "id": "e5", "type": "SEQUENCE", "sourceId": "approve-blank-transfer", "targetId": "end-a" }
          ]
        }
        """;
  }

  private String xorBranchFlowJson() {
    return """
        {
          "formatVersion": 2,
          "id": "flow-xor-branch",
          "name": "默认异或分支",
          "nodes": [
            { "type": "发起节点", "id": "start-a", "permission": [] },
            { "type": "审批节点", "id": "approve-a", "subType": "单签", "candidates": [] },
            { "type": "审批节点", "id": "approve-b", "subType": "单签", "candidates": [] },
            { "type": "结束节点", "id": "end-a" }
          ],
          "edges": [
            { "id": "e1", "type": "SEQUENCE", "sourceId": "start-a", "targetId": "approve-a", "condition": { "left": { "type": "field", "field": "amount" }, "op": "greater", "right": 1000 } },
            { "id": "e2", "type": "SEQUENCE", "sourceId": "start-a", "targetId": "approve-b" },
            { "id": "e3", "type": "SEQUENCE", "sourceId": "approve-a", "targetId": "end-a" },
            { "id": "e4", "type": "SEQUENCE", "sourceId": "approve-b", "targetId": "end-a" }
          ]
        }
        """;
  }

  private String orJoinBranchFlowJson() {
    return """
        {
          "formatVersion": 2,
          "id": "flow-or-branch",
          "name": "显式OR分支",
          "nodes": [
            { "type": "发起节点", "id": "start-a", "permission": [] },
            { "type": "审批节点", "id": "approve-a", "subType": "单签", "candidates": [] },
            { "type": "审批节点", "id": "approve-b", "subType": "单签", "candidates": [] },
            { "type": "JoinNode", "id": "join-a", "joinType": "OR" },
            { "type": "结束节点", "id": "end-a" }
          ],
          "edges": [
            { "id": "e1", "type": "SEQUENCE", "sourceId": "start-a", "targetId": "approve-a", "condition": { "left": { "type": "field", "field": "amount" }, "op": "greater", "right": 1000 } },
            { "id": "e2", "type": "SEQUENCE", "sourceId": "start-a", "targetId": "approve-b" },
            { "id": "e3", "type": "SEQUENCE", "sourceId": "approve-a", "targetId": "join-a" },
            { "id": "e4", "type": "SEQUENCE", "sourceId": "approve-b", "targetId": "join-a" },
            { "id": "e5", "type": "SEQUENCE", "sourceId": "join-a", "targetId": "end-a" }
          ]
        }
        """;
  }

  private String subflowJson() {
    return """
        {
          "formatVersion": 2,
          "id": "flow-subflow",
          "name": "子流程上下文",
          "formType": "default",
          "formKey": "advanced-form",
          "nodes": [
            { "type": "发起节点", "id": "start-a", "permission": [] },
            { "type": "SubflowNode", "id": "subflow-a", "name": "调用子流程", "subFlowKey": "child-flow", "inParameters": [], "outParameters": [] },
            { "type": "结束节点", "id": "end-a" }
          ],
          "edges": [
            { "id": "e1", "type": "SEQUENCE", "sourceId": "start-a", "targetId": "subflow-a" },
            { "id": "e2", "type": "SEQUENCE", "sourceId": "subflow-a", "targetId": "end-a" }
          ]
        }
        """;
  }
}
