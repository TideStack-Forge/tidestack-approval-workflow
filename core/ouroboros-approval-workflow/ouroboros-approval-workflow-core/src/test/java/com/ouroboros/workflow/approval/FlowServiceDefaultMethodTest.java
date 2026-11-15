package com.ouroboros.workflow.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.vavr.control.Try;

/**
 * M7 接口默认方法语义测试：cc 启动变体委托、撤回默认委托、催办默认失败。
 */
class FlowServiceDefaultMethodTest {

  @Test
  void ccStartVariantDelegatesToPlainStart() {
    FlowInstanceService service = Mockito.mock(FlowInstanceService.class, Mockito.CALLS_REAL_METHODS);
    when(service.start(anyString(), anyString(), any(), any(), any()))
        .thenReturn(Try.success("inst-1"));

    Try<String> result = service.start("flow-a", "user-1", null, null, null,
        Map.of("userIds", java.util.List.of("user-2"), "position", "START"));

    assertThat(result.get()).isEqualTo("inst-1");
    verify(service).start(eq("flow-a"), eq("user-1"), eq(null), eq(null), eq(null));
  }

  @Test
  void withdrawDefaultDelegatesToCancel() {
    FlowInstanceService service = Mockito.mock(FlowInstanceService.class, Mockito.CALLS_REAL_METHODS);
    when(service.cancel("inst-1", "user-1", "撤回")).thenReturn(Try.success(null));

    Try<Void> result = service.withdraw("inst-1", "user-1", "撤回");

    assertThat(result.isSuccess()).isTrue();
    verify(service).cancel("inst-1", "user-1", "撤回");
  }

  @Test
  void remindDefaultFailsWithCoreUnsupportedCode() {
    FlowTaskService service = mock(FlowTaskService.class, Mockito.CALLS_REAL_METHODS);

    Try<Void> result = service.remind("task-1", "user-1");

    assertThat(result.isFailure()).isTrue();
    assertThat(result.getCause().getMessage()).contains(ApprovalWorkflowErrorCodes.WORKFLOW_REMIND_UNSUPPORTED);
  }

  @Test
  void ccStartVariantAcceptsNullCc() {
    FlowInstanceService service = Mockito.mock(FlowInstanceService.class, Mockito.CALLS_REAL_METHODS);
    when(service.start(anyString(), anyString(), any(), any(), any(),
        any(CompletableFuture.class))).thenReturn(Try.success("inst-1"));

    Try<String> result = service.start("flow-a", "user-1", null, null, null, (Map<String, Object>) null);

    assertThat(result.get()).isEqualTo("inst-1");
  }
}
