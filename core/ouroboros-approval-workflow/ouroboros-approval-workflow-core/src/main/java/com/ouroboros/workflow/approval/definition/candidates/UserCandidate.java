package com.ouroboros.workflow.approval.definition.candidates;

import java.util.List;

import com.ouroboros.workflow.approval.definition.Candidate;

public class UserCandidate extends Candidate {
  List<String> users;


  public UserCandidate() {
    super();
  }

  @Override
  public String getType() {
    return "User";
  }

  public List<String> getUsers() {
    return users;
  }

  public void setUsers(List<String> users) {
    this.users = users;
  }
}
