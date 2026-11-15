package com.ouroboros.workflow.approval.event;

import com.ouroboros.workflow.approval.service.FlowInstanceFactStateService;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.ouroboros.data.model.TypedDataModel;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.workflow.approval.ApprovalInstanceState;
import com.ouroboros.workflow.approval.model.events.FlowInstanceEvent;
import io.vavr.control.Try;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FlowInstanceFactStateServiceTest {
  @Test
  void baselineRetainsTrustedIdentityButDoesNotInventASequenceOrFact() {
    var stored = new AtomicReference<FlowInstanceFactState>();
    TypedDataModel<String, FlowInstanceFactState> model = mock();
    when(model.insert(any(FlowInstanceFactState.class))).thenAnswer(call -> {
      stored.set(call.getArgument(0));
      return Try.success(stored.get());
    });
    when(model.update(anyString(), anyMap())).thenReturn(Try.success(1L));
    var service = new FlowInstanceFactStateService() {
      @Override protected TypedDataModel<String, FlowInstanceFactState> getDataModel() { return model; }
      @Override public Optional<FlowInstanceFactState> lock(String id) { return Optional.ofNullable(stored.get()); }
    };
    var snapshot = new ApprovalInstanceState("flow-1", "instance-1", new FlowInstanceEvent.BusinessContext("sales.Order", "42"),
        "case", 2, "sales.Order.processCallback", FlowInstanceEvent.EventType.SUSPENDED, null);
    var baseline = service.establishBaseline(snapshot);
    assertEquals(0L, baseline.getStateSequence());
    assertEquals("", baseline.getLastEventFactId());
    assertThrows(BusinessException.class, () -> service.establishBaseline(snapshot));
    var next = event("next-source-fact", FlowInstanceEvent.EventType.RESUMED);
    service.advance(next);
    assertEquals(1L, next.getStateSequence());
    assertEquals(snapshot.businessContext(), next.getBusinessContext());
    assertEquals("case", next.getApprovalCaseId());
    assertEquals(2, next.getSubmissionRound());
    assertThrows(BusinessException.class, () -> service.establishBaseline(new ApprovalInstanceState("flow-1", "invalid",
        snapshot.businessContext(), null, null, null, snapshot.eventType(), null)));
    stored.set(null);
    when(model.insert(any(FlowInstanceFactState.class))).thenReturn(Try.failure(new BusinessException("baseline storage failed")));
    assertThrows(BusinessException.class, () -> service.establishBaseline(snapshot));
  }

  @Test
  void lifecycleFactsAdvanceOnceAndRetainTrustedIdentityAfterRuntimeInstanceDeletion() {
    var stored = new AtomicReference<FlowInstanceFactState>();
    TypedDataModel<String, FlowInstanceFactState> model = mock();
    when(model.insert(any(FlowInstanceFactState.class))).thenAnswer(call -> {
      stored.set(call.getArgument(0));
      return Try.success(stored.get());
    });
    when(model.update(anyString(), anyMap())).thenAnswer(call -> {
      Map<String, Object> values = call.getArgument(1);
      var state = stored.get();
      state.setStateSequence((Long) values.get("stateSequence"));
      state.setLastEventFactId((String) values.get("lastEventFactId"));
      state.setEventType((String) values.get("eventType"));
      state.setBusinessRecordType((String) values.get("businessRecordType"));
      state.setBusinessRecordId((String) values.get("businessRecordId"));
      state.setApprovalCaseId((String) values.get("approvalCaseId"));
      state.setSubmissionRound((Integer) values.get("submissionRound"));
      state.setCallbackFlow((String) values.get("callbackFlow"));
      return Try.success(1L);
    });
    var service = new FlowInstanceFactStateService() {
      @Override protected TypedDataModel<String, FlowInstanceFactState> getDataModel() { return model; }
      @Override public Optional<FlowInstanceFactState> lock(String id) { return Optional.ofNullable(stored.get()); }
    };
    var started = event("fact-start", FlowInstanceEvent.EventType.STARTED);
    started.setBusinessContext(new FlowInstanceEvent.BusinessContext("sales.Order", "42"));
    started.setApprovalCaseId("case-1");
    started.setSubmissionRound(2);
    started.setCallbackFlow("sales.Order.processCallback");
    service.advance(started);
    assertEquals(1, started.getProjectionVersion());
    assertEquals(1L, started.getStateSequence());
    service.advance(started);
    assertEquals(1L, started.getStateSequence());

    var ended = event("fact-end", FlowInstanceEvent.EventType.APPROVED);
    service.advance(ended);
    assertEquals(2L, ended.getStateSequence());
    assertEquals(started.getBusinessContext(), ended.getBusinessContext());
    assertEquals("case-1", ended.getApprovalCaseId());
    assertEquals(2, ended.getSubmissionRound());
    assertEquals("sales.Order.processCallback", ended.getCallbackFlow());

    var collision = event("fact-end", FlowInstanceEvent.EventType.REJECTED);
    assertThrows(BusinessException.class, () -> service.advance(collision));
    var wrongObject = event("other-fact", FlowInstanceEvent.EventType.RESUMED);
    wrongObject.setBusinessContext(new FlowInstanceEvent.BusinessContext("sales.Order", "999"));
    assertThrows(BusinessException.class, () -> service.advance(wrongObject));
    var wrongFlow = event("other-fact", FlowInstanceEvent.EventType.RESUMED);
    wrongFlow.setFlowKey("other");
    assertThrows(BusinessException.class, () -> service.advance(wrongFlow));
  }

  @Test
  void invalidAndFailedSourceWritesCannotProduceASequencedFact() {
    TypedDataModel<String, FlowInstanceFactState> model = mock();
    when(model.insert(any(FlowInstanceFactState.class))).thenReturn(Try.failure(new BusinessException("storage unavailable")));
    var service = new FlowInstanceFactStateService() {
      @Override protected TypedDataModel<String, FlowInstanceFactState> getDataModel() { return model; }
      @Override public Optional<FlowInstanceFactState> lock(String id) { return Optional.empty(); }
    };
    assertThrows(BusinessException.class, () -> service.advance(new FlowInstanceEvent()));
    var fact = event("new", FlowInstanceEvent.EventType.STARTED);
    assertThrows(BusinessException.class, () -> service.advance(fact));
    assertNull(fact.getStateSequence());
    assertNull(fact.getProjectionVersion());
  }

  private FlowInstanceEvent event(String fact, FlowInstanceEvent.EventType type) {
    var event = new FlowInstanceEvent();
    event.setInstanceId("instance-1");
    event.setFlowKey("flow-1");
    event.setEventFactId(fact);
    event.setEventType(type);
    return event;
  }
}
