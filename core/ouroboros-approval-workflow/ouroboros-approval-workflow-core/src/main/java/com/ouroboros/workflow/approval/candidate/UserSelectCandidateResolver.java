package com.ouroboros.workflow.approval.candidate;

import java.util.Collections;
import java.util.List;

import com.ouroboros.workflow.approval.CandidateUserResolver;
import com.ouroboros.workflow.approval.definition.Candidate;
import com.ouroboros.workflow.approval.definition.candidates.UserSelectCandidate;

public class UserSelectCandidateResolver implements CandidateUserResolver {
  @Override
  public Boolean support(Candidate candidate) {
    return candidate instanceof UserSelectCandidate;
  }

  @Override
  public List<String> resolve(Candidate candidate) {
    return Collections.emptyList();
  }
}
