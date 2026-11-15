package com.ouroboros.workflow.approval;

import java.util.List;

import com.ouroboros.workflow.approval.definition.Candidate;

public interface CandidateUserResolver {
  //TODO: 要不要传入流程实例ID和任务实例ID，以及任务名称？
  //      以实现更复杂的共存关系？
  Boolean support(Candidate candidate);

  default List<String> resolve(Candidate candidate, String processInstanceId, String taskInstanceId, String taskName) {
    return resolve(candidate);
  }

  List<String> resolve(Candidate candidate);
}
