package com.ouroboros.workflow.approval.definition.nodes;

import com.ouroboros.workflow.approval.definition.FlowNode;

public class CcNode extends FlowNode {
  public CcNode() {
    super();
  }

  @Override
  public String getType() {
    return BuiltInFlowNodeTypes.CC;
  }
}
