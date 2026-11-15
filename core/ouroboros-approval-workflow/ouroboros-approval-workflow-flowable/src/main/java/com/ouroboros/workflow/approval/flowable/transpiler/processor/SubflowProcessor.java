package com.ouroboros.workflow.approval.flowable.transpiler.processor;

import static com.ouroboros.workflow.approval.flowable.util.BpmnUtil.buildSequenceFlow;
import static com.ouroboros.workflow.approval.flowable.util.BpmnUtil.mapIOParameter;
import static com.ouroboros.workflow.approval.flowable.util.BpmnUtil.saveRejectAction;
import static com.ouroboros.workflow.approval.flowable.util.BpmnUtil.variableEqualsExpression;

import java.util.ArrayList;

import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.CallActivity;
import org.flowable.bpmn.model.ExclusiveGateway;
import org.flowable.bpmn.model.InclusiveGateway;
import org.flowable.bpmn.model.Process;

import com.ouroboros.workflow.approval.definition.FlowNode;
import com.ouroboros.workflow.approval.definition.IOParameterSetting;
import com.ouroboros.workflow.approval.definition.nodes.SubflowNode;
import com.ouroboros.workflow.approval.flowable.transpiler.Constants;
import com.ouroboros.workflow.approval.transpiler.TranspileContext;
import com.ouroboros.workflow.approval.util.TreeGraphNode;

public class SubflowProcessor extends FlowableAbstractProcessor {
  @Override
  Runnable doProcess(TranspileContext<BpmnModel> context, Process process, TreeGraphNode node) {
    var flowNode = (SubflowNode) node.getFlowNode();
    var approvedVariableName = flowNode.getId() + Constants.Variable.APPROVED_SUFFIX;

    var callFlowTask = new CallActivity();
    var decisionGateway = new ExclusiveGateway();
    var approvedGateway = new InclusiveGateway();
    var inParameters = new ArrayList<IOParameterSetting>(flowNode.getInParameters());
    var outParameters = new ArrayList<IOParameterSetting>(flowNode.getOutParameters());

    inParameters.add(new IOParameterSetting(Constants.Variable.PROCESS_STARTER, "", Constants.Variable.PROCESS_STARTER));
    inParameters.add(new IOParameterSetting(
        Constants.Variable.USER_SELECTED_ASSIGNEES,
        "",
        Constants.Variable.USER_SELECTED_ASSIGNEES));
    inParameters.add(new IOParameterSetting(
        Constants.Variable.PROCESS_LAST_FINISHED_TASK_ID,
        "",
        Constants.Variable.PROCESS_LAST_FINISHED_TASK_ID));
    inParameters.add(new IOParameterSetting(Constants.TASK_FORM_TYPE_KEY, "", Constants.TASK_FORM_TYPE_KEY));
    inParameters.add(new IOParameterSetting(Constants.TASK_FORM_ID_KEY, "", Constants.TASK_FORM_ID_KEY));
    inParameters.add(new IOParameterSetting(Constants.CANDIDATE_FORM_DATA_KEY, "", Constants.CANDIDATE_FORM_DATA_KEY));
    inParameters.add(new IOParameterSetting(null, "true", Constants.Variable.HAS_PARENT_FLOW));
    outParameters.add(new IOParameterSetting(Constants.Variable.PROCESS_APPROVED, "", approvedVariableName));
    outParameters.add(new IOParameterSetting(
        Constants.Variable.PROCESS_LAST_FINISHED_TASK_ID,
        "",
        Constants.Variable.PROCESS_LAST_FINISHED_TASK_ID));

    callFlowTask.setId(flowNode.getId());
    callFlowTask.setName(flowNode.getName());
    callFlowTask.setCalledElement(flowNode.getSubFlowKey());
    callFlowTask.setInParameters(mapIOParameter(inParameters));
    callFlowTask.setOutParameters(mapIOParameter(outParameters));
    saveRejectAction(callFlowTask, flowNode.getRejectAction());

    decisionGateway.setId(flowNode.getId() + Constants.SpecialNode.DECISION_ID_SUFFIX);
    decisionGateway.setName(flowNode.getName() + Constants.SpecialNode.DECISION_NAME_SUFFIX);

    approvedGateway.setId(flowNode.getId() + Constants.SpecialNode.APPROVED_ID_SUFFIX);
    approvedGateway.setName(flowNode.getName() + Constants.SpecialNode.APPROVED_NAME_SUFFIX);

    var prevApprovalNode = getPrevApprovalNode(node);
    var rejectedTarget = Constants.Value.ROLLBACK.equalsIgnoreCase(flowNode.getRejectAction()) && prevApprovalNode != null
        ? context.getEntryPoint(prevApprovalNode.getFlowNode().getId())
        : Constants.SpecialNode.FAIL_NODE_ID;

    process.addFlowElement(callFlowTask);
    process.addFlowElement(decisionGateway);
    process.addFlowElement(approvedGateway);

    process.addFlowElement(buildSequenceFlow(callFlowTask, decisionGateway));
    process.addFlowElement(buildSequenceFlow(decisionGateway, approvedGateway, variableEqualsExpression(approvedVariableName, true)));
    process.addFlowElement(buildSequenceFlow(decisionGateway.getId(), rejectedTarget, variableEqualsExpression(approvedVariableName, false)));

    context.setEntryPoint(flowNode.getId(), callFlowTask.getId());
    context.setExitPoint(flowNode.getId(), approvedGateway.getId());
    return null;
  }

  @Override
  public boolean supportNode(FlowNode flowNode) {
    return flowNode instanceof SubflowNode;
  }
}
