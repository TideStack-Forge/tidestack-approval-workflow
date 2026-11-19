package com.ouroboros.workflow.approval.lifecycle;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ouroboros.data.service.SimpleModelServiceImpl;
import com.ouroboros.data.exception.UniqueConstraintViolationException;
import com.ouroboros.data.dsl.Operators;
import com.ouroboros.data.dsl.SExpression;
import com.ouroboros.data.dsl.statement.QueryStatement;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.exception.OuroborosException;
import com.ouroboros.workflow.approval.ApprovalWorkflowErrorCodes;
import com.ouroboros.workflow.approval.ApprovalInstanceState;
import com.ouroboros.workflow.approval.model.events.FlowInstanceEvent;

/**
 * 审批案件生命周期账本。
 *
 * <p>案件稳定地绑定业务记录和 flowKey；执行 ID 只属于一个 submission round。该服务不写业务模型
 * 的状态字段，业务模型投影仍根据 {@link ApprovalBusinessStatus#statusMapKey()} 查找其自定义 code。
 */
@Service
public class ApprovalCaseService extends SimpleModelServiceImpl<String, ApprovalCase> {

  @org.springframework.beans.factory.annotation.Autowired(required = false)
  private org.springframework.beans.factory.ObjectProvider<com.ouroboros.workflow.approval.event.ApprovalBusinessFactProjection> businessProjections;

  private static final int MAX_CAS_ATTEMPTS = 3;

  /** 已保留的执行轮次；调用方必须把两项内容写入启动变量。 */
  public record Submission(String approvalCaseId, int submissionRound) {
  }

  /**
   * 为一次启动保留案件轮次。首次启动创建 round 1；仅撤回或已知启动失败回到 DRAFT 后可创建新轮次。
   */
  @Transactional
  public Submission reserveSubmission(String businessRecordType, String businessRecordId, String flowKey) {
    CaseIdentity identity = CaseIdentity.of(businessRecordType, businessRecordId, flowKey);
    Optional<ApprovalCase> existing = findByRouteKey(identity.routeKey());
    if (existing.isPresent()) {
      return reserveExisting(existing.get());
    }
    ApprovalCase created = newCase(identity);
    var result = getDataModel().insert(created);
    if (result.isSuccess()) {
      return new Submission(created.getApprovalCaseId(), 1);
    }
    if (!isUniqueConstraintViolation(result.getCause())) {
      throw new BusinessException("创建审批案件失败: " + identity.flowKey(), result.getCause());
    }
    ApprovalCase concurrent = findByRouteKey(identity.routeKey())
        .orElseThrow(() -> new BusinessException("审批案件唯一键冲突但回查失败: " + identity.flowKey()));
    return reserveExisting(concurrent);
  }

  /** 把已保留轮次关联到新创建的本地或外部执行。 */
  @Transactional
  public void bindExecution(String approvalCaseId, int submissionRound, String executionId) {
    requireText(approvalCaseId, "approvalCaseId");
    requireText(executionId, "executionId");
    for (int attempt = 0; attempt < MAX_CAS_ATTEMPTS; attempt++) {
      ApprovalCase approvalCase = getRequired(approvalCaseId);
      requireRound(approvalCase, submissionRound);
      if (executionId.equals(approvalCase.getActiveExecutionId())
          || approvalCase.getLastStateSequence() != null && executionId.equals(approvalCase.getLastStateInstanceId())) {
        return;
      }
      if (approvalCase.getActiveExecutionId() != null) {
        throw stateConflict("审批案件当前轮次不能绑定新的执行", approvalCase);
      }
      if (businessStatusOf(approvalCase) != ApprovalBusinessStatus.PENDING) {
        throw stateConflict("审批案件当前轮次不能绑定新的执行", approvalCase);
      }
      Map<String, Object> data = baseUpdate();
      data.put("activeExecutionId", executionId);
      if (conditionalUpdate(approvalCase, data)) {
        return;
      }
    }
    throw new BusinessException("[" + ApprovalWorkflowErrorCodes.WORKFLOW_CASE_STATE_CONFLICT
        + "] 绑定审批执行时发生并发冲突: " + approvalCaseId);
  }

  /** 已知启动失败且尚未创建执行时，回到可编辑草稿。 */
  @Transactional
  public void markStartFailed(String approvalCaseId, int submissionRound, String reason) {
    updateTechnicalState(approvalCaseId, submissionRound, ApprovalTechnicalState.HEALTHY, null,
        ApprovalBusinessStatus.DRAFT, true);
  }

  public void markRecovering(String approvalCaseId, int submissionRound, String reason) {
    updateTechnicalState(approvalCaseId, submissionRound, ApprovalTechnicalState.RECOVERING, reason, null, false);
  }

  public void markUnknown(String approvalCaseId, int submissionRound, String reason) {
    updateTechnicalState(approvalCaseId, submissionRound, ApprovalTechnicalState.UNKNOWN, reason, null, false);
  }

  /**
   * 只有确认不可恢复时才使用；业务状态与技术状态在一次 CAS 写入中同时收敛。
   */
  public void markExhausted(String approvalCaseId, int submissionRound, String reason) {
    updateTechnicalState(approvalCaseId, submissionRound, ApprovalTechnicalState.EXHAUSTED, reason,
        ApprovalBusinessStatus.CANCELED, false);
  }

  /** 规范实例事件的公共消费入口。没有案件上下文的历史事件保持兼容，直接忽略。 */
  @EventListener
  @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
  public void onInstanceEvent(FlowInstanceEvent event) {
    // Versioned cases already committed with the source fact. Delivery must not mutate them again.
    if (event != null && event.getProjectionVersion() != null) { return; }
    if (businessProjections != null && businessProjections.orderedStream().anyMatch(projection -> projection.ownsInstance(event))) { return; }
    applyInstanceEvent(event);
  }

  /** Canonical lock order for source commands and receivers: business case, then instance cursor. */
  @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
  public void lockForSource(FlowInstanceEvent event) {
    if (blank(event.getApprovalCaseId())) {
      var context = event.getBusinessContext();
      if (context == null) { return; }
      var associated = lockByBusinessRecord(context.recordType(), context.recordId(), event.getFlowKey()).orElse(null);
      if (associated == null) { return; }
      if (!java.util.Objects.equals(associated.getActiveExecutionId(), event.getInstanceId())
          && !java.util.Objects.equals(associated.getLastStateInstanceId(), event.getInstanceId())) {
        throw new BusinessException("旧审批实例的新源事实缺少可信案件关联，不能猜测当前轮次");
      }
      event.setApprovalCaseId(associated.getApprovalCaseId());
      event.setSubmissionRound(associated.getSubmissionRound());
      return;
    }
    var current = getRequired(event.getApprovalCaseId());
    var locked = lockByBusinessRecord(current.getBusinessRecordType(), current.getBusinessRecordId(), current.getFlowKey())
        .orElseThrow(() -> new BusinessException("审批案件在源事实锁定前消失"));
    if (!current.getApprovalCaseId().equals(locked.getApprovalCaseId())) {
      throw new BusinessException("审批源事实案件身份冲突");
    }
    var context = new FlowInstanceEvent.BusinessContext(locked.getBusinessRecordType(), locked.getBusinessRecordId());
    if (!java.util.Objects.equals(event.getFlowKey(), locked.getFlowKey())
        || event.getBusinessContext() != null && !context.equals(event.getBusinessContext())) {
      throw new BusinessException("审批源事实与正式案件的业务归属不一致");
    }
    event.setBusinessContext(context);
  }

  /**
   * 将规范实例事件映射为案件状态。返回 false 表示事件属于旧轮次或已过时，不能覆盖当前案件。
   */
  public boolean applyInstanceEvent(FlowInstanceEvent event) {
    if (event == null || blank(event.getApprovalCaseId()) || event.getSubmissionRound() == null) {
      return false;
    }
    for (int attempt = 0; attempt < MAX_CAS_ATTEMPTS; attempt++) {
      ApprovalCase approvalCase = findById(event.getApprovalCaseId()).orElse(null);
      if (approvalCase != null && event.getProjectionVersion() != null) {
        // A command can have established a repeatable-read snapshot before waiting for
        // the receiver's baseline. Apply its fact from the locked current row, not that snapshot.
        approvalCase = lockByBusinessRecord(approvalCase.getBusinessRecordType(), approvalCase.getBusinessRecordId(), approvalCase.getFlowKey())
            .filter(locked -> locked.getApprovalCaseId().equals(event.getApprovalCaseId())).orElse(null);
      }
      if (approvalCase == null || !sameRound(approvalCase, event.getSubmissionRound())) {
        return false;
      }
      boolean versioned = Integer.valueOf(1).equals(event.getProjectionVersion());
      if (event.getProjectionVersion() != null && !versioned) {
        throw new BusinessException("审批案件不支持此源事实版本");
      }
      if (!versioned && approvalCase.getLastStateSequence() != null) { return false; }
      if (versioned && (event.getStateSequence() == null || event.getStateSequence() < 1)) {
        throw new BusinessException("新版审批源事实缺少有效状态序号");
      }
      if (versioned && approvalCase.getLastStateInstanceId() != null
          && !approvalCase.getLastStateInstanceId().equals(event.getInstanceId())) { return false; }
      if (versioned && approvalCase.getLastStateSequence() != null
          && event.getStateSequence() <= approvalCase.getLastStateSequence()) {
        if (event.getStateSequence().equals(approvalCase.getLastStateSequence())
            && !java.util.Objects.equals(event.getEventFactId(), approvalCase.getLastEventFactId())) {
          throw new BusinessException("同一审批案件状态序号出现不同源事实");
        }
        return java.util.Objects.equals(event.getEventFactId(), approvalCase.getLastEventFactId());
      }
      if (event.getEventFactId() != null && event.getEventFactId().equals(approvalCase.getLastEventFactId())) {
        return true;
      }
      ApprovalBusinessStatus target = businessStatusFor(event);
      if (target == null) {
        return false;
      }
      if (!belongsToActiveExecution(approvalCase, event)) {
        return false;
      }
      ApprovalBusinessStatus current = businessStatusOf(approvalCase);
      if (!versioned && current == ApprovalBusinessStatus.SUSPENDED
          && target == ApprovalBusinessStatus.PROCESSING
          && event.getEventType() != FlowInstanceEvent.EventType.RESUMED) {
        return false;
      }
      // Versioned facts already happened in the authoritative engine. Missing older deliveries
      // must not prevent catching up to its current state; commands retain their transition checks.
      if (!versioned && !current.canTransitionTo(target)) {
        return false;
      }
      Map<String, Object> data = baseUpdate();
      data.put("businessStatus", target.name());
      data.put("lastEventFactId", event.getEventFactId());
      if (versioned) {
        data.put("lastStateSequence", event.getStateSequence());
        data.put("lastStateInstanceId", event.getInstanceId());
      }
      if (event.getEventType() == FlowInstanceEvent.EventType.STARTED
          && blank(approvalCase.getActiveExecutionId())
          && !blank(event.getInstanceId())) {
        // The engine's STARTED fact is the authoritative execution identity. This
        // also closes the window in which Flowable commits STARTED before the
        // caller returns from start() and attempts the explicit bind.
        data.put("activeExecutionId", event.getInstanceId());
      }
      if (target.isTerminal()) {
        data.put("activeExecutionId", null);
      }
      if (event.getEventType() == FlowInstanceEvent.EventType.FAILED) {
        data.put("technicalState", ApprovalTechnicalState.EXHAUSTED.name());
        data.put("technicalFailureReason", failureReason(event));
      } else {
        data.put("technicalState", ApprovalTechnicalState.HEALTHY.name());
        data.put("technicalFailureReason", null);
      }
      if (conditionalUpdate(approvalCase, data)) {
        return true;
      }
    }
    return false;
  }

  public Optional<ApprovalCase> findById(String approvalCaseId) {
    if (blank(approvalCaseId)) {
      return Optional.empty();
    }
    try {
      return Optional.ofNullable(getDataModel().get(approvalCaseId, Map.of()).getOrElse((ApprovalCase) null));
    } catch (OuroborosException ex) {
      throw new BusinessException("查询审批案件失败: " + approvalCaseId, ex);
    }
  }

  /** The receiver has verified this engine snapshot while holding the same case lock as source dispatch. */
  @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
  public void establishStateBaseline(ApprovalInstanceState snapshot) {
    var current = lockByBusinessRecord(snapshot.businessContext().recordType(), snapshot.businessContext().recordId(), snapshot.flowKey())
        .orElseThrow(() -> new BusinessException("审批状态基线缺少正式案件"));
    if (!java.util.Objects.equals(current.getApprovalCaseId(), snapshot.approvalCaseId())
        || !java.util.Objects.equals(current.getSubmissionRound(), snapshot.submissionRound())
        || current.getLastStateSequence() != null && current.getLastStateSequence() > 0
        || current.getActiveExecutionId() != null && !current.getActiveExecutionId().equals(snapshot.instanceId())
        || current.getLastStateInstanceId() != null && !current.getLastStateInstanceId().equals(snapshot.instanceId())) {
      throw new BusinessException("审批状态基线已不属于案件当前实例 / 轮次");
    }
    var target = businessStatusFor(snapshot.eventType(), snapshot.outcome());
    if (target == null) { throw new BusinessException("审批状态基线没有可用的业务状态"); }
    var changed = baseUpdate();
    changed.put("businessStatus", target.name());
    changed.put("lastStateSequence", 0L);
    changed.put("lastStateInstanceId", snapshot.instanceId());
    changed.put("activeExecutionId", target.isTerminal() ? null : snapshot.instanceId());
    changed.put("technicalState", snapshot.eventType() == FlowInstanceEvent.EventType.FAILED
        ? ApprovalTechnicalState.EXHAUSTED.name() : ApprovalTechnicalState.HEALTHY.name());
    changed.put("technicalFailureReason", snapshot.eventType() == FlowInstanceEvent.EventType.FAILED ? snapshot.outcome() : null);
    if (!conditionalUpdate(current, changed)) { throw new BusinessException("审批案件状态基线写入冲突"); }
  }

  public Optional<ApprovalCase> findByBusinessRecord(
      String businessRecordType, String businessRecordId, String flowKey) {
    return findByRouteKey(CaseIdentity.of(businessRecordType, businessRecordId, flowKey).routeKey());
  }

  /** Locks the same row updated by reserve/bind CAS, so a new round cannot race a business projection. */
  @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
  public Optional<ApprovalCase> lockByBusinessRecord(String recordType, String recordId, String flowKey) {
    var model = getDataModel().getDataModel();
    var query = QueryStatement.builder().from(model.getFullName(), model.getRawName())
        .select(model.getFields().stream().<SExpression<?>>map(field -> SExpression.field(field.getName())).toList())
        .where(SExpression.create(Operators.EQ, SExpression.field("caseRouteKey"),
            SExpression.constant(CaseIdentity.of(recordType, recordId, flowKey).routeKey()))).build();
    var rows = model.queryForUpdate(query).getOrElseThrow(error -> new BusinessException("审批案件归属锁定失败", error));
    return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst().toBean(ApprovalCase.class));
  }

  private Submission reserveExisting(ApprovalCase approvalCase) {
    for (int attempt = 0; attempt < MAX_CAS_ATTEMPTS; attempt++) {
      ApprovalBusinessStatus current = businessStatusOf(approvalCase);
      if (current != ApprovalBusinessStatus.DRAFT && current != ApprovalBusinessStatus.WITHDRAWN) {
        throw new BusinessException("[" + ApprovalWorkflowErrorCodes.WORKFLOW_SUBMISSION_CONFLICT
            + "] 业务记录已有进行中的或已结束的审批案件: caseId=" + approvalCase.getApprovalCaseId()
            + ", status=" + current);
      }
      int nextRound = roundOf(approvalCase) + 1;
      Map<String, Object> data = baseUpdate();
      data.put("submissionRound", nextRound);
      data.put("activeExecutionId", null);
      data.put("businessStatus", ApprovalBusinessStatus.PENDING.name());
      data.put("technicalState", ApprovalTechnicalState.HEALTHY.name());
      data.put("technicalFailureReason", null);
      data.put("lastEventFactId", null);
      data.put("lastStateSequence", null);
      data.put("lastStateInstanceId", null);
      if (conditionalUpdate(approvalCase, data)) {
        return new Submission(approvalCase.getApprovalCaseId(), nextRound);
      }
      approvalCase = getRequired(approvalCase.getApprovalCaseId());
    }
    throw new BusinessException("[" + ApprovalWorkflowErrorCodes.WORKFLOW_CASE_STATE_CONFLICT
        + "] 保留审批重提轮次时发生并发冲突: " + approvalCase.getApprovalCaseId());
  }

  private void updateTechnicalState(
      String approvalCaseId,
      int submissionRound,
      ApprovalTechnicalState technicalState,
      String reason,
      ApprovalBusinessStatus requestedBusinessStatus,
      boolean onlyWithoutExecution) {
    requireText(approvalCaseId, "approvalCaseId");
    for (int attempt = 0; attempt < MAX_CAS_ATTEMPTS; attempt++) {
      ApprovalCase approvalCase = getRequired(approvalCaseId);
      requireRound(approvalCase, submissionRound);
      ApprovalBusinessStatus current = businessStatusOf(approvalCase);
      ApprovalBusinessStatus target = requestedBusinessStatus == null ? current : requestedBusinessStatus;
      if (!current.canTransitionTo(target)) {
        throw stateConflict("审批案件不能进入目标状态 " + target, approvalCase);
      }
      if (onlyWithoutExecution && !blank(approvalCase.getActiveExecutionId())) {
        throw stateConflict("已创建执行的审批案件不能按启动失败回退", approvalCase);
      }
      Map<String, Object> data = baseUpdate();
      data.put("businessStatus", target.name());
      data.put("technicalState", technicalState.name());
      data.put("technicalFailureReason", reason);
      if (target.isTerminal() || onlyWithoutExecution) {
        data.put("activeExecutionId", null);
      }
      if (conditionalUpdate(approvalCase, data)) {
        return;
      }
    }
    throw new BusinessException("[" + ApprovalWorkflowErrorCodes.WORKFLOW_CASE_STATE_CONFLICT
        + "] 更新审批案件技术状态时发生并发冲突: " + approvalCaseId);
  }

  private ApprovalCase newCase(CaseIdentity identity) {
    LocalDateTime now = LocalDateTime.now();
    ApprovalCase approvalCase = new ApprovalCase();
    approvalCase.setApprovalCaseId(UUID.randomUUID().toString());
    approvalCase.setCaseRouteKey(identity.routeKey());
    approvalCase.setBusinessRecordType(identity.businessRecordType());
    approvalCase.setBusinessRecordId(identity.businessRecordId());
    approvalCase.setFlowKey(identity.flowKey());
    approvalCase.setSubmissionRound(1);
    approvalCase.setBusinessStatus(ApprovalBusinessStatus.PENDING.name());
    approvalCase.setTechnicalState(ApprovalTechnicalState.HEALTHY.name());
    approvalCase.setCreatedAt(now);
    approvalCase.setUpdatedAt(now);
    return approvalCase;
  }

  private Optional<ApprovalCase> findByRouteKey(String routeKey) {
    try {
      return Optional.ofNullable(searchOne(Map.of("WHERE", Map.of(
          "caseRouteKey", Map.of("=", routeKey)))));
    } catch (OuroborosException ex) {
      throw new BusinessException("按业务记录查询审批案件失败", ex);
    }
  }

  private ApprovalCase getRequired(String approvalCaseId) {
    return findById(approvalCaseId)
        .orElseThrow(() -> new BusinessException("审批案件不存在: " + approvalCaseId));
  }

  private boolean conditionalUpdate(ApprovalCase approvalCase, Map<String, Object> data) {
    Map<String, Object> where = new LinkedHashMap<>();
    where.put("approvalCaseId", Map.of("=", approvalCase.getApprovalCaseId()));
    where.put("submissionRound", Map.of("=", roundOf(approvalCase)));
    where.put("businessStatus", Map.of("=", approvalCase.getBusinessStatus()));
    where.put("technicalState", Map.of("=", approvalCase.getTechnicalState()));
    // The execution binding is part of the CAS state. Without this predicate,
    // a concurrent bind or start-failure compensation could overwrite another
    // worker's execution after both workers read the same status snapshot.
    where.put("activeExecutionId", approvalCase.getActiveExecutionId() == null
        ? null : Map.of("=", approvalCase.getActiveExecutionId()));
    try {
      var result = getDataModel().update(where, data);
      if (result.isFailure()) {
        throw new BusinessException("更新审批案件失败: " + approvalCase.getApprovalCaseId(), result.getCause());
      }
      return result.get() != null && result.get() > 0;
    } catch (OuroborosException ex) {
      throw new BusinessException("更新审批案件失败: " + approvalCase.getApprovalCaseId(), ex);
    }
  }

  private Map<String, Object> baseUpdate() {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("updatedAt", LocalDateTime.now());
    return data;
  }

  private ApprovalBusinessStatus businessStatusFor(FlowInstanceEvent event) {
    return businessStatusFor(event.getEventType(), event.getOutcome());
  }

  private ApprovalBusinessStatus businessStatusFor(FlowInstanceEvent.EventType type, String outcome) {
    if (type == null) {
      return null;
    }
    return switch (type) {
      case STARTED, RESUMED -> ApprovalBusinessStatus.PROCESSING;
      case SUSPENDED -> ApprovalBusinessStatus.SUSPENDED;
      case APPROVED -> ApprovalBusinessStatus.APPROVED;
      case REJECTED -> ApprovalBusinessStatus.VETO;
      case CANCELLED -> "withdrawn".equalsIgnoreCase(outcome)
          ? ApprovalBusinessStatus.WITHDRAWN : "terminated".equalsIgnoreCase(outcome)
              ? ApprovalBusinessStatus.TERMINATED : ApprovalBusinessStatus.CANCELED;
      case FAILED -> ApprovalBusinessStatus.CANCELED;
    };
  }

  private boolean belongsToActiveExecution(ApprovalCase approvalCase, FlowInstanceEvent event) {
    String activeExecutionId = approvalCase.getActiveExecutionId();
    String eventExecutionId = event.getInstanceId();
    return blank(activeExecutionId)
        || blank(eventExecutionId)
        || activeExecutionId.equals(eventExecutionId);
  }

  private String failureReason(FlowInstanceEvent event) {
    if (!blank(event.getOutcome())) {
      return event.getOutcome();
    }
    return "流程执行已确认不可恢复";
  }

  private ApprovalBusinessStatus businessStatusOf(ApprovalCase approvalCase) {
    try {
      return ApprovalBusinessStatus.valueOf(approvalCase.getBusinessStatus());
    } catch (IllegalArgumentException | NullPointerException ex) {
      throw new BusinessException("审批案件包含未知业务状态: " + approvalCase.getBusinessStatus(), ex);
    }
  }

  private void requireRound(ApprovalCase approvalCase, int expectedRound) {
    if (!sameRound(approvalCase, expectedRound)) {
      throw stateConflict("审批案件轮次已变化", approvalCase);
    }
  }

  private boolean sameRound(ApprovalCase approvalCase, int expectedRound) {
    return approvalCase.getSubmissionRound() != null && approvalCase.getSubmissionRound() == expectedRound;
  }

  private int roundOf(ApprovalCase approvalCase) {
    if (approvalCase.getSubmissionRound() == null || approvalCase.getSubmissionRound() < 1) {
      throw new BusinessException("审批案件轮次非法: " + approvalCase.getApprovalCaseId());
    }
    return approvalCase.getSubmissionRound();
  }

  private BusinessException stateConflict(String message, ApprovalCase approvalCase) {
    return new BusinessException("[" + ApprovalWorkflowErrorCodes.WORKFLOW_CASE_STATE_CONFLICT + "] "
        + message + ": caseId=" + approvalCase.getApprovalCaseId()
        + ", round=" + approvalCase.getSubmissionRound()
        + ", status=" + approvalCase.getBusinessStatus());
  }

  private boolean isUniqueConstraintViolation(Throwable throwable) {
    Throwable cursor = throwable;
    while (cursor != null) {
      if (cursor instanceof UniqueConstraintViolationException) {
        return true;
      }
      cursor = cursor.getCause();
    }
    return false;
  }

  private static void requireText(String value, String fieldName) {
    if (blank(value)) {
      throw new BusinessException(fieldName + " 不能为空");
    }
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }

  private record CaseIdentity(String businessRecordType, String businessRecordId, String flowKey, String routeKey) {
    private static CaseIdentity of(String businessRecordType, String businessRecordId, String flowKey) {
      requireText(businessRecordType, "businessRecordType");
      requireText(businessRecordId, "businessRecordId");
      requireText(flowKey, "flowKey");
      String routeKey = digest(businessRecordType.trim() + '\u001f' + businessRecordId.trim() + '\u001f' + flowKey.trim());
      return new CaseIdentity(businessRecordType.trim(), businessRecordId.trim(), flowKey.trim(), routeKey);
    }

    private static String digest(String value) {
      try {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
      } catch (NoSuchAlgorithmException ex) {
        throw new IllegalStateException("JVM 缺少 SHA-256 算法", ex);
      }
    }
  }
}
