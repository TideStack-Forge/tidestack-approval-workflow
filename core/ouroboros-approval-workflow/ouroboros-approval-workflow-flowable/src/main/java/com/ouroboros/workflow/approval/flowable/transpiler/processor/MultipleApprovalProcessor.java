package com.ouroboros.workflow.approval.flowable.transpiler.processor;

import static com.ouroboros.workflow.approval.flowable.util.BpmnUtil.buildFieldExtension;
import static com.ouroboros.workflow.approval.flowable.util.BpmnUtil.buildFlowableListener;
import static com.ouroboros.workflow.approval.flowable.util.BpmnUtil.buildSequenceFlow;
import static com.ouroboros.workflow.approval.flowable.util.BpmnUtil.mapFormProperties;
import static com.ouroboros.workflow.approval.flowable.util.BpmnUtil.useDelegateBean;
import static com.ouroboros.workflow.approval.flowable.util.BpmnUtil.variableEqualsExpression;

import java.util.ArrayList;

import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.FieldExtension;
import org.flowable.bpmn.model.ExclusiveGateway;
import org.flowable.bpmn.model.FlowableListener;
import org.flowable.bpmn.model.InclusiveGateway;
import org.flowable.bpmn.model.MultiInstanceLoopCharacteristics;
import org.flowable.bpmn.model.Process;
import org.flowable.bpmn.model.ServiceTask;
import org.flowable.bpmn.model.UserTask;
import org.flowable.engine.delegate.ExecutionListener;
import org.flowable.task.service.delegate.TaskListener;

import com.ouroboros.workflow.approval.definition.FlowNode;
import com.ouroboros.workflow.approval.definition.nodes.MultipleApprovalNode;
import com.ouroboros.workflow.approval.flowable.delegate.InitializeMultipleApprovalServiceTask;
import com.ouroboros.workflow.approval.flowable.listener.CreateMultipleApprovalTaskListener;
import com.ouroboros.workflow.approval.flowable.listener.MultipleApprovalCompletedTaskListener;
import com.ouroboros.workflow.approval.flowable.listener.MultipleApprovalEndExecutionListener;
import com.ouroboros.workflow.approval.flowable.listener.MultipleApprovalStartExecutionListener;
import com.ouroboros.workflow.approval.flowable.transpiler.Constants;
import com.ouroboros.workflow.approval.transpiler.TranspileContext;
import com.ouroboros.workflow.approval.util.TreeGraphNode;

public class MultipleApprovalProcessor extends FlowableAbstractProcessor {
  @Override
  Runnable doProcess(TranspileContext<BpmnModel> context, Process process, TreeGraphNode node) {
    var flowNode = (MultipleApprovalNode) node.getFlowNode();
    var approvedVar = flowNode.getId() + Constants.Variable.APPROVED_SUFFIX;
    var multipleApprovalCompletedVar = flowNode.getId() + Constants.Variable.MULTIPLE_APPROVAL_COMPLETED_SUFFIX;
    var formKey = context.getStructuredFlowDefinition().getFormKey();
    var isSequential = Constants.Value.MULTIPLE_APPROVAL_MODE_SEQUENTIAL.equalsIgnoreCase(flowNode.getMultipleMode());

    var initialMultipleApprovalServiceTask = new ServiceTask();
    var approvalTask = new UserTask();
    var decisionGateway = new ExclusiveGateway();
    var approvedGateway = new InclusiveGateway();
    var multi = new MultiInstanceLoopCharacteristics();
    var taskListeners = new ArrayList<FlowableListener>();
    var executionListeners = new ArrayList<FlowableListener>();
    var fieldExtensions = new ArrayList<FieldExtension>();

    initialMultipleApprovalServiceTask.setId(flowNode.getId() + Constants.SpecialNode.RESET_MULTIPLE_APPROVAL_ID_SUFFIX);
    initialMultipleApprovalServiceTask.setName(Constants.SpecialNode.RESET_MULTIPLE_APPROVAL_NAME);
    useDelegateBean(initialMultipleApprovalServiceTask, InitializeMultipleApprovalServiceTask.BEAN_NAME);
    initialMultipleApprovalServiceTask.setFieldExtensions(fieldExtensions);

    fieldExtensions.add(buildFieldExtension(Constants.Property.PERCENTAGE, String.valueOf(flowNode.getPercentage())));
    fieldExtensions.add(buildFieldExtension(Constants.Property.MULTIPLE_APPROVAL_TASK_ID, flowNode.getId()));
    fieldExtensions.add(buildFieldExtension(Constants.Property.IS_SEQUENTIAL, String.valueOf(isSequential)));
    fieldExtensions.add(buildFieldExtension(Constants.Property.REJECT_ACTION, flowNode.getRejectAction()));

    approvalTask.setId(flowNode.getId());
    approvalTask.setName(flowNode.getName());
    approvalTask.setAssignee("${assignee}");
    approvalTask.setFormKey(formKey);
    approvalTask.setLoopCharacteristics(multi);
    approvalTask.setFormProperties(mapFormProperties(flowNode.getPermission()));
    approvalTask.setExecutionListeners(executionListeners);
    approvalTask.setTaskListeners(taskListeners);

    multi.setCompletionCondition(
        "${(nrOfCompletedInstances >= nrOfInstances) || ("
            + variableEqualsExpression(multipleApprovalCompletedVar, true)
            + ")}");
    multi.setInputDataItem(Constants.Variable.getMultipleApprovalAssigneeListVariableName(flowNode.getId()));
    multi.setElementVariable("assignee");
    multi.setSequential(isSequential);

    taskListeners.add(buildFlowableListener(TaskListener.EVENTNAME_CREATE, CreateMultipleApprovalTaskListener.BEAN_NAME));
    taskListeners.add(buildFlowableListener(TaskListener.EVENTNAME_COMPLETE, MultipleApprovalCompletedTaskListener.BEAN_NAME));
    executionListeners.add(buildFlowableListener(ExecutionListener.EVENTNAME_START, MultipleApprovalStartExecutionListener.BEAN_NAME));
    executionListeners.add(buildFlowableListener(ExecutionListener.EVENTNAME_END, MultipleApprovalEndExecutionListener.BEAN_NAME));

    decisionGateway.setId(flowNode.getId() + Constants.SpecialNode.DECISION_ID_SUFFIX);
    decisionGateway.setName(flowNode.getName() + Constants.SpecialNode.DECISION_NAME_SUFFIX);

    approvedGateway.setId(flowNode.getId() + Constants.SpecialNode.APPROVED_ID_SUFFIX);
    approvedGateway.setName(flowNode.getName() + Constants.SpecialNode.APPROVED_NAME_SUFFIX);

    var prevApprovalNode = getPrevApprovalNode(node);
    var rejectedTarget = Constants.Value.ROLLBACK.equalsIgnoreCase(flowNode.getRejectAction()) && prevApprovalNode != null
        ? context.getEntryPoint(prevApprovalNode.getFlowNode().getId())
        : Constants.SpecialNode.FAIL_NODE_ID;

    process.addFlowElement(initialMultipleApprovalServiceTask);
    process.addFlowElement(approvalTask);
    process.addFlowElement(decisionGateway);
    process.addFlowElement(approvedGateway);
    addTimeoutBoundary(process, flowNode, approvalTask);

    process.addFlowElement(buildSequenceFlow(initialMultipleApprovalServiceTask, approvalTask));
    process.addFlowElement(buildSequenceFlow(approvalTask, decisionGateway));
    process.addFlowElement(buildSequenceFlow(decisionGateway, approvedGateway, variableEqualsExpression(approvedVar, true)));
    process.addFlowElement(buildSequenceFlow(decisionGateway.getId(), rejectedTarget, variableEqualsExpression(approvedVar, false)));

    context.setEntryPoint(flowNode.getId(), initialMultipleApprovalServiceTask.getId());
    context.setExitPoint(flowNode.getId(), approvedGateway.getId());
    return null;
  }

  @Override
  public boolean supportNode(FlowNode flowNode) {
    return flowNode instanceof MultipleApprovalNode;
  }
}
