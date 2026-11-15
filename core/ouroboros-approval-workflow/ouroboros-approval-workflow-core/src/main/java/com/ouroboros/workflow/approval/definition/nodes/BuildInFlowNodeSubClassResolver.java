package com.ouroboros.workflow.approval.definition.nodes;

import com.ouroboros.workflow.approval.definition.FlowNodeSubClassResolver;

public class BuildInFlowNodeSubClassResolver implements FlowNodeSubClassResolver {
  @Override
  public Class<?> resolve(String type) {
    String normalizedType = BuiltInFlowNodeTypes.normalize(type);
    if (BuiltInFlowNodeTypes.START.equals(normalizedType)) {
      return StartNode.class;
    }
    if (BuiltInFlowNodeTypes.APPROVAL.equals(normalizedType)) {
      return ApprovalNode.class;
    }
    if (BuiltInFlowNodeTypes.HANDLE.equals(normalizedType)) {
      return HandleNode.class;
    }
    if (BuiltInFlowNodeTypes.CC.equals(normalizedType)) {
      return CcNode.class;
    }
    if (BuiltInFlowNodeTypes.NOTICE.equals(normalizedType)) {
      return NoticeNode.class;
    }
    if ("分流节点".equals(type)) {
      return ForkNode.class;
    }
    if (BuiltInFlowNodeTypes.SUBFLOW.equals(normalizedType)) {
      return SubflowNode.class;
    }
    if (BuiltInFlowNodeTypes.JOIN.equals(normalizedType)) {
      return JoinNode.class;
    }
    if (BuiltInFlowNodeTypes.END.equals(normalizedType)) {
      return EndNode.class;
    }
    return null;
  }
}
