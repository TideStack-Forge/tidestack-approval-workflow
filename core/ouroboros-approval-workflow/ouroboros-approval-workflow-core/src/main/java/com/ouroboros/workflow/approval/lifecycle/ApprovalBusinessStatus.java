package com.ouroboros.workflow.approval.lifecycle;

import java.util.Arrays;

/**
 * 审批业务生命周期的规范语义。
 *
 * <p>该枚举不替代业务模型 {@code workflow.bindings[0].statusMap} 中的状态码；
 * 它只定义跨引擎共享的语义及合法转换，业务模型仍决定实际持久化的 code 和 label。
 */
public enum ApprovalBusinessStatus {
  DRAFT("draft", false),
  SUBMITTED("submitted", false),
  PENDING("pending", false),
  PROCESSING("processing", false),
  SUSPENDED("suspended", false),
  WITHDRAWN("withdrawn", false),
  APPROVED("approved", true),
  VETO("veto", true),
  CANCELED("canceled", true),
  TERMINATED("terminated", true);

  private final String statusMapKey;
  private final boolean terminal;

  ApprovalBusinessStatus(String statusMapKey, boolean terminal) {
    this.statusMapKey = statusMapKey;
    this.terminal = terminal;
  }

  /** 业务模型 {@code statusMap} 的稳定语义键。 */
  public String statusMapKey() {
    return statusMapKey;
  }

  public boolean isTerminal() {
    return terminal;
  }

  /**
   * 判断业务生命周期转换是否合法。相同状态用于至少一次事件重投，视为幂等。
   */
  public boolean canTransitionTo(ApprovalBusinessStatus target) {
    if (target == null) {
      return false;
    }
    if (this == target) {
      return true;
    }
    return switch (this) {
      case DRAFT -> in(target, SUBMITTED);
      case SUBMITTED -> in(target, DRAFT, PENDING);
      case PENDING -> in(target, DRAFT, PROCESSING, CANCELED);
      case PROCESSING -> in(target, SUSPENDED, WITHDRAWN, APPROVED, VETO, CANCELED, TERMINATED);
      case SUSPENDED -> in(target, PROCESSING, WITHDRAWN, CANCELED, TERMINATED);
      case WITHDRAWN -> in(target, DRAFT, SUBMITTED, PENDING);
      case APPROVED, VETO, CANCELED, TERMINATED -> false;
    };
  }

  private static boolean in(ApprovalBusinessStatus target, ApprovalBusinessStatus... candidates) {
    return Arrays.stream(candidates).anyMatch(candidate -> candidate == target);
  }
}
