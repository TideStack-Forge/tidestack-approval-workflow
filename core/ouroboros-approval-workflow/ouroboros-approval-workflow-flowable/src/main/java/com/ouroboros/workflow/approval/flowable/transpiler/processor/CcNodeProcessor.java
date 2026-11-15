package com.ouroboros.workflow.approval.flowable.transpiler.processor;

import static com.ouroboros.workflow.approval.flowable.util.BpmnUtil.buildLargeTextExtensionElement;
import static com.ouroboros.workflow.approval.flowable.util.BpmnUtil.useDelegateBean;

import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.Process;
import org.flowable.bpmn.model.ServiceTask;

import com.ouroboros.util.Json;
import com.ouroboros.workflow.approval.definition.FlowNode;
import com.ouroboros.workflow.approval.definition.nodes.CcNode;
import com.ouroboros.workflow.approval.flowable.delegate.CcServiceTask;
import com.ouroboros.workflow.approval.flowable.transpiler.Constants;
import com.ouroboros.workflow.approval.transpiler.TranspileContext;
import com.ouroboros.workflow.approval.util.TreeGraphNode;

public class CcNodeProcessor extends FlowableAbstractProcessor {
  @Override
  Runnable doProcess(TranspileContext<BpmnModel> context, Process process, TreeGraphNode node) {
    var flowNode = (CcNode) node.getFlowNode();
    var ccServiceTask = new ServiceTask();
    var extensionElement = buildLargeTextExtensionElement("cc", Json.toJsonString(flowNode));

    ccServiceTask.setId(flowNode.getId() + Constants.SpecialNode.APPROVED_ID_SUFFIX);
    ccServiceTask.setName(flowNode.getName());
    useDelegateBean(ccServiceTask, CcServiceTask.BEAN_NAME);
    ccServiceTask.addExtensionElement(extensionElement);

    process.addFlowElement(ccServiceTask);

    context.setEntryPoint(flowNode.getId(), ccServiceTask.getId());
    context.setExitPoint(flowNode.getId(), ccServiceTask.getId());
    return null;
  }

  @Override
  public boolean supportNode(FlowNode flowNode) {
    return flowNode instanceof CcNode;
  }
}
