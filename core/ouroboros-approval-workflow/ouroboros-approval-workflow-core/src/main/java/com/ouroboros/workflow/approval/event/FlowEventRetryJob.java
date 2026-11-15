package com.ouroboros.workflow.approval.event;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 统一事件 outbox 重投作业（Phase 4 M24，可靠发布）。
 *
 * <p>到期条目经 {@link InstanceEventDispatcher#deliverOutbox} 重投（与 after-commit 回调共用投递入口），
 * 成功 markSuccess / 失败 markFailure（指数退避）；DEAD 条目不动（无人工重放端点，与镜像 outbox 现状一致）。
 */
@Component
public class FlowEventRetryJob {

  private static final Logger logger = LoggerFactory.getLogger(FlowEventRetryJob.class);
  private static final String CLAIM_OWNER = "flow-event-retry-job";
  private static final int LEASE_SECONDS = 60;

  private final FlowEventOutboxService outboxService;
  private final InstanceEventDispatcher eventDispatcher;
  private final TaskEventDispatcher taskEventDispatcher;

  @Autowired
  public FlowEventRetryJob(
      FlowEventOutboxService outboxService,
      InstanceEventDispatcher eventDispatcher,
      TaskEventDispatcher taskEventDispatcher) {
    this.outboxService = outboxService;
    this.eventDispatcher = eventDispatcher;
    this.taskEventDispatcher = taskEventDispatcher;
  }

  @Scheduled(fixedDelayString = "${ouroboros.workflow.approval.flow-event-retry-delay-ms:30000}")
  public void sweep() {
    List<FlowEventOutboxService.Claim> claimed = outboxService.claimDue(100, CLAIM_OWNER, LEASE_SECONDS);
    for (FlowEventOutboxService.Claim claim : claimed) {
      if (FlowEventOutbox.EVENT_KIND_TASK.equals(claim.outbox().getEventKind())) {
        taskEventDispatcher.deliverClaimedOutbox(claim.outbox().getId(), claim.claimToken());
      } else {
        eventDispatcher.deliverClaimedOutbox(claim.outbox().getId(), claim.claimToken());
      }
    }
    if (!claimed.isEmpty()) {
      logger.info("统一事件 outbox 重投完成: {} 条", claimed.size());
    }
  }
}
