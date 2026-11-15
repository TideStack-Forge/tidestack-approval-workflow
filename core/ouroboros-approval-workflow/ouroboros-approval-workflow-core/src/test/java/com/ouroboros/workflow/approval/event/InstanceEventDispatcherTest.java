package com.ouroboros.workflow.approval.event;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.ouroboros.workflow.approval.listener.FlowInstanceListener;
import com.ouroboros.workflow.approval.model.events.FlowInstanceEvent;

class InstanceEventDispatcherTest {
  @Test
  void dispatchEventInvokesRegisteredListener() {
    InstanceEventDispatcher dispatcher = new InstanceEventDispatcher();
    CapturingFlowInstanceListener listener = new CapturingFlowInstanceListener();
    setField(dispatcher, "listeners", Collections.singletonList(listener));
    FlowInstanceEvent event = new FlowInstanceEvent();
    event.setInstanceId("instance-1");

    dispatcher.dispatchEvent(event);

    assertEquals(1, listener.eventCount());
    assertEquals("instance-1", listener.lastEvent().getInstanceId());
  }

  @Test
  void dispatchEventNoopsWhenNoListenersRegistered() {
    InstanceEventDispatcher dispatcher = new InstanceEventDispatcher();

    assertDoesNotThrow(() -> dispatcher.dispatchEvent(new FlowInstanceEvent()));
  }

  @Test
  void dispatchWithinTransactionEnqueuesAndDeliversAfterCommit() {
    Map<String, FlowEventOutbox> store = new LinkedHashMap<>();
    FlowEventOutboxServiceTest.InMemoryOutboxService outboxService =
        new FlowEventOutboxServiceTest.InMemoryOutboxService(
            FlowEventOutboxServiceTest.mockDataModel(store), store);
    InstanceEventDispatcher dispatcher = new InstanceEventDispatcher();
    CapturingFlowInstanceListener listener = new CapturingFlowInstanceListener();
    setField(dispatcher, "listeners", Collections.singletonList(listener));
    setField(dispatcher, "outboxService", outboxService);

    FlowInstanceEvent event = new FlowInstanceEvent();
    event.setInstanceId("instance-1");
    try {
      TransactionSynchronizationManager.initSynchronization();
      dispatcher.dispatchEvent(event);

      // 事务内：仅落库，不立即投递
      assertEquals(0, listener.eventCount());
      assertEquals(1, store.size());
      assertEquals(FlowEventOutbox.STATUS_PENDING, store.values().iterator().next().getStatus());

      // after-commit：投递 + markSuccess
      var enclosingGuard = new com.ouroboros.data.model.DataModelWriteGuard() {
        public void beforeWrite(com.ouroboros.data.model.DataModel model) {
          throw new IllegalStateException("enclosing Action must not own post-commit work");
        }
        public void markRollbackOnly() { }
      };
      setField(dispatcher, "publisher", (ApplicationEventPublisher) delivered ->
          org.junit.jupiter.api.Assertions.assertNull(com.ouroboros.data.model.DataModelExecutionContext.getWriteGuard()));
      com.ouroboros.data.model.DataModelExecutionContext.withWriteGuard(enclosingGuard, () -> {
        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
          synchronization.afterCommit();
        }
        org.junit.jupiter.api.Assertions.assertSame(enclosingGuard,
            com.ouroboros.data.model.DataModelExecutionContext.getWriteGuard());
        return null;
      });
    } finally {
      TransactionSynchronizationManager.clearSynchronization();
    }

    assertEquals(1, listener.eventCount());
    assertEquals("instance-1", listener.lastEvent().getInstanceId());
    assertEquals(FlowEventOutbox.STATUS_SUCCESS, store.values().iterator().next().getStatus());
  }

  @Test
  void deliveryFailureMarksFailureWithoutThrowing() {
    Map<String, FlowEventOutbox> store = new LinkedHashMap<>();
    FlowEventOutboxServiceTest.InMemoryOutboxService outboxService =
        new FlowEventOutboxServiceTest.InMemoryOutboxService(
            FlowEventOutboxServiceTest.mockDataModel(store), store);
    InstanceEventDispatcher dispatcher = new InstanceEventDispatcher();
    CapturingFlowInstanceListener listener = new CapturingFlowInstanceListener();
    setField(dispatcher, "listeners", Collections.singletonList(listener));
    setField(dispatcher, "outboxService", outboxService);
    setField(dispatcher, "publisher", (ApplicationEventPublisher) event -> {
      throw new IllegalStateException("publisher boom");
    });

    FlowInstanceEvent event = new FlowInstanceEvent();
    event.setInstanceId("instance-1");
    try {
      TransactionSynchronizationManager.initSynchronization();
      dispatcher.dispatchEvent(event);
      for (TransactionSynchronization synchronization
          : TransactionSynchronizationManager.getSynchronizations()) {
        synchronization.afterCommit();
      }
    } finally {
      TransactionSynchronizationManager.clearSynchronization();
    }

    assertEquals(FlowEventOutbox.STATUS_FAILED, store.values().iterator().next().getStatus());
    assertEquals(1, listener.eventCount());
  }

  @Test
  void deliverClaimedOutboxSkipsWhenTokenDoesNotHoldLease() {
    Map<String, FlowEventOutbox> store = new LinkedHashMap<>();
    FlowEventOutboxServiceTest.InMemoryOutboxService outboxService =
        new FlowEventOutboxServiceTest.InMemoryOutboxService(
            FlowEventOutboxServiceTest.mockDataModel(store), store);
    FlowEventOutbox outbox = outboxService.enqueue(event("instance-1"));
    outboxService.claim(outbox.getId(), "worker-1", 60).orElseThrow();
    InstanceEventDispatcher dispatcher = new InstanceEventDispatcher();
    CapturingFlowInstanceListener listener = new CapturingFlowInstanceListener();
    setField(dispatcher, "listeners", Collections.singletonList(listener));
    setField(dispatcher, "outboxService", outboxService);

    dispatcher.deliverClaimedOutbox(outbox.getId(), "wrong-token");

    assertEquals(0, listener.eventCount());
    assertEquals(FlowEventOutbox.STATUS_PROCESSING, store.values().iterator().next().getStatus());
  }

  private void setField(Object target, String fieldName, Object value) {
    try {
      Field field = target.getClass().getDeclaredField(fieldName);
      field.setAccessible(true);
      field.set(target, value);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("Failed to set field " + fieldName, e);
    }
  }

  private FlowInstanceEvent event(String instanceId) {
    FlowInstanceEvent event = new FlowInstanceEvent();
    event.setFlowKey("flow-a");
    event.setInstanceId(instanceId);
    event.setEventType(FlowInstanceEvent.EventType.APPROVED);
    return event;
  }

  private static final class CapturingFlowInstanceListener implements FlowInstanceListener {
    private FlowInstanceEvent lastEvent;
    private int eventCount;

    @Override
    public void onEvent(FlowInstanceEvent event) {
      lastEvent = event;
      eventCount++;
    }

    private FlowInstanceEvent lastEvent() {
      return lastEvent;
    }

    private int eventCount() {
      return eventCount;
    }
  }
}
