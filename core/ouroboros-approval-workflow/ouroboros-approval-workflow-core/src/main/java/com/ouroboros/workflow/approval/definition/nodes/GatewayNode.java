package com.ouroboros.workflow.approval.definition.nodes;

import com.ouroboros.workflow.approval.definition.FlowNode;

public class GatewayNode extends FlowNode {
  String splitType;

  @Override
  public String getType() {
    return "GatewayNode";
  }

  public String getSplitType() {
    return splitType;
  }

  public void setSplitType(String splitType) {
    this.splitType = splitType;
  }
}
