package com.ouroboros.workflow.approval.definition.candidates;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.ouroboros.workflow.approval.definition.Candidate;
import com.ouroboros.workflow.approval.definition.startrequirements.CandidateRange;

public class UserSelectCandidate extends Candidate {
  Mode mode;
  CandidateRange candidateRange;
  boolean allowOverride;

  public UserSelectCandidate() {
    super();
  }

  @Override
  public String getType() {
    return "UserSelect";
  }

  public Mode getMode() {
    return mode;
  }

  public void setMode(Mode mode) {
    this.mode = mode;
  }

  /**
   * 自选审批人选择范围类型（设计 L662 范围约束；可空=不限制）。
   *
   * <p>Phase 4 M22 偏差：成员资格校验数据源未接入（AWF 无角色/组织数据源可依赖），
   * 当前仅"单选数量上限"由 {@code mode} 派生校验；ROLE/POSITION/DEPARTMENT/FORM_FIELD
   * 的条件值与数据源为预留接入点。
   */
  public CandidateRange getCandidateRange() {
    return candidateRange;
  }

  public void setCandidateRange(CandidateRange candidateRange) {
    this.candidateRange = candidateRange;
  }

  /**
   * 是否允许发起人覆盖该节点的审批人（设计 L634 定义级开关的节点级落地；缺省 false）。
   */
  public boolean isAllowOverride() {
    return allowOverride;
  }

  public void setAllowOverride(boolean allowOverride) {
    this.allowOverride = allowOverride;
  }

  /**
   * 解析候选列表中的第一个自选审批人候选；列表为空或无自选候选返回 null。
   *
   * @param candidates 节点候选列表（可空）
   * @return 第一个 {@code UserSelectCandidate} 实例，或 null
   */
  public static UserSelectCandidate resolve(List<Candidate> candidates) {
    if (candidates == null) {
      return null;
    }
    return candidates.stream()
        .filter(UserSelectCandidate.class::isInstance)
        .map(UserSelectCandidate.class::cast)
        .findFirst()
        .orElse(null);
  }

  public enum Mode {
    SINGLE("single"), MULTIPLE("multiple");
    final private String mode;

    Mode(String mode) {
      this.mode = mode;
    }


    @JsonCreator
    public static Mode fromString(String mode) {
      for (Mode m : Mode.values()) {
        if (m.mode.equalsIgnoreCase(mode)) {
          return m;
        }
      }
      return null;
    }

    @Override
    @JsonValue
    public String toString() {
      return mode;
    }
  }
}
