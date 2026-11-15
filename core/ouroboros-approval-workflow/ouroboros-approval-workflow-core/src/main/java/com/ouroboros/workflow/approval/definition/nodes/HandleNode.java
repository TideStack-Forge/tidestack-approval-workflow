package com.ouroboros.workflow.approval.definition.nodes;

import com.ouroboros.workflow.approval.definition.FlowNode;

public class HandleNode extends FlowNode {
  public HandleNode() {
    setType(BuiltInFlowNodeTypes.HANDLE);
  }
}
