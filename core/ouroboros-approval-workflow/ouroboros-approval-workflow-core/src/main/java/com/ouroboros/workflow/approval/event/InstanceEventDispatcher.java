package com.ouroboros.workflow.approval.event;

import com.ouroboros.workflow.approval.service.FlowInstanceFactStateService;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.ouroboros.workflow.approval.listener.FlowInstanceListener;
import com.ouroboros.workflow.approval.model.events.FlowInstanceEvent;

/**
 * 流程实例事件分发器（统一结果事件的兼容入口）。
 *
 * <p>职责：经 Spring {@link ApplicationEventPublisher} 发布统一结果事件，供订阅方
 * 以 {@code @EventListener} 按 payload 类型订阅。历史直连 listener 不再是生产装配点。
 *
 * <p>可靠发布（Phase 4 M24，决策 D-12）：事务同步活动时，事件先入 {@link FlowEventOutbox}
 * （同事务落库），注册 after-commit 回调统一投递（Spring 事件总线），投递失败
 * markFailure 由 {@link FlowEventRetryJob} 到期重投（at-least-once，消费方幂等由既有机制保证）；
 * 无事务（单元测试/非事务调用）时直接投递，行为与 Phase 3 一致。
 * 与设计 L677"默认同步 in-tx 发布"的取舍：可靠发布优先，投递时机从 in-tx 延后到 commit 后，
 * 需要 in-tx 语义的监听方自行使用 {@code @TransactionalEventListener}（发布方不感知事务阶段）。
 * 与镜像 outbox（{@code ExternalMirrorOutbox}）的关系：镜像 outbox 是外部 HTTP 命令补偿
 * （跨系统投递），本 outbox 是本地事件投递补偿（进程内监听器）；两者独立演进、互不触碰。
 *
 * <p>非 Spring 测试可以反射安装 listener 作为记录器；该字段没有 Spring 注入点，不能形成
 * production direct/Spring 双通道。
 */
@Component
public class InstanceEventDispatcher {

  private static final String INLINE_CLAIM_OWNER = "instance-event-dispatcher";
  private static final int INLINE_LEASE_SECONDS = 60;

  /** Only isolated non-Spring tests may install direct listeners by reflection. */
  List<FlowInstanceListener> listeners;

  @Autowired(required = false)
  FlowEventOutboxService outboxService;

  @Autowired(required = false)
  FlowInstanceFactStateService factStateService;

  @Autowired(required = false)
  com.ouroboros.workflow.approval.lifecycle.ApprovalCaseService approvalCaseService;

  private boolean springDeliveryConfigured;

  private ApplicationEventPublisher publisher = event -> {
    // 默认 no-op：非 Spring 场景（单元测试）下不向事件总线发布
  };

  public InstanceEventDispatcher() {
  }

  /**
   * 供 Spring 注入应用事件发布器；非 Spring 场景（单元测试）为 no-op 发布器。
   */
  @Autowired(required = false)
  public void setPublisher(ApplicationEventPublisher publisher) {
    if (publisher != null) {
      this.publisher = publisher;
      this.springDeliveryConfigured = true;
    }
  }

  /**
   * 分发流程实例事件：事务内落 outbox + after-commit 投递；无事务直接投递。
   */
  public void dispatchEvent(FlowInstanceEvent event) {
    dispatchEvent(event, true);
  }

  /** Independent Saga commits leave delivery to the existing Outbox worker, outside caller locks. */
  public void enqueueEvent(FlowInstanceEvent event) {
    if (outboxService == null || !TransactionSynchronizationManager.isActualTransactionActive()
        || !TransactionSynchronizationManager.isSynchronizationActive()) {
      throw new com.ouroboros.exception.BusinessException("实例事实持久交付需要源事务及正式 Outbox");
    }
    dispatchEvent(event, false);
  }

  private void dispatchEvent(FlowInstanceEvent event, boolean inlineDelivery) {
    if (event.getEventFactId() == null || event.getEventFactId().isBlank()) {
      event.setEventFactId(UUID.randomUUID().toString());
    }
    if (factStateService != null) {
      factStateService.restoreIdentity(event);
      if (approvalCaseService != null) { approvalCaseService.lockForSource(event); }
      factStateService.advance(event);
      if (approvalCaseService != null) { approvalCaseService.applyInstanceEvent(event); }
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

  /**
   * 按 outbox 条目投递（after-commit 回调与重试作业共用）：反序列化事件 → Spring 事件总线
   * 发布 → 成功 markSuccess / 失败 markFailure（不抛出，重试作业兜底）。
   */
  public void deliverOutbox(String outboxId) {
    deliverOutbox(outboxId, INLINE_CLAIM_OWNER, INLINE_LEASE_SECONDS);
  }

  public void deliverOutbox(String outboxId, String owner, int leaseSeconds) {
    Optional<FlowEventOutboxService.Claim> claim = outboxService.claim(outboxId, owner, leaseSeconds);
    if (claim.isEmpty()) {
      return;
    }
    deliverClaimedOutbox(claim.get().outbox().getId(), claim.get().claimToken());
  }

  /**
   * 投递已经由调用方领取的条目；retry job 与 after-commit 统一走此处完成 markSuccess/markFailure。
   */
  public void deliverClaimedOutbox(String outboxId, String claimToken) {
    Optional<FlowEventOutbox> outbox = outboxService.findById(outboxId);
    if (outbox.isEmpty()) {
      return;
    }
    if (!hasClaimToken(outbox.get(), claimToken)) {
      return;
    }
    Optional<FlowInstanceEvent> event = outboxService.fromInstanceJson(outbox.get());
    if (event.isEmpty()) {
      outboxService.markFailure(outboxId, claimToken, "统一事件反序列化失败");
      return;
    }
    try {
      deliver(event.get());
      outboxService.markSuccess(outboxId, claimToken);
    } catch (RuntimeException ex) {
      outboxService.markFailure(outboxId, claimToken, ex.getMessage());
    }
  }

  private boolean hasClaimToken(FlowEventOutbox outbox, String claimToken) {
    return FlowEventOutbox.STATUS_PROCESSING.equals(outbox.getStatus())
        && claimToken != null
        && claimToken.equals(outbox.getClaimToken());
  }

  private void deliver(FlowInstanceEvent event) {
    // Direct listeners are a test-only hook, never a Spring production injection point.
    if (!springDeliveryConfigured) {
      Optional.ofNullable(listeners)
          .ifPresent(listeners -> listeners.forEach(listener -> listener.onEvent(event)));
    }
    publisher.publishEvent(event);
  }
}
