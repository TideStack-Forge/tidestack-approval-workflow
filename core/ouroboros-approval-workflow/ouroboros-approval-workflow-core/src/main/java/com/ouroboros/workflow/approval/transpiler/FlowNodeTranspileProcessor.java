package com.ouroboros.workflow.approval.transpiler;

import com.ouroboros.workflow.approval.util.TreeGraphNode;

public interface FlowNodeTranspileProcessor<M> {
  boolean supportModel(Object flowModel);

  boolean supportNode(TreeGraphNode flowNode);

  Runnable process(TranspileContext<M> context, TreeGraphNode node);
}
