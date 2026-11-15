package com.ouroboros.workflow.approval.event;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Field;
import java.util.Collections;

import org.junit.jupiter.api.Test;

import com.ouroboros.workflow.approval.listener.FlowTaskListener;
import com.ouroboros.workflow.approval.model.events.FlowTaskEvent;

class TaskEventDispatcherTest {
  @Test
  void dispatchEventInvokesRegisteredListener() {
    TaskEventDispatcher dispatcher = new TaskEventDispatcher();
    CapturingFlowTaskListener listener = new CapturingFlowTaskListener();
    setField(dispatcher, "listeners", Collections.singletonList(listener));
    FlowTaskEvent event = new FlowTaskEvent();
    event.setTaskId("task-1");

    dispatcher.dispatchEvent(event);

    assertEquals(1, listener.eventCount());
    assertEquals("task-1", listener.lastEvent().getTaskId());
  }

  @Test
  void dispatchEventNoopsWhenNoListenersRegistered() {
    TaskEventDispatcher dispatcher = new TaskEventDispatcher();

    assertDoesNotThrow(() -> dispatcher.dispatchEvent(new FlowTaskEvent()));
  }

  @Test
  void durableOnlyDispatchRequiresASourceTransactionAndNeverFallsBackToSpring() {
    var task = new TaskEventDispatcher();
    task.setPublisher(event -> { throw new AssertionError("durable fact must not be broadcast inline"); });
    assertThrows(com.ouroboros.exception.BusinessException.class, () -> task.enqueueEvent(new FlowTaskEvent()));
    var instance = new InstanceEventDispatcher();
    instance.setPublisher(event -> { throw new AssertionError("durable fact must not be broadcast inline"); });
    assertThrows(com.ouroboros.exception.BusinessException.class,
        () -> instance.enqueueEvent(new com.ouroboros.workflow.approval.model.events.FlowInstanceEvent()));
  }

  @Test
  void independentSagaFactsStayPendingForTheExistingWorkerWithoutRegisteringInlineCallbacks() {
    var store = new java.util.LinkedHashMap<String, FlowEventOutbox>();
    var outbox = new FlowEventOutboxServiceTest.InMemoryOutboxService(FlowEventOutboxServiceTest.mockDataModel(store), store);
    var task = new TaskEventDispatcher(); setField(task, "outboxService", outbox);
    var instance = new InstanceEventDispatcher(); setField(instance, "outboxService", outbox);
    try {
      org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
      org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(true);
      task.enqueueEvent(new FlowTaskEvent());
      instance.enqueueEvent(new com.ouroboros.workflow.approval.model.events.FlowInstanceEvent());
      assertEquals(2, store.size());
      org.junit.jupiter.api.Assertions.assertTrue(store.values().stream()
          .allMatch(fact -> FlowEventOutbox.STATUS_PENDING.equals(fact.getStatus())));
      org.junit.jupiter.api.Assertions.assertTrue(org.springframework.transaction.support.TransactionSynchronizationManager
          .getSynchronizations().isEmpty());
    } finally {
      org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();
      org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(false);
    }
  }

  @Test
  void ordinarySourceTransactionsStillDeliverOnlyAfterCommitWithTheOriginalTime() {
    var store = new java.util.LinkedHashMap<String, FlowEventOutbox>();
    var outbox = new FlowEventOutboxServiceTest.InMemoryOutboxService(FlowEventOutboxServiceTest.mockDataModel(store), store);
    var task = new TaskEventDispatcher(); setField(task, "outboxService", outbox);
    var delivered = new java.util.ArrayList<FlowTaskEvent>();
    task.setPublisher(event -> delivered.add((FlowTaskEvent) event));
    var event = new FlowTaskEvent(); event.setEventTime(java.time.LocalDateTime.of(2026, 10, 1, 12, 0));
    event.setEventFactId("original-task-fact");
    try {
      org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
      task.dispatchEvent(event);
      assertEquals(0, delivered.size());
      for (var synchronization : org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations()) {
        synchronization.afterCommit();
      }
      assertEquals(1, delivered.size());
      assertEquals(event.getEventTime(), delivered.getFirst().getEventTime());
      assertEquals(event.getEventFactId(), delivered.getFirst().getEventFactId());
      assertEquals(FlowEventOutbox.STATUS_SUCCESS, store.get(event.getEventFactId()).getStatus());
    } finally {
      org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();
    }
  }

  @Test
  void dispatchEventPropagatesListenerFailures() {
    TaskEventDispatcher dispatcher = new TaskEventDispatcher();
    setField(dispatcher, "listeners", Collections.singletonList(new ThrowingFlowTaskListener("dispatch failed")));

    RuntimeException error = assertThrows(RuntimeException.class, () -> dispatcher.dispatchEvent(new FlowTaskEvent()));
    assertEquals("dispatch failed", error.getMessage());
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

  private static final class CapturingFlowTaskListener implements FlowTaskListener {
    private FlowTaskEvent lastEvent;
    private int eventCount;

    @Override
    public void onEvent(FlowTaskEvent event) {
      lastEvent = event;
      eventCount++;
    }

    private FlowTaskEvent lastEvent() {
      return lastEvent;
    }

    private int eventCount() {
      return eventCount;
    }
  }

  private static final class ThrowingFlowTaskListener implements FlowTaskListener {
    private final String message;

    private ThrowingFlowTaskListener(String message) {
      this.message = message;
    }

    @Override
    public void onEvent(FlowTaskEvent event) {
      throw new RuntimeException(message);
    }
  }
}
