package com.ouroboros.workflow.approval.definition.nodes;

public final class BuiltInFlowNodeTypes {
  public static final String START = "StartNode";
  public static final String APPROVAL = "ApprovalNode";
  public static final String HANDLE = "HandleNode";
  public static final String CC = "CcNode";
  public static final String SUBFLOW = "SubflowNode";
  public static final String JOIN = "JoinNode";
  public static final String END = "EndNode";
  public static final String NOTICE = "NoticeNode";

  private BuiltInFlowNodeTypes() {
  }

  public static String normalize(String type) {
    if (type == null) {
      return null;
    }
    if (START.equals(type) || "发起节点".equals(type)) {
      return START;
    }
    if (APPROVAL.equals(type) || "审批节点".equals(type)) {
      return APPROVAL;
    }
    if (HANDLE.equals(type) || "办理节点".equals(type)) {
      return HANDLE;
    }
    if (CC.equals(type) || "抄送节点".equals(type)) {
      return CC;
    }
    if (NOTICE.equals(type) || "通知节点".equals(type)) {
      return NOTICE;
    }
    if (SUBFLOW.equals(type) || "子流程节点".equals(type)) {
      return SUBFLOW;
    }
    if (JOIN.equals(type) || "合并节点".equals(type)) {
      return JOIN;
    }
    if (END.equals(type) || "结束节点".equals(type)) {
      return END;
    }
    return type;
  }
}
