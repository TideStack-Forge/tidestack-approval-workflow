package com.ouroboros.workflow.approval.event;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ouroboros.data.service.SimpleModelServiceImpl;
import com.ouroboros.common.service.outbox.OutboxBackoff;
import com.ouroboros.common.service.outbox.OutboxLease;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.exception.OuroborosException;
import com.ouroboros.workflow.approval.ApprovalWorkflowErrorCodes;
import com.ouroboros.workflow.approval.model.events.FlowInstanceEvent;
import com.ouroboros.workflow.approval.model.events.FlowTaskEvent;

/**
 * 统一事件 outbox 服务（Phase 4 M24，可靠发布）。
 *
 * <p>入队（同事务落库）→ after-commit 投递 → 成功 markSuccess / 失败 markFailure
 * （指数退避 2^retry 分钟上限 60、超 {@link FlowEventOutbox#MAX_RETRY_COUNT} → DEAD）→
 * {@link FlowEventRetryJob} 到期重投。模式照抄镜像 outbox（{@code ExternalMirrorOutboxService}）。
 * 投递为 at-least-once 语义，消费方幂等由既有机制（inbox eventId 幂等、业务回调幂等）保证。
 */
@Service
public class FlowEventOutboxService extends SimpleModelServiceImpl<String, FlowEventOutbox> {

  @org.springframework.beans.factory.annotation.Autowired(required = false)
  private FlowBusinessNotificationOwnership notificationOwnership;

  private static final Logger logger = LoggerFactory.getLogger(FlowEventOutboxService.class);

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
      .registerModule(new JavaTimeModule())
      .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

  /** 恢复扫描的单次数据库读取上限，避免异常调用者把 limit 放大成全表读取。 */
  private static final int MAX_RECOVERY_SCAN_LIMIT = 1_000;

  public record Claim(FlowEventOutbox outbox, String claimToken) {
  }

  /** A Spring payload alone is not source proof. Verify it against the committed canonical Outbox fact. */
  public void requirePersistedFact(FlowInstanceEvent event) {
    FlowEventOutbox stored = requireFact(event.getEventFactId(), FlowEventOutbox.EVENT_KIND_INSTANCE);
    var canonical = fromInstanceJson(stored)
        .orElseThrow(() -> new BusinessException("审批实例源事实无法读取"));
    requireSamePayload(canonical, event);
  }

  public void requirePersistedFact(FlowTaskEvent event) {
    FlowEventOutbox stored = requireFact(event.getEventFactId(), FlowEventOutbox.EVENT_KIND_TASK);
    var canonical = fromTaskJson(stored)
        .orElseThrow(() -> new BusinessException("审批任务源事实无法读取"));
    requireSamePayload(canonical, event);
  }

  private FlowEventOutbox requireFact(String factId, String kind) {
    if (factId == null || factId.isBlank()) { throw new BusinessException("审批源事实身份缺失"); }
    var stored = findById(factId).orElseThrow(() -> new BusinessException("审批源事实尚未持久化"));
    if (!kind.equals(stored.getEventKind())) { throw new BusinessException("审批源事实种类不匹配"); }
    return stored;
  }

  private void requireSamePayload(Object canonical, Object delivered) {
    if (!OBJECT_MAPPER.valueToTree(canonical).equals(OBJECT_MAPPER.valueToTree(delivered))) {
      throw new BusinessException("审批交付载荷与持久源事实不一致");
    }
  }

  /** 入队实例事件；每次调用对应一个独立事件事实。 */
  public FlowEventOutbox enqueue(FlowInstanceEvent event) {
    if (Integer.valueOf(1).equals(event.getProjectionVersion())) {
      if (event.getEventTime() == null) { event.setEventTime(LocalDateTime.now()); }
      if (event.getOccurredAt() == null) { event.setOccurredAt(event.getEventTime().atZone(java.time.ZoneId.systemDefault()).toInstant()); }
    }
    if (Integer.valueOf(1).equals(event.getProjectionVersion())) {
      var stateModel = com.ouroboros.data.model.TypedDataModelCenter.getDataModel(String.class, FlowInstanceFactState.class)
          .orElseThrow(() -> new BusinessException("审批实例事实版本模型不可用")).getDataModel();
      Object stateResource = stateModel.getAdapter().requireTransactionResource()
          .getOrElseThrow(error -> new BusinessException("审批事实版本未参与实际源事务", error));
      Object outboxResource = getDataModel().getDataModel().getAdapter().requireTransactionResource()
          .getOrElseThrow(error -> new BusinessException("审批 Outbox 未参与实际源事务", error));
      if (stateResource != outboxResource) {
        throw new BusinessException("审批事实版本与 Outbox 不属于同一提交资源");
      }
      if (event.getApprovalCaseId() != null) {
        var caseModel = com.ouroboros.data.model.TypedDataModelCenter.getDataModel(String.class,
            com.ouroboros.workflow.approval.lifecycle.ApprovalCase.class)
            .orElseThrow(() -> new BusinessException("审批案件模型不可用")).getDataModel();
        Object caseResource = caseModel.getAdapter().requireTransactionResource()
            .getOrElseThrow(error -> new BusinessException("审批案件未参与实际源事务", error));
        if (caseResource != outboxResource) { throw new BusinessException("审批案件与源事实不属于同一提交资源"); }
      }
    }
    if (event.getEventFactId() == null || event.getEventFactId().isBlank()) {
      event.setEventFactId(UUID.randomUUID().toString());
    }
    String eventTypeName = event.getEventType() == null ? null : event.getEventType().name();
    if (event.getBusinessNotificationOwner() == null && notificationOwnership != null) {
      event.setBusinessNotificationOwner(notificationOwnership.capture(event).orElse(null));
    }
    return enqueue(event.getEventFactId(), FlowEventOutbox.EVENT_KIND_INSTANCE, eventTypeName, event.getFlowKey(),
        event.getInstanceId(), null, toJson(event));
  }

  /** 入队任务事件；每次调用对应一个独立事件事实。 */
  public FlowEventOutbox enqueue(FlowTaskEvent event) {
    if (Integer.valueOf(1).equals(event.getProjectionVersion())) {
      if (event.getEventTime() == null) { event.setEventTime(LocalDateTime.now()); }
      if (event.getOccurredAt() == null) { event.setOccurredAt(event.getEventTime().atZone(java.time.ZoneId.systemDefault()).toInstant()); }
    }
    if (event.getEventFactId() == null || event.getEventFactId().isBlank()) {
      event.setEventFactId(UUID.randomUUID().toString());
    }
    String eventTypeName = event.getFactKind() == FlowTaskEvent.FactKind.ACTION_COMPLETED
        ? FlowTaskEvent.FactKind.ACTION_COMPLETED.name() : event.getState() == null ? null : event.getState().name();
    if (event.getBusinessNotificationOwner() == null && notificationOwnership != null) {
      event.setBusinessNotificationOwner(notificationOwnership.capture(event).orElse(null));
    }
    return enqueue(event.getEventFactId(), FlowEventOutbox.EVENT_KIND_TASK, eventTypeName, event.getFlowKey(),
        event.getInstanceId(), event.getTaskId(), toJson(event));
  }

  private FlowEventOutbox enqueue(
      String eventFactId,
      String eventKind,
      String eventTypeName,
      String flowKey,
      String instanceId,
      String taskId,
      String eventJson) {
    FlowEventOutbox outbox = new FlowEventOutbox();
    outbox.setId(eventFactId);
    outbox.setEventFactId(eventFactId);
    outbox.setEventKind(eventKind);
    outbox.setEventType(eventTypeName);
    outbox.setEventJson(eventJson);
    outbox.setFlowKey(flowKey);
    outbox.setInstanceId(instanceId);
    outbox.setTaskId(taskId);
    outbox.setStatus(FlowEventOutbox.STATUS_PENDING);
    outbox.setRetryCount(0);
    outbox.setClaimOwner(null);
    outbox.setClaimToken(null);
    outbox.setClaimUntil(null);
    LocalDateTime now = LocalDateTime.now();
    outbox.setCreatedAt(now);
    outbox.setUpdatedAt(now);
    var insertResult = getDataModel().insert(outbox);
    if (insertResult.isSuccess()) {
      return insertResult.get();
    }
    Throwable cause = insertResult.getCause();
    if (isUniqueConstraintViolation(cause)) {
      Optional<FlowEventOutbox> existing = findById(eventFactId);
      if (existing.isEmpty()) {
        throw new BusinessException("统一事件事实唯一键冲突但回查失败: " + eventFactId, cause);
      }
      if (!sameFact(existing.get(), outbox)) {
        throw new BusinessException("[" + ApprovalWorkflowErrorCodes.WORKFLOW_EVENT_FACT_CONFLICT
            + "] 统一事件事实身份冲突: " + eventFactId, cause);
      }
      return existing.get();
    }
    throw new BusinessException("统一事件 outbox 入队失败: " + eventFactId, cause);
  }

  private boolean sameFact(FlowEventOutbox existing, FlowEventOutbox requested) {
    return java.util.Objects.equals(existing.getEventFactId(), requested.getEventFactId())
        && java.util.Objects.equals(existing.getEventKind(), requested.getEventKind())
        && java.util.Objects.equals(existing.getEventType(), requested.getEventType())
        && java.util.Objects.equals(existing.getEventJson(), requested.getEventJson())
        && java.util.Objects.equals(existing.getFlowKey(), requested.getFlowKey())
        && java.util.Objects.equals(existing.getInstanceId(), requested.getInstanceId())
        && java.util.Objects.equals(existing.getTaskId(), requested.getTaskId());
  }

  private boolean isUniqueConstraintViolation(Throwable throwable) {
    Throwable cursor = throwable;
    while (cursor != null) {
      if (cursor instanceof com.ouroboros.data.exception.UniqueConstraintViolationException) {
        return true;
      }
      cursor = cursor.getCause();
    }
    return false;
  }

  /**
   * 原子领取一个统一事件 outbox 条目。PENDING 可立即领取；FAILED 需到期；PROCESSING 需 lease 过期。
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Optional<Claim> claim(String id, String owner, int leaseSeconds) {
    if (owner == null || owner.isBlank()) {
      throw new BusinessException("统一事件 outbox claim owner 不能为空");
    }
    Optional<FlowEventOutbox> outboxOpt = findById(id);
    if (outboxOpt.isEmpty()) {
      return Optional.empty();
    }
    LocalDateTime now = LocalDateTime.now();
    FlowEventOutbox outbox = outboxOpt.get();
    if (!isClaimable(outbox, now)) {
      return Optional.empty();
    }
    String claimToken = UUID.randomUUID().toString();
    LocalDateTime claimUntil = now.plusSeconds(Math.max(leaseSeconds, 1));
    Map<String, Object> where = new LinkedHashMap<>();
    where.put("id", Map.of("=", id));
    if (FlowEventOutbox.STATUS_PENDING.equals(outbox.getStatus())) {
      where.put("status", Map.of("=", FlowEventOutbox.STATUS_PENDING));
    } else if (FlowEventOutbox.STATUS_FAILED.equals(outbox.getStatus())) {
      where.put("status", Map.of("=", FlowEventOutbox.STATUS_FAILED));
    } else if (FlowEventOutbox.STATUS_PROCESSING.equals(outbox.getStatus())) {
      where.put("status", Map.of("=", FlowEventOutbox.STATUS_PROCESSING));
      where.put("claimUntil", Map.of("<", now));
    }
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("status", FlowEventOutbox.STATUS_PROCESSING);
    data.put("claimOwner", owner);
    data.put("claimToken", claimToken);
    data.put("claimUntil", claimUntil);
    data.put("lastError", null);
    data.put("updatedAt", now);
    if (!conditionalUpdate(where, data, "领取统一事件 outbox 条目失败: " + id)) {
      return Optional.empty();
    }
    return findById(id).map(claimed -> new Claim(claimed, claimToken));
  }

  /**
   * 领取当前可投递条目：PENDING、到期 FAILED，以及 lease 过期 PROCESSING。
   */
  public List<Claim> claimDue(int limit, String owner, int leaseSeconds) {
    int size = Math.max(limit, 0);
    List<Claim> claimed = new ArrayList<>();
    for (FlowEventOutbox candidate : findClaimableCandidates(size)) {
      if (claimed.size() >= size) {
        break;
      }
      claim(candidate.getId(), owner, leaseSeconds).ifPresent(claimed::add);
    }
    return claimed;
  }

  /**
   * 查询可领取候选：先取"已经到期该重投的"（lease 过期 PROCESSING），再取 FAILED，最后取 PENDING。
   *
   * <p>此前三类状态各做一次**无时间条件、无排序、无 limit** 的 searchList，把全表拉进内存后才
   * {@code filter(...).limit(limit)}：一是 PENDING 堆积时，排在后面的 FAILED/过期 PROCESSING
   * 永远进不了前 limit 个候选，重投被活活饿死；二是每次 claim 都要把整个 outbox 表读进 JVM。
   * 现在把"lease 已过期"这个必然带值的时间条件下推到 WHERE，并把 PENDING 放到最后，
   * 保证补投优先级高于新事件。
   *
   * <p>FAILED 的时间条件使用 {@code nextAttemptAt <= now OR nextAttemptAt IS NULL}，兼顾立即重投的
   * 空值语义；重试上限也在数据库侧过滤，到期判定仍由 {@code isClaimable} 统一负责。
   */
  private List<FlowEventOutbox> findClaimableCandidates(int limit) {
    int scanLimit = Math.min(Math.max(limit, 0), MAX_RECOVERY_SCAN_LIMIT);
    if (scanLimit == 0) {
      return List.of();
    }
    LocalDateTime now = LocalDateTime.now();
    List<FlowEventOutbox> candidates = new ArrayList<>();
    try {
      // 保持 PROCESSING → FAILED → PENDING 的恢复优先级；每类都在数据库侧限流，
      // 以便前一类被并发 worker 抢空后仍能从后一类补足本轮 batch。
      candidates.addAll((List<FlowEventOutbox>) searchList(candidateStatement(
          processingWhere(now), orderBy("claimUntil", "createdAt", "id"), scanLimit)));
      candidates.addAll((List<FlowEventOutbox>) searchList(candidateStatement(
          failedWhere(now), orderBy("nextAttemptAt", "createdAt", "id"), scanLimit)));
      candidates.addAll((List<FlowEventOutbox>) searchList(candidateStatement(
          Map.of("status", Map.of("=", FlowEventOutbox.STATUS_PENDING)),
          orderBy("createdAt", "id"), scanLimit)));
      return candidates.stream()
          .filter(outbox -> isClaimable(outbox, now))
          .limit(scanLimit)
          .toList();
    } catch (OuroborosException ex) {
      throw new BusinessException("查询可领取统一事件 outbox 失败", ex);
    }
  }

  private Map<String, Object> processingWhere(LocalDateTime now) {
    return Map.of(
        "status", Map.of("=", FlowEventOutbox.STATUS_PROCESSING),
        "claimUntil", Map.of("<=", now));
  }

  private Map<String, Object> failedWhere(LocalDateTime now) {
    Map<String, Object> where = new LinkedHashMap<>();
    where.put("status", Map.of("=", FlowEventOutbox.STATUS_FAILED));
    where.put("retryCount", Map.of("<=", FlowEventOutbox.MAX_RETRY_COUNT));
    Map<String, Object> due = new LinkedHashMap<>();
    due.put("nextAttemptAt", Map.of("<=", now));
    Map<String, Object> immediate = new LinkedHashMap<>();
    immediate.put("nextAttemptAt", null);
    where.put("$or", List.of(due, immediate));
    return where;
  }

  private Map<String, String> orderBy(String... fields) {
    Map<String, String> order = new LinkedHashMap<>();
    for (String field : fields) {
      order.put(field, "asc");
    }
    return order;
  }

  private Map<String, Object> candidateStatement(
      Map<String, Object> where, Map<String, String> order, int limit) {
    Map<String, Object> statement = new LinkedHashMap<>();
    statement.put("WHERE", where);
    statement.put("ORDER", order);
    statement.put("LIMIT", limit);
    return statement;
  }

  private boolean isClaimable(FlowEventOutbox outbox, LocalDateTime now) {
    return OutboxLease.isClaimable(outbox.getStatus(), outbox.getRetryCount(), outbox.getNextAttemptAt(),
        outbox.getClaimToken(), outbox.getClaimUntil(), FlowEventOutbox.MAX_RETRY_COUNT, now);
  }

  /**
   * 投递成功：条目置 SUCCESS。
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public boolean markSuccess(String id, String claimToken) {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("status", FlowEventOutbox.STATUS_SUCCESS);
    data.put("claimOwner", null);
    data.put("claimToken", null);
    data.put("claimUntil", null);
    data.put("nextAttemptAt", null);
    data.put("updatedAt", LocalDateTime.now());
    return updateClaimed(id, claimToken, data);
  }

  /**
   * 投递失败：重试计数 + 指数退避（2^retry 分钟，上限 60 分钟）；超过上限进入死信。
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public boolean markFailure(String id, String claimToken, String error) {
    Optional<FlowEventOutbox> outboxOpt = findById(id);
    if (outboxOpt.isEmpty()
        || !FlowEventOutbox.STATUS_PROCESSING.equals(outboxOpt.get().getStatus())
        || claimToken == null
        || !claimToken.equals(outboxOpt.get().getClaimToken())) {
      return false;
    }
    FlowEventOutbox outbox = outboxOpt.get();
    int retryCount = outbox.getRetryCount() == null ? 0 : outbox.getRetryCount();
    int next = retryCount + 1;
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("retryCount", next);
    data.put("lastError", OutboxBackoff.truncateError(error));
    if (OutboxBackoff.isDead(next, FlowEventOutbox.MAX_RETRY_COUNT)) {
      data.put("status", FlowEventOutbox.STATUS_DEAD);
      data.put("nextAttemptAt", null);
    } else {
      data.put("status", FlowEventOutbox.STATUS_FAILED);
      data.put("nextAttemptAt", OutboxBackoff.nextAttemptAt(retryCount));
    }
    data.put("claimOwner", null);
    data.put("claimToken", null);
    data.put("claimUntil", null);
    data.put("updatedAt", LocalDateTime.now());
    return updateClaimed(id, claimToken, data);
  }

  private boolean updateClaimed(String id, String claimToken, Map<String, Object> data) {
    Map<String, Object> where = new LinkedHashMap<>();
    where.put("id", Map.of("=", id));
    where.put("status", Map.of("=", FlowEventOutbox.STATUS_PROCESSING));
    where.put("claimToken", Map.of("=", claimToken));
    return conditionalUpdate(where, data, "更新统一事件 outbox claim 状态失败: " + id);
  }

  private boolean conditionalUpdate(Map<String, Object> where, Map<String, Object> data, String message) {
    try {
      var result = getDataModel().update(where, data);
      if (result.isFailure()) {
        throw new BusinessException(message, result.getCause());
      }
      Long rows = result.get();
      return rows != null && rows > 0;
    } catch (OuroborosException ex) {
      throw new BusinessException(message, ex);
    }
  }

  /**
   * 按 ID 查询条目；不存在返回空，查询失败明确抛业务异常。
   */
  public Optional<FlowEventOutbox> findById(String id) {
    var result = getDataModel().get(id, Map.of());
    if (result.isSuccess()) {
      return Optional.ofNullable(result.get());
    }
    throw new BusinessException("查询统一事件 outbox 失败: id=" + id, result.getCause());
  }

  private String toJson(Object event) {
    try {
      return OBJECT_MAPPER.writeValueAsString(event);
    } catch (Exception ex) {
      String instanceId = event instanceof FlowInstanceEvent instanceEvent
          ? instanceEvent.getInstanceId()
          : event instanceof FlowTaskEvent taskEvent ? taskEvent.getInstanceId() : null;
      throw new BusinessException("[" + ApprovalWorkflowErrorCodes.WORKFLOW_EVENT_SERIALIZATION_FAILED
          + "] 统一事件序列化失败: " + instanceId, ex);
    }
  }

  /** 反序列化实例事件；失败返回空（条目进入失败重试）。 */
  public Optional<FlowInstanceEvent> fromInstanceJson(FlowEventOutbox outbox) {
    try {
      return Optional.ofNullable(OBJECT_MAPPER.readValue(outbox.getEventJson(), FlowInstanceEvent.class));
    } catch (Exception ex) {
      logger.warn("统一事件反序列化失败: outboxId={}, cause={}", outbox.getId(), ex.getMessage());
      return Optional.empty();
    }
  }

  /** 反序列化任务事件；失败返回空（条目进入失败重试）。 */
  public Optional<FlowTaskEvent> fromTaskJson(FlowEventOutbox outbox) {
    try {
      return Optional.ofNullable(OBJECT_MAPPER.readValue(outbox.getEventJson(), FlowTaskEvent.class));
    } catch (Exception ex) {
      logger.warn("统一任务事件反序列化失败: outboxId={}, cause={}", outbox.getId(), ex.getMessage());
      return Optional.empty();
    }
  }
}
