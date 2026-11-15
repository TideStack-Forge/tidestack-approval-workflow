package com.ouroboros.workflow.approval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.workflow.approval.definition.candidates.UserSelectCandidate;
import com.ouroboros.workflow.approval.definition.nodes.ApprovalNode;
import com.ouroboros.workflow.approval.definition.nodes.SingleApprovalNode;

/**
 * M22-T2 自选审批人数量区间校验测试：SINGLE 超选拒绝、合规通过、MULTIPLE 不限、无候选不校验。
 */
class UserSelectAssigneeRangeCheckerTest {

  @Test
  void singleNodeWithTwoAssigneesIsRejected() {
    ApprovalNode node = node("approve-1", "经理审批", UserSelectCandidate.Mode.SINGLE);

    var failure = UserSelectAssigneeRangeChecker.checkSingle(
        List.of(node), Map.of("approve-1", List.of("u-1", "u-2")));

    assertThat(failure).isPresent();
    assertThat(failure.get().getMessage())
        .contains(ApprovalWorkflowErrorCodes.WORKFLOW_ASSIGNEE_OUT_OF_RANGE)
        .contains("经理审批")
        .contains("最多选择 1 人");
  }

  @Test
  void singleNodeWithOneAssigneePasses() {
    ApprovalNode node = node("approve-1", "经理审批", UserSelectCandidate.Mode.SINGLE);

    var failure = UserSelectAssigneeRangeChecker.checkSingle(
        List.of(node), Map.of("approve-1", List.of("u-1")));

    assertThat(failure).isEmpty();
  }

  @Test
  void multipleNodeWithManyAssigneesPasses() {
    ApprovalNode node = node("approve-2", "会签", UserSelectCandidate.Mode.MULTIPLE);

    var failure = UserSelectAssigneeRangeChecker.checkSingle(
        List.of(node), Map.of("approve-2", List.of("u-1", "u-2", "u-3")));

    assertThat(failure).isEmpty();
  }

  @Test
  void nodeWithoutUserSelectCandidateIsNotChecked() {
    ApprovalNode node = new SingleApprovalNode();
    node.setId("approve-3");
    node.setName("角色审批");
    node.setCandidates(List.of());

    var failure = UserSelectAssigneeRangeChecker.checkSingle(
        List.of(node), Map.of("approve-3", List.of("u-1", "u-2")));

    assertThat(failure).isEmpty();
  }

  private ApprovalNode node(String id, String name, UserSelectCandidate.Mode mode) {
    UserSelectCandidate candidate = new UserSelectCandidate();
    candidate.setMode(mode);
    SingleApprovalNode node = new SingleApprovalNode();
    node.setId(id);
    node.setName(name);
    node.setCandidates(List.of(candidate));
    return node;
  }
}
