package com.ouroboros.workflow.approval.definition.nodes;

import com.ouroboros.workflow.approval.definition.FlowNode;

public class EndNode extends FlowNode {
  public static final String OUTCOME_APPROVED = "approved";
  public static final String OUTCOME_REJECTED = "rejected";

  String outcome;

  public EndNode() {
    super();
  }

  @Override
  public String getType() {
    return BuiltInFlowNodeTypes.END;
  }

  public String getOutcome() {
    return outcome;
  }

  public void setOutcome(String outcome) {
    this.outcome = outcome;
  }

  public String resolveOutcome() {
    return outcome == null || outcome.trim().isEmpty() ? OUTCOME_APPROVED : outcome;
  }
}
