package com.ouroboros.workflow.approval.candidate;

import java.util.List;

import com.ouroboros.workflow.approval.CandidateUserResolver;
import com.ouroboros.workflow.approval.definition.Candidate;
import com.ouroboros.workflow.approval.definition.candidates.UserCandidate;

public class UserCandidateResolver implements CandidateUserResolver {

  @Override
  public Boolean support(Candidate candidate) {
    return candidate instanceof UserCandidate;
  }

  @Override
  public List<String> resolve(Candidate candidate) {
    return ((UserCandidate) candidate).getUsers();
  }
}
