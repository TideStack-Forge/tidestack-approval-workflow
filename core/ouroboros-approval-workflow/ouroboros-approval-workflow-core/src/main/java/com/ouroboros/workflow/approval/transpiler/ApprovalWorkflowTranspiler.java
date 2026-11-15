package com.ouroboros.workflow.approval.transpiler;

import com.ouroboros.workflow.approval.definition.FlowDefinition;
import com.ouroboros.workflow.approval.definition.graph.StructuredFlowDefinition;

public interface ApprovalWorkflowTranspiler<T> {
  T transpile(StructuredFlowDefinition flowDefinition);

  T transpile(FlowDefinition flowDefinition);
}
