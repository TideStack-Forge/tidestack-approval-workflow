package com.ouroboros.workflow.approval.event;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.ouroboros.workflow.approval.listener.FlowTaskListener;
import com.ouroboros.workflow.approval.model.events.FlowTaskEvent;

@Component
public class TaskEventDispatcher {
  private static final String INLINE_CLAIM_OWNER = "task-event-dispatcher";
  private static final int INLINE_LEASE_SECONDS = 60;

  /** Only isolated non-Spring tests may install direct listeners by reflection. */
  List<FlowTaskListener> listeners;

  @Autowired(required = false)
  FlowEventOutboxService outboxService;

  @Autowired(required = false)
  FlowTaskFactContextProvider factContextProvider;

  private boolean springDeliveryConfigured;

  private ApplicationEventPublisher publisher = event -> {
    // No-op outside a Spring application context.
  };

  @Autowired(required = false)
  public void setPublisher(ApplicationEventPublisher publisher) {
    if (publisher != null) {
      this.publisher = publisher;
      this.springDeliveryConfigured = true;
    }
  }

  public void prepareFact(FlowTaskEvent event) {
    if (factContextProvider != null) {
      factContextProvider.capture(event);
      event.setProjectionVersion(1);
    }
  }

  public void dispatchEvent(FlowTaskEvent event) {
    dispatchEvent(event, true);
  }

  /** Independent Saga commits leave delivery to the existing Outbox worker, outside caller locks. */
  public void enqueueEvent(FlowTaskEvent event) {
    if (outboxService == null || !TransactionSynchronizationManager.isActualTransactionActive()
        || !TransactionSynchronizationManager.isSynchronizationActive()) {
      throw new com.ouroboros.exception.BusinessException("任务事实持久交付需要源事务及正式 Outbox");
    }
    dispatchEvent(event, false);
  }

  private void dispatchEvent(FlowTaskEvent event, boolean inlineDelivery) {
    prepareFact(event);
    if (event.getEventFactId() == null || event.getEventFactId().isBlank()) {
      event.setEventFactId(UUID.randomUUID().toString());
    }
    if (outboxService != null && TransactionSynchronizationManager.isSynchronizationActive()) {
      FlowEventOutbox outbox = outboxService.enqueue(event);
      if (!inlineDelivery) { return; }
      TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override
        public void afterCommit() {
          com.ouroboros.data.model.DataModelExecutionContext.withPlatformWrite(() -> {
            deliverOutbox(outbox.getId(), INLINE_CLAIM_OWNER, INLINE_LEASE_SECONDS);
            return null;
          });
        }
      });
      return;
    }
    deliver(event);
  }

  public void deliverClaimedOutbox(String outboxId, String claimToken) {
    Optional<FlowEventOutbox> outbox = outboxService.findById(outboxId);
    if (outbox.isEmpty() || !hasClaimToken(outbox.get(), claimToken)) {
      return;
    }
    Optional<FlowTaskEvent> event = outboxService.fromTaskJson(outbox.get());
    if (event.isEmpty()) {
      outboxService.markFailure(outboxId, claimToken, "统一任务事件反序列化失败");
      return;
    }
    try {
      deliver(event.get());
      outboxService.markSuccess(outboxId, claimToken);
    } catch (RuntimeException ex) {
      outboxService.markFailure(outboxId, claimToken, ex.getMessage());
    }
  }

  private void deliverOutbox(String outboxId, String owner, int leaseSeconds) {
    outboxService.claim(outboxId, owner, leaseSeconds)
        .ifPresent(claim -> deliverClaimedOutbox(claim.outbox().getId(), claim.claimToken()));
  }

  private boolean hasClaimToken(FlowEventOutbox outbox, String claimToken) {
    return FlowEventOutbox.STATUS_PROCESSING.equals(outbox.getStatus())
        && claimToken != null
        && claimToken.equals(outbox.getClaimToken());
  }

  private void deliver(FlowTaskEvent event) {
    // Direct listeners are a test-only hook, never a Spring production injection point.
    if (!springDeliveryConfigured) {
      Optional.ofNullable(listeners)
          .ifPresent(listeners -> listeners.forEach(listener -> listener.onEvent(event)));
    }
    publisher.publishEvent(event);
  }
}
