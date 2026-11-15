package com.ouroboros.workflow.approval.definition.candidates;

import com.ouroboros.workflow.approval.definition.Candidate;

public class StarterCandidate extends Candidate {
  Boolean isChecked;


  public StarterCandidate() {
    super();
  }

  @Override
  public String getType() {
    return "Starter";
  }

  public Boolean getIsChecked() {
    return isChecked;
  }

  public void setIsChecked(boolean checked) {
    this.isChecked = checked;
  }
}
