package com.ouroboros.workflow.approval.event;

/** Frozen in the canonical source fact; projectionVersion alone never transfers notification ownership. */
public record FlowBusinessNotificationOwner(int version, String modelFullName, String subscriptionName,
                                            String user, String ruleRevision) {
  public FlowBusinessNotificationOwner {
    if (version != 1 || blank(modelFullName) || blank(subscriptionName) || blank(user) || blank(ruleRevision)) {
      throw new IllegalArgumentException("业务通知责任标识无效");
    }
  }

  private static boolean blank(String value) { return value == null || value.isBlank(); }
}
