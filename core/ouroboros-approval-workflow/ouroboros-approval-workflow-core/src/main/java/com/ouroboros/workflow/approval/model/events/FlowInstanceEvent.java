package com.ouroboros.workflow.approval.model.events;

import java.time.LocalDateTime;

import com.ouroboros.workflow.approval.definition.FlowDefinition.Notification;

public class FlowInstanceEvent {
  private String eventFactId;
  private Integer projectionVersion;
  private com.ouroboros.workflow.approval.event.FlowBusinessNotificationOwner businessNotificationOwner;

  @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
  public com.ouroboros.workflow.approval.event.FlowBusinessNotificationOwner getBusinessNotificationOwner() { return businessNotificationOwner; }
  public void setBusinessNotificationOwner(com.ouroboros.workflow.approval.event.FlowBusinessNotificationOwner owner) { businessNotificationOwner = owner; }
  private Long stateSequence;
  String flowKey;
  String instanceId;
  EventType eventType;
  Notification notification;
  private LocalDateTime eventTime;
  private java.time.Instant occurredAt;
  private String starter;
  private String lastTaskId;
  private String outcome;

  /** 稳定审批案件 ID。 */
  private String approvalCaseId;

  /** 发起此执行的审批轮次。 */
  private Integer submissionRound;

  /** 业务记录定位（统一结果事件载荷）。 */
  private BusinessContext businessContext;

  /** 回调逻辑流绑定键（业务回调直连路由）。 */
  private String callbackFlow;

  /** 结果型（RESULT_ONLY）本地跟踪 ID。 */
  private String trackingId;

  /** 外部详情跳转地址（LINK 交互）。 */
  private String externalUrl;

  /**
   * 业务记录定位上下文。
   *
   * @param recordType 业务记录类型（业务模型全名）
   * @param recordId   业务记录主键
   */
  public record BusinessContext(String recordType, String recordId) {
  }

  public String getEventFactId() {
    return eventFactId;
  }

  public Integer getProjectionVersion() { return projectionVersion; }

  public void setProjectionVersion(Integer projectionVersion) { this.projectionVersion = projectionVersion; }

  public Long getStateSequence() { return stateSequence; }

  public void setStateSequence(Long stateSequence) { this.stateSequence = stateSequence; }

  public void setEventFactId(String eventFactId) {
    this.eventFactId = eventFactId;
  }

  public String getFlowKey() {
    return flowKey;
  }

  public void setFlowKey(String flowKey) {
    this.flowKey = flowKey;
  }

  public String getInstanceId() {
    return instanceId;
  }

  public void setInstanceId(String instanceId) {
    this.instanceId = instanceId;
  }

  public EventType getEventType() {
    return eventType;
  }

  public void setEventType(EventType eventType) {
    this.eventType = eventType;
  }

  public Notification getNotification() {
    return notification;
  }

  public void setNotification(Notification notification) {
    this.notification = notification;
  }

  public LocalDateTime getEventTime() {
    return eventTime;
  }

  public void setEventTime(LocalDateTime eventTime) {
    this.eventTime = eventTime;
  }

  public java.time.Instant getOccurredAt() { return occurredAt; }
  public void setOccurredAt(java.time.Instant occurredAt) { this.occurredAt = occurredAt; }

  public String getStarter() {
    return starter;
  }

  public void setStarter(String string) {
    this.starter = string;
  }

  public String getLastTaskId() {
    return lastTaskId;
  }

  public void setLastTaskId(String lastTaskId) {
    this.lastTaskId = lastTaskId;
  }

  public String getOutcome() {
    return outcome;
  }

  public void setOutcome(String outcome) {
    this.outcome = outcome;
  }

  public String getApprovalCaseId() {
    return approvalCaseId;
  }

  public void setApprovalCaseId(String approvalCaseId) {
    this.approvalCaseId = approvalCaseId;
  }

  public Integer getSubmissionRound() {
    return submissionRound;
  }

  public void setSubmissionRound(Integer submissionRound) {
    this.submissionRound = submissionRound;
  }

  public BusinessContext getBusinessContext() {
    return businessContext;
  }

  public void setBusinessContext(BusinessContext businessContext) {
    this.businessContext = businessContext;
  }

  public String getCallbackFlow() {
    return callbackFlow;
  }

  public void setCallbackFlow(String callbackFlow) {
    this.callbackFlow = callbackFlow;
  }

  public String getTrackingId() {
    return trackingId;
  }

  public void setTrackingId(String trackingId) {
    this.trackingId = trackingId;
  }

  public String getExternalUrl() {
    return externalUrl;
  }

  public void setExternalUrl(String externalUrl) {
    this.externalUrl = externalUrl;
  }

  public enum EventType {
    /**
     * 流程实例启动
     */
    STARTED,
    /**
     * 流程实例暂停
     */
    SUSPENDED,
    /**
     * 流程实例恢复
     */
    RESUMED,
    /**
     * 审批通过
     */
    APPROVED,
    /**
     * 审批拒绝
     */
    REJECTED,
    /**
     * 流程实例取消
     */
    CANCELLED,
    /**
     * 流程异常终态（外部引擎错误/异常结束）
     */
    FAILED
  }
}
