package com.ouroboros.workflow.approval.candidate;

import java.util.Collections;
import java.util.List;

import com.ouroboros.workflow.approval.CandidateUserResolver;
import com.ouroboros.workflow.approval.definition.Candidate;
import com.ouroboros.workflow.approval.definition.candidates.StarterCandidate;

// TODO: 考虑挪到 activity6 的模块中，通过查询流程实例的发起人来获取发起人信息，以减少硬编码
public class StarterCandidateResolver implements CandidateUserResolver {
  @Override
  public Boolean support(Candidate candidate) {
    return candidate instanceof StarterCandidate;
  }

  @Override
  public List<String> resolve(Candidate candidate) {
    return Collections.singletonList("${starter}");
  }
}
