package com.ouroboros.workflow.approval.service;

import java.util.Objects;
import java.util.Optional;

import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ouroboros.data.service.SimpleModelServiceImpl;
import com.ouroboros.data.exception.UniqueConstraintViolationException;
import com.ouroboros.data.dsl.statement.QueryStatement;
import com.ouroboros.data.dsl.SExpression;
import com.ouroboros.data.dsl.Operators;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.workflow.approval.ApprovalInstanceState;
import com.ouroboros.workflow.approval.event.FlowInstanceFactState;
import com.ouroboros.workflow.approval.model.events.FlowInstanceEvent;

/** Assigns source-owned lifecycle versions under a database row lock. */
public class FlowInstanceFactStateService extends SimpleModelServiceImpl<String, FlowInstanceFactState> {
  /** Identity is immutable once sourced; restore it before taking the canonical case/instance lock order. */
  public void restoreIdentity(FlowInstanceEvent event) {
    var current = getDataModel().get(event.getInstanceId(), java.util.Map.of())
        .getOrElseThrow(error -> new BusinessException("读取审批源事实归属失败", error));
    if (current != null) { inheritIdentity(event, current); }
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void advance(FlowInstanceEvent event) {
    if (event.getInstanceId() == null || event.getInstanceId().isBlank() || event.getEventType() == null) {
      throw new BusinessException("审批实例事实缺少实例或生命周期类型");
    }
    FlowInstanceFactState state = lock(event.getInstanceId()).orElseGet(() -> create(event));
    inheritIdentity(event, state);
    if (Objects.equals(state.getLastEventFactId(), event.getEventFactId())
        && !Objects.equals(state.getEventType(), event.getEventType().name())) {
      throw new BusinessException("同一审批事实不能对应不同生命周期状态");
    }
    long sequence = Objects.equals(state.getLastEventFactId(), event.getEventFactId())
        ? state.getStateSequence() : Math.addExact(state.getStateSequence(), 1);
    var changed = new java.util.LinkedHashMap<String, Object>();
    changed.put("stateSequence", sequence);
    changed.put("lastEventFactId", event.getEventFactId());
    changed.put("eventType", event.getEventType().name());
    changed.put("outcome", event.getOutcome());
    changed.put("businessRecordType", event.getBusinessContext() == null ? null : event.getBusinessContext().recordType());
    changed.put("businessRecordId", event.getBusinessContext() == null ? null : event.getBusinessContext().recordId());
    changed.put("approvalCaseId", event.getApprovalCaseId());
    changed.put("submissionRound", event.getSubmissionRound());
    changed.put("callbackFlow", event.getCallbackFlow());
    Long rows = getDataModel().update(event.getInstanceId(), changed).getOrElseThrow(error ->
        new BusinessException("审批实例事实版本更新失败", error));
    if (!Long.valueOf(1).equals(rows)) { throw new BusinessException("审批实例事实版本未更新唯一实例"); }
    event.setProjectionVersion(1);
    event.setStateSequence(sequence);
  }

  /** Sequence zero is a verified state baseline, never an invented legacy fact or delivery order. */
  @Transactional(propagation = Propagation.MANDATORY)
  public FlowInstanceFactState establishBaseline(ApprovalInstanceState snapshot) {
    if (snapshot.instanceId() == null || snapshot.flowKey() == null || snapshot.eventType() == null
        || snapshot.businessContext() == null || snapshot.approvalCaseId() == null
        || snapshot.submissionRound() == null || snapshot.submissionRound() < 1) {
      throw new BusinessException("审批可信状态基线缺少实例、业务对象或案件轮次");
    }
    var existing = lock(snapshot.instanceId());
    if (existing.isPresent()) { throw new BusinessException("审批状态基线必须在源游标首次建立时写入"); }
    var state = new FlowInstanceFactState();
    state.setInstanceId(snapshot.instanceId());
    state.setFlowKey(snapshot.flowKey());
    state.setStateSequence(0L);
    state.setLastEventFactId("");
    state.setEventType(snapshot.eventType().name());
    state.setOutcome(snapshot.outcome());
    state.setBusinessRecordType(snapshot.businessContext().recordType());
    state.setBusinessRecordId(snapshot.businessContext().recordId());
    state.setApprovalCaseId(snapshot.approvalCaseId());
    state.setSubmissionRound(snapshot.submissionRound());
    state.setCallbackFlow(snapshot.callbackFlow());
    return getDataModel().insert(state).getOrElseThrow(error -> new BusinessException("审批可信状态基线持久化失败", error));
  }

  private FlowInstanceFactState create(FlowInstanceEvent event) {
    var state = new FlowInstanceFactState();
    state.setInstanceId(event.getInstanceId());
    state.setStateSequence(0L);
    state.setLastEventFactId("");
    state.setEventType(event.getEventType().name());
    state.setFlowKey(event.getFlowKey());
    var inserted = getDataModel().insert(state);
    if (inserted.isSuccess()) { return state; }
    Throwable cause = inserted.getCause();
    for (Throwable cursor = cause; cursor != null; cursor = cursor.getCause()) {
      if (cursor instanceof UniqueConstraintViolationException) {
        return lock(event.getInstanceId()).orElseThrow(() -> new BusinessException("审批事实版本并发创建后不可见", cause));
      }
    }
    throw new BusinessException("审批实例事实版本创建失败", cause);
  }

  public Optional<FlowInstanceFactState> lock(String instanceId) {
    var model = getDataModel().getDataModel();
    var query = QueryStatement.builder().from(model.getFullName(), model.getRawName())
        .select(model.getFields().stream().<SExpression<?>>map(field -> SExpression.field(field.getName())).toList())
        .where(SExpression.create(Operators.EQ, SExpression.field("instanceId"), SExpression.constant(instanceId))).build();
    var rows = model.queryForUpdate(query).getOrElseThrow(error -> new BusinessException("审批实例事实版本锁定失败", error));
    return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst().toBean(FlowInstanceFactState.class));
  }

  private void inheritIdentity(FlowInstanceEvent event, FlowInstanceFactState state) {
    if (!Objects.equals(event.getFlowKey(), state.getFlowKey())) {
      throw new BusinessException("审批实例事实流程归属冲突");
    }
    if (state.getStateSequence() > 0 && state.getBusinessRecordType() == null && event.getBusinessContext() != null) {
      throw new BusinessException("审批实例不能在后续事实中改变业务归属");
    }
    if (state.getBusinessRecordType() != null) {
      var recorded = new FlowInstanceEvent.BusinessContext(state.getBusinessRecordType(), state.getBusinessRecordId());
      if (event.getBusinessContext() != null && !recorded.equals(event.getBusinessContext())) {
        throw new BusinessException("审批实例事实业务归属冲突");
      }
      event.setBusinessContext(recorded);
    }
    requireSameIdentity(state.getApprovalCaseId(), event.getApprovalCaseId());
    requireSameIdentity(state.getSubmissionRound(), event.getSubmissionRound());
    requireSameIdentity(state.getCallbackFlow(), event.getCallbackFlow());
    if (event.getApprovalCaseId() == null) { event.setApprovalCaseId(state.getApprovalCaseId()); }
    if (event.getSubmissionRound() == null) { event.setSubmissionRound(state.getSubmissionRound()); }
    if (event.getCallbackFlow() == null) { event.setCallbackFlow(state.getCallbackFlow()); }
  }

  private void requireSameIdentity(Object recorded, Object requested) {
    if (recorded != null && requested != null && !recorded.equals(requested)) {
      throw new BusinessException("审批实例事实案件、轮次或回调归属冲突");
    }
  }
}
