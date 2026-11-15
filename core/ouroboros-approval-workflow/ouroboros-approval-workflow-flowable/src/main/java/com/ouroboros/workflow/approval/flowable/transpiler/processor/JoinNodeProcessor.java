package com.ouroboros.workflow.approval.flowable.transpiler.processor;

import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.Gateway;
import org.flowable.bpmn.model.InclusiveGateway;
import org.flowable.bpmn.model.ParallelGateway;
import org.flowable.bpmn.model.Process;

import com.ouroboros.workflow.approval.definition.FlowNode;
import com.ouroboros.workflow.approval.definition.nodes.JoinNode;
import com.ouroboros.workflow.approval.semantics.JoinType;
import com.ouroboros.workflow.approval.semantics.JoinSemantics;
import com.ouroboros.workflow.approval.transpiler.TranspileContext;
import com.ouroboros.workflow.approval.util.TreeGraphNode;

public class JoinNodeProcessor extends FlowableAbstractProcessor {
  @Override
  Runnable doProcess(TranspileContext<BpmnModel> context, Process process, TreeGraphNode node) {
    JoinNode flowNode = (JoinNode) node.getFlowNode();

    Gateway gateway;
    JoinType normalizedType = JoinSemantics.normalizeJoinType(flowNode.getJoinType());
    if (normalizedType == JoinType.AND) {
      gateway = new ParallelGateway();
    } else {
      gateway = new InclusiveGateway();
    }

    gateway.setId(flowNode.getId());
    gateway.setName(flowNode.getName());
    process.addFlowElement(gateway);
    context.setEntryPoint(flowNode.getId(), gateway.getId());
    context.setExitPoint(flowNode.getId(), gateway.getId());
    return null;
  }

  @Override
  public boolean supportNode(FlowNode flowNode) {
    return flowNode instanceof JoinNode;
  }
}
