package com.ouroboros.workflow.approval.flowable.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.ouroboros.workflow.approval.definition.candidates.StarterCandidate;
import com.ouroboros.workflow.approval.definition.candidates.UserCandidate;

class BpmnUtilCandidateUserResolverTest {
  @Test
  void getCandidateUsersResolvesConfiguredCandidatesThroughResolverChain() {
    UserCandidate userCandidate = new UserCandidate();
    userCandidate.setUsers(Arrays.asList("user-a", "user-b"));

    StarterCandidate starterCandidate = new StarterCandidate();
    starterCandidate.setIsChecked(true);

    List<String> users = BpmnUtil.getCandidateUsers(
        Arrays.asList(userCandidate, starterCandidate),
        "instance-1",
        "task-1",
        "经理审批");

    assertEquals(Arrays.asList("user-a", "user-b", "${starter}"), users);
  }
}
