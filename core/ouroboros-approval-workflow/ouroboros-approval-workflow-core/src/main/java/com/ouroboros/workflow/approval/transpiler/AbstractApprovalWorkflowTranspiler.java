package com.ouroboros.workflow.approval.transpiler;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.ouroboros.util.Json;
import com.ouroboros.workflow.approval.definition.FlowDefinition;
import com.ouroboros.workflow.approval.definition.codec.CompositeFlowDefinitionCodec;
import com.ouroboros.workflow.approval.definition.graph.StructuredFlowDefinition;

public abstract class AbstractApprovalWorkflowTranspiler<T> implements ApprovalWorkflowTranspiler<T> {
  private static final CompositeFlowDefinitionCodec FLOW_DEFINITION_CODEC = new CompositeFlowDefinitionCodec();

  @Override
  public T transpile(FlowDefinition flowDefinition) {
    var structuredDefinition = FLOW_DEFINITION_CODEC.decode(Json.toJsonString(flowDefinition)).get();
    return transpile(structuredDefinition);
  }

  @Override
  public T transpile(StructuredFlowDefinition flowDefinition) {
    TranspileContext<T> context = initContext(flowDefinition);
    preTranspile(context);
    doTranspile(context);
    postTranspile(context);
    return context.getModel();
  }

  protected abstract List<FlowNodeTranspileProcessor<T>> getTranspileProcessors();

  protected void doTranspile(TranspileContext<T> context) {
    var processors = getTranspileProcessors();
    var nodes = context.getTreeGraphNodes();
    var postProcessors = new ArrayList<Runnable>();
    for (var node : nodes) {
      for (var processor : processors) {
        if (processor.supportNode(node)) {
          postProcessors.add(processor.process(context, node));
        }
      }
    }
    postProcessors.stream()
        .filter(Objects::nonNull)
        .forEach(Runnable::run);
  }

  protected void preTranspile(TranspileContext<T> context) {

  }

  abstract protected void postTranspile(TranspileContext<T> context);

  protected abstract TranspileContext<T> initContext(StructuredFlowDefinition flowDefinition);
}
