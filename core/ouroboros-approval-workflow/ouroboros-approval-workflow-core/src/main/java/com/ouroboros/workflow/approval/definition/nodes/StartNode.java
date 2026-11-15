package com.ouroboros.workflow.approval.definition.nodes;

import com.ouroboros.workflow.approval.definition.FlowNode;

public class StartNode extends FlowNode {
  public StartNode() {
    super();
  }

  @Override
  public String getType() {
    return BuiltInFlowNodeTypes.START;
  }
}
