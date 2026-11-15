package com.ouroboros.workflow.approval;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.workflow.approval.definition.candidates.UserSelectCandidate;
import com.ouroboros.workflow.approval.definition.nodes.ApprovalNode;

/**
 * 自选审批人数量区间校验（Phase 4 M22）。
 *
 * <p>由 flowable/activiti6 两引擎的启动校验共用（避免双份实现漂移）：SINGLE 节点选择超过 1 人
 * → 拒绝（错误码 {@link ApprovalWorkflowErrorCodes#WORKFLOW_ASSIGNEE_OUT_OF_RANGE}）。
 * candidateRange 成员资格校验（ROLE/POSITION/DEPARTMENT/FORM_FIELD）数据源未接入，
 * 见 {@code UserSelectCandidate.candidateRange} javadoc（Phase 4 M22 偏差）。
 */
public final class UserSelectAssigneeRangeChecker {

  private UserSelectAssigneeRangeChecker() {
  }

  /**
   * 校验自选节点的数量区间；违规返回对应 {@link BusinessException}，合规返回空。
   */
  public static Optional<BusinessException> checkSingle(
      List<ApprovalNode> nodes, Map<String, List<String>> assigneeMap) {
    for (ApprovalNode node : nodes) {
      UserSelectCandidate candidate = UserSelectCandidate.resolve(node.getCandidates());
      if (candidate == null || candidate.getMode() != UserSelectCandidate.Mode.SINGLE) {
        continue;
      }
      List<String> selected = assigneeMap.get(node.getId());
      if (selected != null && selected.size() > 1) {
        return Optional.of(new BusinessException(
            "[" + ApprovalWorkflowErrorCodes.WORKFLOW_ASSIGNEE_OUT_OF_RANGE
                + "] 节点：【" + node.getName() + "】 为单选节点，最多选择 1 人"));
      }
    }
    return Optional.empty();
  }
}
