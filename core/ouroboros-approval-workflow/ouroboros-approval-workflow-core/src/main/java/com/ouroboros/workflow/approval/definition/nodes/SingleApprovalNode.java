package com.ouroboros.workflow.approval.definition.nodes;

import java.util.List;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize()
public class SingleApprovalNode extends ApprovalNode {
  List<String> actions;

  public SingleApprovalNode() {
    super();
    setSubType("单签");
  }

  @Override
  public String getSubType() {
    return "单签";
  }

  @Override
  public List<String> getActions() {
    return actions;
  }

  @Override
  public void setActions(List<String> actions) {
    this.actions = actions;
  }
}
