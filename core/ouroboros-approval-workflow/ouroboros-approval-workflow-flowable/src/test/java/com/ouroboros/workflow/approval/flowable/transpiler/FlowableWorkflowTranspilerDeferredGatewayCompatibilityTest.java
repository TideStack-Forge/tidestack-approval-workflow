package com.ouroboros.workflow.approval.flowable.transpiler;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.ExclusiveGateway;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.Gateway;
import org.flowable.bpmn.model.InclusiveGateway;
import org.flowable.bpmn.model.ParallelGateway;
import org.flowable.bpmn.model.Process;

import com.ouroboros.workflow.approval.definition.codec.CompositeFlowDefinitionCodec;
import com.ouroboros.workflow.approval.definition.graph.StructuredFlowDefinition;
import com.ouroboros.workflow.approval.testkit.contract.AbstractDeferredGatewayCompatibilityContractTest;
import com.ouroboros.workflow.approval.semantics.GatewaySemantics;
import com.ouroboros.workflow.approval.semantics.SplitType;

class FlowableWorkflowTranspilerDeferredGatewayCompatibilityTest extends AbstractDeferredGatewayCompatibilityContractTest {
  private final CompositeFlowDefinitionCodec codec = new CompositeFlowDefinitionCodec();
  private final FlowableWorkflowTranspiler transpiler = new FlowableWorkflowTranspiler();

  @Override
  protected Object transpile(String json) {
    return transpiler.transpile(codec.decode(json).get());
  }

  @Override
  protected Object getMainProcess(Object model) {
    return ((BpmnModel) model).getMainProcess();
  }

  @Override
  protected Object findGatewayAfterNode(Object process, String nodeId) {
    return findGatewayAfterNode((Process) process, nodeId);
  }

  @Override
  protected Object getFlowElement(Object process, String elementId) {
    return ((Process) process).getFlowElement(elementId);
  }

  @Override
  protected SplitType inferCompatibilitySplitType(String json, String sourceNodeId) {
    StructuredFlowDefinition definition = codec.decode(json).get();
    return GatewaySemantics.inferSplitType(definition.getOutgoingEdges(sourceNodeId));
  }

  @Override
  protected void assertIsInclusiveGateway(Object gateway, String message) {
    assertTrue(gateway instanceof InclusiveGateway, message + "，实际类型：" + gateway.getClass().getSimpleName());
  }

  @Override
  protected void assertIsExclusiveGateway(Object gateway, String message) {
    assertTrue(gateway instanceof ExclusiveGateway, message + "，实际类型：" + gateway.getClass().getSimpleName());
  }

  @Override
  protected void assertIsParallelGateway(Object gateway, String message) {
    assertTrue(gateway instanceof ParallelGateway, message + "，实际类型：" + gateway.getClass().getSimpleName());
  }

  private Gateway findGatewayAfterNode(Process process, String nodeId) {
    FlowElement element = process.getFlowElement(nodeId);
    if (element == null) {
      return null;
    }

    String[] suffixes = {"__andSplit", "__xorSplit", "__orSplit"};
    for (String suffix : suffixes) {
      FlowElement gateway = process.getFlowElement(nodeId + suffix);
      if (gateway instanceof Gateway) {
        return (Gateway) gateway;
      }
    }

    return null;
  }

  private Gateway findGatewayBeforeNode(Process process, String nodeId) {
    for (FlowElement fe : process.getFlowElements()) {
      if (!(fe instanceof org.flowable.bpmn.model.SequenceFlow)) {
        continue;
      }
      org.flowable.bpmn.model.SequenceFlow flow = (org.flowable.bpmn.model.SequenceFlow) fe;
      if (!nodeId.equals(flow.getTargetRef())) {
        continue;
      }
      FlowElement source = process.getFlowElement(flow.getSourceRef());
      if (source instanceof Gateway) {
        return (Gateway) source;
      }
    }
    return null;
  }

}
