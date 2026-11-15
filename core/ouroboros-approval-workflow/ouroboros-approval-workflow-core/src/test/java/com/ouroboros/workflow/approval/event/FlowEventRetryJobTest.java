package com.ouroboros.workflow.approval.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * M24-T2 统一事件 outbox 重投作业测试：到期条目经 dispatcher 投递，无到期条目不动作。
 */
class FlowEventRetryJobTest {

  private FlowEventOutboxService outboxService;
  private InstanceEventDispatcher eventDispatcher;
  private TaskEventDispatcher taskEventDispatcher;
  private FlowEventRetryJob job;

  @BeforeEach
  void setUp() {
    outboxService = mock(FlowEventOutboxService.class);
    eventDispatcher = mock(InstanceEventDispatcher.class);
    taskEventDispatcher = mock(TaskEventDispatcher.class);
    job = new FlowEventRetryJob(outboxService, eventDispatcher, taskEventDispatcher);
  }

  @Test
  void sweepDeliversEachDueEntry() {
    FlowEventOutbox a = new FlowEventOutbox();
    a.setId("outbox-1");
    a.setEventKind(FlowEventOutbox.EVENT_KIND_INSTANCE);
    FlowEventOutbox b = new FlowEventOutbox();
    b.setId("outbox-2");
    b.setEventKind(FlowEventOutbox.EVENT_KIND_TASK);
    when(outboxService.claimDue(100, "flow-event-retry-job", 60)).thenReturn(List.of(
        new FlowEventOutboxService.Claim(a, "token-a"),
        new FlowEventOutboxService.Claim(b, "token-b")));

    job.sweep();

    verify(eventDispatcher).deliverClaimedOutbox("outbox-1", "token-a");
    verify(taskEventDispatcher).deliverClaimedOutbox("outbox-2", "token-b");
  }

  @Test
  void sweepWithNoDueEntriesDoesNothing() {
    when(outboxService.claimDue(100, "flow-event-retry-job", 60)).thenReturn(List.of());

    job.sweep();

    verify(eventDispatcher, never()).deliverClaimedOutbox(
        org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    verify(taskEventDispatcher, never()).deliverClaimedOutbox(
        org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
  }
}
