package com.ouroboros.workflow.approval.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ouroboros.data.model.TypedDataModel;
import com.ouroboros.workflow.approval.model.events.FlowInstanceEvent;
import com.ouroboros.data.exception.UniqueConstraintViolationException;

import io.vavr.control.Try;

/**
 * M24-T1 统一事件 outbox 服务测试：入队、退避、死信、到期重试、序列化往返。
 */
class FlowEventOutboxServiceTest {

  private InMemoryOutboxService service;
  private Map<String, FlowEventOutbox> store;
  private List<Map<String, Object>> queryStatements;

  @BeforeEach
  void setUp() {
    store = new LinkedHashMap<>();
    queryStatements = new ArrayList<>();
    service = new InMemoryOutboxService(mockDataModel(store, queryStatements), store);
  }

  @Test
  void enqueueCreatesPendingEntryWithSerializedEvent() {
    FlowEventOutbox outbox = service.enqueue(event("inst-1"));

    assertThat(outbox.getStatus()).isEqualTo(FlowEventOutbox.STATUS_PENDING);
    assertThat(outbox.getRetryCount()).isZero();
    assertThat(outbox.getEventJson()).contains("inst-1");
    assertThat(outbox.getFlowKey()).isEqualTo("flow-a");
  }

  @Test
  void markFailureAppliesExponentialBackoff() {
    FlowEventOutbox outbox = service.enqueue(event("inst-1"));
    LocalDateTime before = LocalDateTime.now();

    FlowEventOutboxService.Claim claim = service.claim(outbox.getId(), "worker-1", 60).orElseThrow();
    service.markFailure(outbox.getId(), claim.claimToken(), "boom");

    FlowEventOutbox failed = service.findById(outbox.getId()).get();
    assertThat(failed.getStatus()).isEqualTo(FlowEventOutbox.STATUS_FAILED);
    assertThat(failed.getClaimOwner()).isNull();
    assertThat(failed.getClaimToken()).isNull();
    assertThat(failed.getClaimUntil()).isNull();
    assertThat(failed.getRetryCount()).isEqualTo(1);
    assertThat(failed.getNextAttemptAt()).isAfterOrEqualTo(before.plusMinutes(1));
    assertThat(failed.getLastError()).isEqualTo("boom");
  }

  @Test
  void exceedingMaxRetryCountMovesToDead() {
    FlowEventOutbox outbox = service.enqueue(event("inst-1"));

    for (int i = 0; i <= FlowEventOutbox.MAX_RETRY_COUNT; i++) {
      FlowEventOutbox current = store.get(outbox.getId());
      if (FlowEventOutbox.STATUS_FAILED.equals(current.getStatus())) {
        current.setNextAttemptAt(LocalDateTime.now().minusMinutes(1));
      }
      FlowEventOutboxService.Claim claim = service.claim(outbox.getId(), "worker-1", 60).orElseThrow();
      service.markFailure(outbox.getId(), claim.claimToken(), "boom-" + i);
    }

    FlowEventOutbox dead = service.findById(outbox.getId()).get();
    assertThat(dead.getStatus()).isEqualTo(FlowEventOutbox.STATUS_DEAD);
  }

  @Test
  void claimDueClaimsPendingExpiredFailedAndExpiredProcessingEntries() {
    FlowEventOutbox due = service.enqueue(event("inst-1"));
    FlowEventOutboxService.Claim dueClaim = service.claim(due.getId(), "worker-1", 60).orElseThrow();
    service.markFailure(due.getId(), dueClaim.claimToken(), "boom");
    store.get(due.getId()).setNextAttemptAt(LocalDateTime.now().minusMinutes(1));

    FlowEventOutbox pending = service.enqueue(event("inst-2"));
    FlowEventOutbox processing = service.enqueue(event("inst-3"));
    store.get(processing.getId()).setStatus(FlowEventOutbox.STATUS_PROCESSING);
    store.get(processing.getId()).setClaimOwner("old-worker");
    store.get(processing.getId()).setClaimToken("old-token");
    store.get(processing.getId()).setClaimUntil(LocalDateTime.now().minusMinutes(1));

    List<FlowEventOutboxService.Claim> claimed = service.claimDue(100, "worker-2", 60);

    assertThat(claimed).extracting(claim -> claim.outbox().getId())
        .contains(due.getId())
        .contains(pending.getId())
        .contains(processing.getId());
    assertThat(store.get(due.getId()).getStatus()).isEqualTo(FlowEventOutbox.STATUS_PROCESSING);
    assertThat(store.get(due.getId()).getClaimOwner()).isEqualTo("worker-2");
    assertThat(store.get(processing.getId()).getClaimOwner()).isEqualTo("worker-2");
    assertThat(store.get(processing.getId()).getClaimToken()).isNotEqualTo("old-token");
  }

  @Test
  void claimDuePushesStateFiltersOrderingAndLimitToDataModel() {
    service.claimDue(7, "worker-1", 60);

    assertThat(queryStatements).hasSize(3);
    assertThat(queryStatements).allSatisfy(statement -> assertThat(statement.get("LIMIT")).isEqualTo(7));
    assertThat(queryStatements.get(0).get("ORDER")).isEqualTo(Map.of(
        "claimUntil", "asc", "createdAt", "asc", "id", "asc"));
    Object processingWhere = queryStatements.get(0).get("WHERE");
    assertThat(processingWhere).isInstanceOf(Map.class);
    Map<?, ?> processingWhereMap = (Map<?, ?>) processingWhere;
    assertThat(processingWhereMap.get("status")).isEqualTo(Map.of("=", FlowEventOutbox.STATUS_PROCESSING));
    assertThat(processingWhereMap.get("claimUntil")).isInstanceOf(Map.class);
    assertThat(((Map<?, ?>) processingWhereMap.get("claimUntil")).get("<=")).isNotNull();
  }

  @Test
  void markSuccessAndFailureRequireClaimToken() {
    FlowEventOutbox outbox = service.enqueue(event("inst-1"));
    FlowEventOutboxService.Claim claim = service.claim(outbox.getId(), "worker-1", 60).orElseThrow();

    assertThat(service.markSuccess(outbox.getId(), "wrong-token")).isFalse();
    assertThat(store.get(outbox.getId()).getStatus()).isEqualTo(FlowEventOutbox.STATUS_PROCESSING);

    assertThat(service.markFailure(outbox.getId(), "wrong-token", "boom")).isFalse();
    assertThat(store.get(outbox.getId()).getStatus()).isEqualTo(FlowEventOutbox.STATUS_PROCESSING);

    assertThat(service.markSuccess(outbox.getId(), claim.claimToken())).isTrue();
    assertThat(store.get(outbox.getId()).getStatus()).isEqualTo(FlowEventOutbox.STATUS_SUCCESS);
    assertThat(store.get(outbox.getId()).getClaimOwner()).isNull();
    assertThat(store.get(outbox.getId()).getClaimToken()).isNull();
  }

  @Test
  void oldClaimTokenCannotCompleteAfterExpiredReclaim() {
    FlowEventOutbox outbox = service.enqueue(event("inst-1"));
    FlowEventOutboxService.Claim first = service.claim(outbox.getId(), "same-worker", 60).orElseThrow();
    store.get(outbox.getId()).setClaimUntil(LocalDateTime.now().minusMinutes(1));

    FlowEventOutboxService.Claim second = service.claim(outbox.getId(), "same-worker", 60).orElseThrow();

    assertThat(second.claimToken()).isNotEqualTo(first.claimToken());
    assertThat(service.markSuccess(outbox.getId(), first.claimToken())).isFalse();
    assertThat(service.markFailure(outbox.getId(), first.claimToken(), "stale")).isFalse();
    assertThat(store.get(outbox.getId()).getStatus()).isEqualTo(FlowEventOutbox.STATUS_PROCESSING);
    assertThat(store.get(outbox.getId()).getClaimToken()).isEqualTo(second.claimToken());
    assertThat(service.markFailure(outbox.getId(), second.claimToken(), "current")).isTrue();
    assertThat(store.get(outbox.getId()).getStatus()).isEqualTo(FlowEventOutbox.STATUS_FAILED);
  }

  @Test
  void fromInstanceJsonRoundTripsEvent() {
    FlowInstanceEvent event = event("inst-1");
    event.setBusinessContext(new FlowInstanceEvent.BusinessContext("supplier", "sup-1"));
    event.setCallbackFlow("supplier.processCallback");
    event.setTrackingId("tracking-1");
    event.setExternalUrl("https://external.example/inst-1");
    FlowEventOutbox outbox = service.enqueue(event);

    FlowInstanceEvent restored = service.fromInstanceJson(outbox).get();

    assertThat(restored.getInstanceId()).isEqualTo("inst-1");
    assertThat(restored.getFlowKey()).isEqualTo("flow-a");
    assertThat(restored.getEventType()).isEqualTo(FlowInstanceEvent.EventType.APPROVED);
    assertThat(restored.getBusinessContext())
        .isEqualTo(new FlowInstanceEvent.BusinessContext("supplier", "sup-1"));
    assertThat(restored.getCallbackFlow()).isEqualTo("supplier.processCallback");
    assertThat(restored.getTrackingId()).isEqualTo("tracking-1");
    assertThat(restored.getExternalUrl()).isEqualTo("https://external.example/inst-1");
  }

  @Test
  void enqueuePreservesRepeatedLifecycleEventsAsDistinctFacts() {
    FlowEventOutbox first = service.enqueue(event("inst-1"));

    FlowEventOutbox second = service.enqueue(event("inst-1"));

    assertThat(second.getId()).isNotEqualTo(first.getId());
    assertThat(second.getEventFactId()).isNotEqualTo(first.getEventFactId());
    assertThat(store).hasSize(2);
  }

  @Test
  void enqueueCreatesSeparateEntriesForDifferentEventTypes() {
    FlowEventOutbox first = service.enqueue(event("inst-1"));

    FlowInstanceEvent other = event("inst-1");
    other.setEventType(FlowInstanceEvent.EventType.REJECTED);
    FlowEventOutbox second = service.enqueue(other);

    assertThat(second.getId()).isNotEqualTo(first.getId());
    assertThat(store).hasSize(2);
  }

  @Test
  void enqueueWithSameExplicitFactIsIdempotent() {
    FlowInstanceEvent firstEvent = event("inst-1");
    firstEvent.setEventFactId("fact-1");
    FlowEventOutbox first = service.enqueue(firstEvent);

    FlowInstanceEvent retryEvent = event("inst-1");
    retryEvent.setEventFactId("fact-1");
    FlowEventOutbox retry = service.enqueue(retryEvent);

    assertThat(retry.getId()).isEqualTo(first.getId());
    assertThat(store).hasSize(1);
  }

  @Test
  void enqueueWithSameFactAndDifferentPayloadFailsClosed() {
    FlowInstanceEvent firstEvent = event("inst-1");
    firstEvent.setEventFactId("fact-1");
    service.enqueue(firstEvent);

    FlowInstanceEvent conflictingEvent = event("inst-2");
    conflictingEvent.setEventFactId("fact-1");

    assertThatThrownBy(() -> service.enqueue(conflictingEvent))
        .isInstanceOf(com.ouroboros.exception.BusinessException.class)
        .hasMessageContaining("WORKFLOW_EVENT_FACT_CONFLICT");
    assertThat(store).hasSize(1);
  }

  private FlowInstanceEvent event(String instanceId) {
    FlowInstanceEvent event = new FlowInstanceEvent();
    event.setFlowKey("flow-a");
    event.setInstanceId(instanceId);
    event.setEventType(FlowInstanceEvent.EventType.APPROVED);
    event.setOutcome("approved");
    return event;
  }

  static TypedDataModel<String, FlowEventOutbox> mockDataModel(
      Map<String, FlowEventOutbox> store,
      List<Map<String, Object>> queryStatements) {
    TypedDataModel<String, FlowEventOutbox> dm = mock(TypedDataModel.class);
    when(dm.insert(any(FlowEventOutbox.class))).thenAnswer(inv -> {
      FlowEventOutbox o = inv.getArgument(0);
      if (store.containsKey(o.getId())) {
        return Try.failure(new UniqueConstraintViolationException("duplicate event fact"));
      }
      store.put(o.getId(), o);
      return Try.success(o);
    });
    when(dm.query(anyMap())).thenAnswer(inv -> {
      Map<String, Object> statement = inv.getArgument(0);
      queryStatements.add(statement);
      return Try.success(new ArrayList<>(store.values().stream()
          .filter(o -> matchesStatement(o, statement))
          .toList()));
    });
    when(dm.get(any(), anyMap())).thenAnswer(inv -> Try.success(store.get(inv.getArgument(0))));
    when(dm.update(anyMap(), anyMap())).thenAnswer(inv -> {
      Map<String, Object> statement = inv.getArgument(0);
      Map<String, Object> data = inv.getArgument(1);
      long rows = 0;
      for (FlowEventOutbox outbox : store.values()) {
        if (matchesStatement(outbox, statement)) {
          applyData(outbox, data);
          rows++;
        }
      }
      return Try.success(rows);
    });
    return dm;
  }

  static TypedDataModel<String, FlowEventOutbox> mockDataModel(
      Map<String, FlowEventOutbox> store) {
    return mockDataModel(store, new ArrayList<>());
  }

  /** 按语句字段（instanceId/eventType/status 等）过滤内存存储，使生产查询路径被触达。 */
  static boolean matchesStatement(FlowEventOutbox outbox, Map<String, Object> statement) {
    for (Map.Entry<String, Object> entry : statement.entrySet()) {
      if (!(entry.getValue() instanceof Map<?, ?> condition)) {
        continue;
      }
      Object expected = condition.get("=");
      Object actual = switch (entry.getKey()) {
        case "id" -> outbox.getId();
        case "instanceId" -> outbox.getInstanceId();
        case "eventType" -> outbox.getEventType();
        case "status" -> outbox.getStatus();
        case "claimOwner" -> outbox.getClaimOwner();
        case "claimToken" -> outbox.getClaimToken();
        case "claimUntil" -> outbox.getClaimUntil();
        default -> null;
      };
      if (condition.containsKey("<")) {
        Object threshold = condition.get("<");
        if (!(actual instanceof LocalDateTime actualTime)
            || !(threshold instanceof LocalDateTime thresholdTime)
            || !actualTime.isBefore(thresholdTime)) {
          return false;
        }
        continue;
      }
      if (!java.util.Objects.equals(expected, actual)) {
        return false;
      }
    }
    return true;
  }

  static void applyData(FlowEventOutbox outbox, Map<String, Object> data) {
    data.forEach((key, value) -> {
      switch (key) {
        case "status" -> outbox.setStatus((String) value);
        case "claimOwner" -> outbox.setClaimOwner((String) value);
        case "claimToken" -> outbox.setClaimToken((String) value);
        case "claimUntil" -> outbox.setClaimUntil((LocalDateTime) value);
        case "nextAttemptAt" -> outbox.setNextAttemptAt((LocalDateTime) value);
        case "retryCount" -> outbox.setRetryCount((Integer) value);
        case "lastError" -> outbox.setLastError((String) value);
        case "updatedAt" -> outbox.setUpdatedAt((LocalDateTime) value);
        default -> {
        }
      }
    });
  }

  static final class InMemoryOutboxService extends FlowEventOutboxService {
    private final TypedDataModel<String, FlowEventOutbox> dataModel;
    private final Map<String, FlowEventOutbox> store;

    InMemoryOutboxService(TypedDataModel<String, FlowEventOutbox> dataModel,
        Map<String, FlowEventOutbox> store) {
      this.dataModel = dataModel;
      this.store = store;
    }

    @Override
    protected TypedDataModel<String, FlowEventOutbox> getDataModel() {
      return dataModel;
    }

    @Override
    public Boolean update(String id, FlowEventOutbox model) {
      store.put(id, model);
      return true;
    }
  }
}
