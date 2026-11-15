package com.ouroboros.workflow.approval.definition;

public interface CandidateSubClassResolver {
  Class<?> resolve(String type);
}
