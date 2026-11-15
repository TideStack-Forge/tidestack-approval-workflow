package com.ouroboros.workflow.approval.definition.candidates;

import com.ouroboros.workflow.approval.definition.Candidate;

public class FormFieldCandidate extends Candidate {
  String field;

  public FormFieldCandidate() {
    super();
  }

  @Override
  public String getType() {
    return "FormField";
  }

  public String getField() {
    return field;
  }

  public void setField(String field) {
    this.field = field;
  }
}
