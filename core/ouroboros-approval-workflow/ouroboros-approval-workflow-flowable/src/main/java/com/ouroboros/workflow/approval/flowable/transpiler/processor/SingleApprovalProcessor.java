package com.ouroboros.workflow.approval.flowable.transpiler.processor;

import static com.ouroboros.workflow.approval.flowable.util.BpmnUtil.buildSequenceFlow;
import static com.ouroboros.workflow.approval.flowable.util.BpmnUtil.buildFlowableListener;
import static com.ouroboros.workflow.approval.flowable.util.BpmnUtil.mapFormProperties;
import static com.ouroboros.workflow.approval.flowable.util.BpmnUtil.saveActions;
import static com.ouroboros.workflow.approval.flowable.util.BpmnUtil.saveCandidates;
import static com.ouroboros.workflow.approval.flowable.util.BpmnUtil.variableEqualsExpression;

import java.util.ArrayList;

import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.ExclusiveGateway;
import org.flowable.bpmn.model.FlowableListener;
import org.flowable.bpmn.model.InclusiveGateway;
import org.flowable.bpmn.model.ImplementationType;
import org.flowable.bpmn.model.Process;
import org.flowable.bpmn.model.ServiceTask;
import org.flowable.bpmn.model.UserTask;
import org.flowable.task.service.delegate.TaskListener;

import com.ouroboros.workflow.approval.definition.FlowNode;
import com.ouroboros.workflow.approval.definition.nodes.SingleApprovalNode;
import com.ouroboros.workflow.approval.flowable.delegate.AddSignGateDelegate;
import com.ouroboros.workflow.approval.flowable.listener.CreateSingleApprovalTaskListener;
import com.ouroboros.workflow.approval.flowable.listener.SingleApproveCompletedTaskListener;
import com.ouroboros.workflow.approval.flowable.transpiler.Constants;
import com.ouroboros.workflow.approval.transpiler.TranspileContext;
import com.ouroboros.workflow.approval.util.TreeGraphNode;

public class SingleApprovalProcessor extends FlowableAbstractProcessor {
  @Override
  Runnable doProcess(TranspileContext<BpmnModel> context, Process process, TreeGraphNode node) {
    var flowNode = (SingleApprovalNode) node.getFlowNode();
    var approvedVar = flowNode.getId() + Constants.Variable.APPROVED_SUFFIX;
    var approvalActionVar = flowNode.getId() + Constants.Variable.APPROVAL_ACTION_SUFFIX;
    var formKey = context.getStructuredFlowDefinition().getFormKey();

    var approvalTask = new UserTask();
    var decisionGateway = new ExclusiveGateway();
    var addSignAfterCurrentGateway = new ExclusiveGateway();
    var addSignGateServiceTask = new ServiceTask();
    var addSignAfterCurrentGateTask = new UserTask();
    var approvedGateway = new InclusiveGateway();
    var taskListeners = new ArrayList<FlowableListener>();
    var addSignAfterCurrentActiveVar = Constants.Variable.addSignAfterCurrentGateActiveVariableName(flowNode.getId());

    approvalTask.setId(flowNode.getId());
    approvalTask.setName(flowNode.getName());
    approvalTask.setFormKey(formKey);
    approvalTask.setTaskListeners(taskListeners);
    approvalTask.setFormProperties(mapFormProperties(flowNode.getPermission()));
    saveCandidates(approvalTask, flowNode.getCandidates());
    saveActions(approvalTask, flowNode.getActions());

    taskListeners.add(buildFlowableListener(TaskListener.EVENTNAME_CREATE, CreateSingleApprovalTaskListener.BEAN_NAME));
    taskListeners.add(buildFlowableListener(TaskListener.EVENTNAME_COMPLETE, SingleApproveCompletedTaskListener.BEAN_NAME));

    decisionGateway.setId(flowNode.getId() + Constants.SpecialNode.DECISION_ID_SUFFIX);
    decisionGateway.setName(flowNode.getName() + Constants.SpecialNode.DECISION_NAME_SUFFIX);

    approvedGateway.setId(flowNode.getId() + Constants.SpecialNode.APPROVED_ID_SUFFIX);
    approvedGateway.setName(flowNode.getName() + Constants.SpecialNode.APPROVED_NAME_SUFFIX);

    addSignAfterCurrentGateway.setId(flowNode.getId() + Constants.SpecialNode.ADD_SIGN_AFTER_CURRENT_GATEWAY_ID_SUFFIX);
    addSignAfterCurrentGateway.setName(flowNode.getName() + Constants.SpecialNode.ADD_SIGN_AFTER_CURRENT_GATEWAY_NAME_SUFFIX);

    addSignGateServiceTask.setId(flowNode.getId() + "__addSignGate");
    addSignGateServiceTask.setName(flowNode.getName() + "加签后置网关");
    addSignGateServiceTask.setImplementationType(ImplementationType.IMPLEMENTATION_TYPE_CLASS);
    addSignGateServiceTask.setImplementation(AddSignGateDelegate.class.getName());

    addSignAfterCurrentGateTask.setId(Constants.Variable.addSignAfterCurrentGateTaskId(flowNode.getId()));
    addSignAfterCurrentGateTask.setName(flowNode.getName() + Constants.SpecialNode.ADD_SIGN_AFTER_CURRENT_GATE_TASK_NAME_SUFFIX);

    var prevApprovalNode = getPrevApprovalNode(node);
    var rollbackId = prevApprovalNode == null
        ? Constants.SpecialNode.FAIL_NODE_ID
        : context.getEntryPoint(prevApprovalNode.getFlowNode().getId());

    process.addFlowElement(approvalTask);
    process.addFlowElement(decisionGateway);
    process.addFlowElement(addSignAfterCurrentGateway);
    process.addFlowElement(addSignGateServiceTask);
    process.addFlowElement(addSignAfterCurrentGateTask);
    process.addFlowElement(approvedGateway);
    addTimeoutBoundary(process, flowNode, approvalTask);

    process.addFlowElement(buildSequenceFlow(approvalTask, decisionGateway));
    process.addFlowElement(buildSequenceFlow(decisionGateway, addSignAfterCurrentGateway, variableEqualsExpression(approvedVar, true)));
    process.addFlowElement(buildSequenceFlow(
        addSignAfterCurrentGateway,
        addSignGateServiceTask,
        variableEqualsExpression(addSignAfterCurrentActiveVar, true)));
    process.addFlowElement(buildSequenceFlow(addSignGateServiceTask, addSignAfterCurrentGateTask));
    process.addFlowElement(buildSequenceFlow(
        addSignAfterCurrentGateway,
        approvedGateway,
        variableEqualsExpression(addSignAfterCurrentActiveVar, false)));
    process.addFlowElement(buildSequenceFlow(addSignAfterCurrentGateTask, approvedGateway));
    process.addFlowElement(buildSequenceFlow(decisionGateway.getId(),
        Constants.SpecialNode.FAIL_NODE_ID,
        variableEqualsExpression(approvedVar, false) + " && "
            + variableEqualsExpression(approvalActionVar, Constants.Value.REJECTED)));
    process.addFlowElement(buildSequenceFlow(decisionGateway.getId(),
        rollbackId,
        variableEqualsExpression(approvedVar, false) + " && "
            + variableEqualsExpression(approvalActionVar, Constants.Value.ROLLBACK)));

    context.setEntryPoint(flowNode.getId(), approvalTask.getId());
    context.setExitPoint(flowNode.getId(), approvedGateway.getId());
    return null;
  }

  @Override
  public boolean supportNode(FlowNode flowNode) {
    return flowNode instanceof SingleApprovalNode;
  }
}
