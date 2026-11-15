package com.ouroboros.workflow.approval.definition.candidates;

import java.util.List;

import com.ouroboros.workflow.approval.definition.Candidate;

public class RoleCandidate extends Candidate {
  List<String> roles;

  public RoleCandidate() {
    super();
  }

  @Override
  public String getType() {
    return "Role";
  }

  public List<String> getRoles() {
    return roles;
  }

  public void setRoles(List<String> roles) {
    this.roles = roles;
  }
}
