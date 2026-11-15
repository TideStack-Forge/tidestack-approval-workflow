package com.ouroboros.workflow.approval.semantics;

public enum SplitType {
  AND("AND"), XOR("XOR"), OR("OR");

  private final String value;

  SplitType(String value) {
    this.value = value;
  }

  public static SplitType fromString(String splitType) {
    for (SplitType value : SplitType.values()) {
      if (value.value.equalsIgnoreCase(splitType)) {
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
