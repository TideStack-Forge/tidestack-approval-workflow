package com.ouroboros.workflow.approval.event;

import java.time.LocalDateTime;

import com.ouroboros.data.annotation.Field;
import com.ouroboros.data.annotation.Model;
import com.ouroboros.data.annotation.PrimaryKey;
import com.ouroboros.data.annotation.UniqueConstraint;

/**
 * 统一结果事件 outbox（Phase 4 M24，可靠发布）。
 *
 * <p>与镜像 outbox（{@code ExternalMirrorOutbox}，外部 HTTP 命令补偿）相互独立：
 * 本实体是本地事件投递补偿（进程内监听器），入队/退避/死信模式与其一致。
 */
@Model(rawName = "t_approval_workflow_flow_event_outbox", label = "审批流统一事件 Outbox")
@UniqueConstraint(name = "uk_awf_flow_event_fact", fields = {"eventFactId"})
public class FlowEventOutbox {

  public static final String EVENT_KIND_INSTANCE = "INSTANCE";
  public static final String EVENT_KIND_TASK = "TASK";

  public static final String STATUS_PENDING = "PENDING";
  public static final String STATUS_PROCESSING = "PROCESSING";
  public static final String STATUS_SUCCESS = "SUCCESS";
  public static final String STATUS_FAILED = "FAILED";
  public static final String STATUS_DEAD = "DEAD";

  /** 重试上限（超过进入死信，模式同镜像 outbox）。 */
  public static final int MAX_RETRY_COUNT = 6;

  @Field
  @PrimaryKey
  private String id;

  /** 每次业务分发生成的稳定事件事实 ID；重投始终复用该 ID。 */
  @Field
  private String eventFactId;

  /** INSTANCE / TASK。 */
  @Field
  private String eventKind;

  /** 统一事件类型（EventType 枚举名；可空=事件未带类型）。 */
  @Field(nullable = true)
  private String eventType;

  /** 事件 payload 序列化（统一事件 JSON）。 */
  @Field(rawType = "longtext")
  private String eventJson;

  @Field
  private String flowKey;

  @Field
  private String instanceId;

  @Field(nullable = true)
  private String taskId;

  @Field
  private String status;

  @Field
  private Integer retryCount;

  @Field(nullable = true)
  private LocalDateTime nextAttemptAt;

  @Field(nullable = true)
  private String claimOwner;

  @Field(nullable = true)
  private String claimToken;

  @Field(nullable = true)
  private LocalDateTime claimUntil;

  @Field(rawType = "longtext", nullable = true)
  private String lastError;

  @Field
  private LocalDateTime createdAt;

  @Field
  private LocalDateTime updatedAt;

  public FlowEventOutbox() {
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getEventFactId() {
    return eventFactId;
  }

  public void setEventFactId(String eventFactId) {
    this.eventFactId = eventFactId;
  }

  public String getEventKind() {
    return eventKind;
  }

  public void setEventKind(String eventKind) {
    this.eventKind = eventKind;
  }

  public String getEventType() {
    return eventType;
  }

  public void setEventType(String eventType) {
    this.eventType = eventType;
  }

  public String getEventJson() {
    return eventJson;
  }

  public void setEventJson(String eventJson) {
    this.eventJson = eventJson;
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

  public String getTaskId() {
    return taskId;
  }

  public void setTaskId(String taskId) {
    this.taskId = taskId;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public Integer getRetryCount() {
    return retryCount;
  }

  public void setRetryCount(Integer retryCount) {
    this.retryCount = retryCount;
  }

  public LocalDateTime getNextAttemptAt() {
    return nextAttemptAt;
  }

  public void setNextAttemptAt(LocalDateTime nextAttemptAt) {
    this.nextAttemptAt = nextAttemptAt;
  }

  public String getClaimOwner() {
    return claimOwner;
  }

  public void setClaimOwner(String claimOwner) {
    this.claimOwner = claimOwner;
  }

  public String getClaimToken() {
    return claimToken;
  }

  public void setClaimToken(String claimToken) {
    this.claimToken = claimToken;
  }

  public LocalDateTime getClaimUntil() {
    return claimUntil;
  }

  public void setClaimUntil(LocalDateTime claimUntil) {
    this.claimUntil = claimUntil;
  }

  public String getLastError() {
    return lastError;
  }

  public void setLastError(String lastError) {
    this.lastError = lastError;
  }

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
