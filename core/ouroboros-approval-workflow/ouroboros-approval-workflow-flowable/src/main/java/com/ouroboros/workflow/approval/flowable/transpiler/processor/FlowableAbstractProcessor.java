package com.ouroboros.workflow.approval.flowable.transpiler.processor;

import java.time.Duration;
import java.time.format.DateTimeParseException;

import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.BoundaryEvent;
import org.flowable.bpmn.model.FieldExtension;
import org.flowable.bpmn.model.Process;
import org.flowable.bpmn.model.ServiceTask;
import org.flowable.bpmn.model.TimerEventDefinition;
import org.flowable.bpmn.model.UserTask;

import com.ouroboros.workflow.approval.definition.FlowNode;
import com.ouroboros.workflow.approval.definition.nodes.ApprovalNode;
import com.ouroboros.workflow.approval.flowable.delegate.ApprovalTimeoutDelegate;
import com.ouroboros.workflow.approval.flowable.transpiler.Constants;
import com.ouroboros.workflow.approval.flowable.util.BpmnUtil;
import com.ouroboros.workflow.approval.transpiler.FlowNodeTranspileProcessor;
import com.ouroboros.workflow.approval.transpiler.TranspileContext;
import com.ouroboros.workflow.approval.util.TreeGraphNode;

public abstract class FlowableAbstractProcessor implements FlowNodeTranspileProcessor<BpmnModel> {
  @Override
  public boolean supportModel(Object flowModel) {
    return flowModel instanceof BpmnModel;
  }

  @Override
  public boolean supportNode(TreeGraphNode flowNode) {
    return supportNode(flowNode.getFlowNode());
  }

  @Override
  public final Runnable process(TranspileContext<BpmnModel> context, TreeGraphNode node) {
    var process = context.getModel().getMainProcess();
    return doProcess(context, process, node);
  }

  abstract Runnable doProcess(TranspileContext<BpmnModel> context, Process process, TreeGraphNode node);

  abstract boolean supportNode(FlowNode flowNode);

  protected TreeGraphNode getPrevApprovalNode(TreeGraphNode node) {
    if (node == null) {
      return null;
    }
    TreeGraphNode prevNode = node.getPrevNode();
    if (prevNode == null) {
      return getPrevApprovalNode(node.getParentNode());
    }
    if (prevNode.getFlowNode() instanceof ApprovalNode) {
      return prevNode;
    }
    return getPrevApprovalNode(prevNode);
  }

  protected void addTimeoutBoundary(Process process, ApprovalNode flowNode, UserTask approvalTask) {
    ApprovalNode.Timeout timeout = flowNode.getTimeout();
    if (timeout == null
        || !isValidDuration(timeout.getDuration())
        || !isSupportedTimeout(timeout)) {
      return;
    }

    BoundaryEvent boundaryEvent = new BoundaryEvent();
    boundaryEvent.setId(flowNode.getId() + Constants.SpecialNode.TIMEOUT_BOUNDARY_ID_SUFFIX);
    boundaryEvent.setName(flowNode.getName() + Constants.SpecialNode.TIMEOUT_BOUNDARY_NAME_SUFFIX);
    boundaryEvent.setAttachedToRef(approvalTask);
    boundaryEvent.setAttachedToRefId(approvalTask.getId());
    boundaryEvent.setCancelActivity(false);

    TimerEventDefinition timerEventDefinition = new TimerEventDefinition();
    timerEventDefinition.setTimeDuration(timeout.getDuration());
    boundaryEvent.addEventDefinition(timerEventDefinition);

    ServiceTask timeoutServiceTask = new ServiceTask();
    timeoutServiceTask.setId(flowNode.getId() + Constants.SpecialNode.TIMEOUT_SERVICE_TASK_ID_SUFFIX);
    timeoutServiceTask.setName(flowNode.getName() + Constants.SpecialNode.TIMEOUT_SERVICE_TASK_NAME_SUFFIX);
    BpmnUtil.useDelegateBean(timeoutServiceTask, ApprovalTimeoutDelegate.BEAN_NAME);
    timeoutServiceTask.getFieldExtensions().add(field(Constants.Property.APPROVAL_TASK_ID, approvalTask.getId()));
    timeoutServiceTask.getFieldExtensions().add(field(Constants.Property.TIMEOUT_ACTION, timeout.getAction()));
    timeoutServiceTask.getFieldExtensions().add(field(Constants.Property.TIMEOUT_TRANSFER_TO, timeout.getTransferTo()));

    process.addFlowElement(boundaryEvent);
    process.addFlowElement(timeoutServiceTask);
    process.addFlowElement(BpmnUtil.buildSequenceFlow(boundaryEvent, timeoutServiceTask));
  }

  private FieldExtension field(String name, String value) {
    return BpmnUtil.buildFieldExtension(name, value == null ? "" : value);
  }

  private boolean isSupportedTimeout(ApprovalNode.Timeout timeout) {
    String action = timeout.getAction();
    if (Constants.Value.TIMEOUT_ACTION_TRANSFER.equals(action)) {
      return timeout.getTransferTo() != null && !timeout.getTransferTo().trim().isEmpty();
    }
    return Constants.Value.TIMEOUT_ACTION_REMIND.equals(action)
        || Constants.Value.TIMEOUT_ACTION_AUTO_APPROVE.equals(action)
        || Constants.Value.TIMEOUT_ACTION_AUTO_REJECT.equals(action);
  }

  private boolean isValidDuration(String duration) {
    if (duration == null || duration.trim().isEmpty()) {
      return false;
    }
    try {
      Duration.parse(duration);
      return true;
    } catch (DateTimeParseException ex) {
      return false;
    }
  }
}
