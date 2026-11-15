package com.ouroboros.workflow.approval.definition.candidates;

import com.ouroboros.workflow.approval.definition.CandidateSubClassResolver;

public class BuildInCandidateSubClassResolver implements CandidateSubClassResolver {
  @Override
  public Class<?> resolve(String type) {
    return switch (type) {
      case "User" -> UserCandidate.class;
      case "Role" -> RoleCandidate.class;
      case "Position" -> PositionCandidate.class;
      case "UserSelect" -> UserSelectCandidate.class;
      case "Starter" -> StarterCandidate.class;
      case "FormField" -> FormFieldCandidate.class;
      default -> null;
    };
  }
}
