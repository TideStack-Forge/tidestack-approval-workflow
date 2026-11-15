package com.ouroboros.workflow.approval.flowable.transpiler.processor;

import static com.ouroboros.workflow.approval.flowable.util.BpmnUtil.buildFieldExtension;
import static com.ouroboros.workflow.approval.flowable.util.BpmnUtil.buildSequenceFlow;
import static com.ouroboros.workflow.approval.flowable.util.BpmnUtil.useDelegateBean;

import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.Process;
import org.flowable.bpmn.model.ServiceTask;

import com.ouroboros.workflow.approval.definition.FlowNode;
import com.ouroboros.workflow.approval.definition.nodes.EndNode;
import com.ouroboros.workflow.approval.flowable.delegate.SetProcessOutcomeServiceTask;
import com.ouroboros.workflow.approval.flowable.transpiler.Constants;
import com.ouroboros.workflow.approval.transpiler.TranspileContext;
import com.ouroboros.workflow.approval.util.TreeGraphNode;

public class EndNodeProcessor extends FlowableAbstractProcessor {
  @Override
  Runnable doProcess(TranspileContext<BpmnModel> context, Process process, TreeGraphNode node) {
    EndNode flowNode = (EndNode) node.getFlowNode();
    ServiceTask outcomeTask = new ServiceTask();
    outcomeTask.setId(flowNode.getId() + Constants.SpecialNode.OUTCOME_ID_SUFFIX);
    outcomeTask.setName(flowNode.getName());
    useDelegateBean(outcomeTask, SetProcessOutcomeServiceTask.BEAN_NAME);
    outcomeTask.getFieldExtensions().add(buildFieldExtension(Constants.Property.PROCESS_OUTCOME, flowNode.resolveOutcome()));

    process.addFlowElement(outcomeTask);
    process.addFlowElement(buildSequenceFlow(outcomeTask.getId(), Constants.SpecialNode.END_EVENT_ID));

    context.setEntryPoint(flowNode.getId(), outcomeTask.getId());
    context.setExitPoint(flowNode.getId(), Constants.SpecialNode.END_EVENT_ID);
    return null;
  }

  @Override
  public boolean supportNode(FlowNode flowNode) {
    return flowNode instanceof EndNode;
  }
}
