package com.ouroboros.workflow.approval.semantics;

public enum JoinType {
  AND("AND"), OR("OR");

  private final String value;

  JoinType(String value) {
    this.value = value;
  }

  public static JoinType fromString(String joinType) {
    for (JoinType value : JoinType.values()) {
      if (value.value.equalsIgnoreCase(joinType)) {
        return value;
      }
    }
    return null;
  }

  @Override
  public String toString() {
    return value;
  }
}
