package com.ouroboros.workflow.approval.definition.startrequirements;

/**
 * 自选审批人选择范围类型（设计文档"自选审批人的统一契约与适配"L659）。
 *
 * <p>仅表达范围类型；具体条件值（如角色 ID/岗位 ID/部门 ID/表单字段名）的建模与
 * 成员资格校验数据源待接入（Phase 4 M22 偏差记录：AWF 无角色/组织数据源可依赖，
 * 且设计文档未定义条件值结构——接入点在 {@code UserSelectCandidate.candidateRange} javadoc 注明）。
 */
public enum CandidateRange {
  ROLE,
  POSITION,
  DEPARTMENT,
  FORM_FIELD
}
