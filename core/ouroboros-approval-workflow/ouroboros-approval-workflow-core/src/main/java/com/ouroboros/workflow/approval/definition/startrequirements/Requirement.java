package com.ouroboros.workflow.approval.definition.startrequirements;

/**
 * 单个自选节点的发起需求（设计文档 L659，字段名一一对应，禁止改名）。
 *
 * @param nodeId         自选节点 ID（发起提交时 taskAssignee.{nodeId} 的键）
 * @param label          节点展示名
 * @param mode           选择模式："single"/"multiple"（与 {@code UserSelectCandidate.Mode} 字符串一致）
 * @param min            最少选择人数（可空=不限）
 * @param max            最多选择人数（可空=不限）
 * @param candidateRange 选择范围类型（可空=不限制）
 * @param allowOverride  是否允许发起人覆盖该节点的审批人（false 时不返回该项，前端不渲染选择器，L661）
 */
public record Requirement(
    String nodeId,
    String label,
    String mode,
    Integer min,
    Integer max,
    CandidateRange candidateRange,
    boolean allowOverride) {
}
