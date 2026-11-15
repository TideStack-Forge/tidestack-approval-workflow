package com.ouroboros.workflow.approval.lifecycle;

import java.time.LocalDateTime;

import com.ouroboros.data.annotation.Field;
import com.ouroboros.data.annotation.Model;
import com.ouroboros.data.annotation.PrimaryKey;
import com.ouroboros.data.annotation.UniqueConstraint;

/**
 * 一个业务记录在某个流程定义上的稳定审批案件。
 *
 * <p>每次重新提交只增加 {@link #submissionRound} 并绑定新的 executionId；任何已取消的
 * 引擎实例都不会被复活或复用。
 */
@Model(rawName = "t_approval_workflow_case", label = "审批案件")
@UniqueConstraint(name = "uk_awf_case_route", fields = {"caseRouteKey"})
public class ApprovalCase {

  @Field
  @PrimaryKey
  private String approvalCaseId;

  /** 业务记录类型 + 记录 ID + flowKey 的不可逆路由摘要。 */
  @Field(size = 96)
  private String caseRouteKey;

  @Field
  private String businessRecordType;

  @Field
  private String businessRecordId;

  @Field
  private String flowKey;

  @Field
  private Integer submissionRound;

  /** 当前轮次的本地或外部执行 ID；终态时清空。 */
  @Field(nullable = true)
  private String activeExecutionId;

  /** {@link ApprovalBusinessStatus} 枚举名。 */
  @Field
  private String businessStatus;

  /** {@link ApprovalTechnicalState} 枚举名。 */
  @Field
  private String technicalState;

  @Field(rawType = "longtext", nullable = true)
  private String technicalFailureReason;

  /** 最近一次应用的规范实例事件事实 ID，供重投诊断。 */
  @Field(nullable = true)
  private String lastEventFactId;

  /** Source-owned version; old rows have no baseline until verified against the engine. */
  @Field(nullable = true)
  private Long lastStateSequence;

  /** Retained after terminal activeExecutionId is cleared; old facts cannot reclaim the round. */
  @Field(nullable = true)
  private String lastStateInstanceId;

  @Field
  private LocalDateTime createdAt;

  @Field
  private LocalDateTime updatedAt;

  public String getApprovalCaseId() {
    return approvalCaseId;
  }

  public void setApprovalCaseId(String approvalCaseId) {
    this.approvalCaseId = approvalCaseId;
  }

  public String getCaseRouteKey() {
    return caseRouteKey;
  }

  public void setCaseRouteKey(String caseRouteKey) {
    this.caseRouteKey = caseRouteKey;
  }

  public String getBusinessRecordType() {
    return businessRecordType;
  }

  public void setBusinessRecordType(String businessRecordType) {
    this.businessRecordType = businessRecordType;
  }

  public String getBusinessRecordId() {
    return businessRecordId;
  }

  public void setBusinessRecordId(String businessRecordId) {
    this.businessRecordId = businessRecordId;
  }

  public String getFlowKey() {
    return flowKey;
  }

  public void setFlowKey(String flowKey) {
    this.flowKey = flowKey;
  }

  public Integer getSubmissionRound() {
    return submissionRound;
  }

  public void setSubmissionRound(Integer submissionRound) {
    this.submissionRound = submissionRound;
  }

  public String getActiveExecutionId() {
    return activeExecutionId;
  }

  public void setActiveExecutionId(String activeExecutionId) {
    this.activeExecutionId = activeExecutionId;
  }

  public String getBusinessStatus() {
    return businessStatus;
  }

  public void setBusinessStatus(String businessStatus) {
    this.businessStatus = businessStatus;
  }

  public String getTechnicalState() {
    return technicalState;
  }

  public void setTechnicalState(String technicalState) {
    this.technicalState = technicalState;
  }

  public String getTechnicalFailureReason() {
    return technicalFailureReason;
  }

  public void setTechnicalFailureReason(String technicalFailureReason) {
    this.technicalFailureReason = technicalFailureReason;
  }

  public String getLastEventFactId() {
    return lastEventFactId;
  }

  public void setLastEventFactId(String lastEventFactId) {
    this.lastEventFactId = lastEventFactId;
  }

  public Long getLastStateSequence() { return lastStateSequence; }
  public void setLastStateSequence(Long lastStateSequence) { this.lastStateSequence = lastStateSequence; }
  public String getLastStateInstanceId() { return lastStateInstanceId; }
  public void setLastStateInstanceId(String lastStateInstanceId) { this.lastStateInstanceId = lastStateInstanceId; }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(LocalDateTime updatedAt) {
    this.updatedAt = updatedAt;
  }
}
