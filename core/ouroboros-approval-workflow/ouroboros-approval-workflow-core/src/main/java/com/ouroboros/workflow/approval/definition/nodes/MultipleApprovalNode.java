package com.ouroboros.workflow.approval.definition.nodes;

import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize()
public class MultipleApprovalNode extends ApprovalNode {
  String multipleMode;
  Integer percentage;
  String rejectAction;
  List<String> actions;

  public MultipleApprovalNode() {
    super();
    setSubType("会签");
  }

  @Override
  public String getSubType() {
    return "会签";
  }

  public String getMultipleMode() {
    return multipleMode;
  }

  public void setMultipleMode(String multipleMode) {
    this.multipleMode = multipleMode;
  }

  public Integer getPercentage() {
    return percentage;
  }

  public void setPercentage(Integer percentage) {
    this.percentage = percentage;
  }

  public String getRejectAction() {
    return rejectAction;
  }

  public void setRejectAction(String rejectAction) {
    this.rejectAction = rejectAction;
    if ((this.actions == null || this.actions.isEmpty()) && rejectAction != null) {
      this.actions = Collections.singletonList(rejectAction);
    }
  }

  @Override
  public List<String> getActions() {
    if (actions != null && !actions.isEmpty()) {
      return actions;
    }
    if (rejectAction == null) {
      return Collections.emptyList();
    }
    return Collections.singletonList(rejectAction);
  }

  @Override
  public void setActions(List<String> actions) {
    this.actions = actions;
  }
}
