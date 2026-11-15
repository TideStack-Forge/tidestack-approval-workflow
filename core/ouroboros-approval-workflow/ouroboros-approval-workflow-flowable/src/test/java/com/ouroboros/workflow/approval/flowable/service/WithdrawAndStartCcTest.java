package com.ouroboros.workflow.approval.flowable.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.flowable.engine.RuntimeService;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.runtime.ProcessInstanceQuery;
import org.flowable.task.api.TaskQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ouroboros.workflow.approval.event.InstanceEventDispatcher;
import com.ouroboros.workflow.approval.flowable.transpiler.Constants;

import io.vavr.control.Try;

/**
 * M7 发起人撤回与发起时抄送测试（内存 mock 引擎服务）。
 */
class WithdrawAndStartCcTest {

  private RuntimeService runtimeService;
  private FlowInstanceCommandService commandService;
  private FlowableFlowInstanceService service;

  @BeforeEach
  void setUp() {
    runtimeService = mock(RuntimeService.class);
    var taskService = mock(org.flowable.engine.TaskService.class);
    var dispatcher = mock(InstanceEventDispatcher.class);
    // historyService 传 null：getLastFinishedTaskId 已对 null 兜底，避免 mock 链
    commandService = new FlowInstanceCommandService(
        taskService, runtimeService, null, null, null, List.of(), dispatcher);
    service = new FlowableFlowInstanceService(commandService, mock(FlowInstanceQueryService.class));
    service.runtimeService = runtimeService;

    TaskQuery taskQuery = mock(TaskQuery.class);
    when(taskService.createTaskQuery()).thenReturn(taskQuery);
    when(taskQuery.processInstanceId(anyString())).thenReturn(taskQuery);
    when(taskQuery.singleResult()).thenReturn(null);
  }

  private void stubRunningInstance(String instanceId) {
    ProcessInstance instance = mock(ProcessInstance.class);
    when(instance.isEnded()).thenReturn(false);
    ProcessInstanceQuery query = mock(ProcessInstanceQuery.class);
    when(query.processInstanceId(instanceId)).thenReturn(query);
    when(query.singleResult()).thenReturn(instance);
    when(runtimeService.createProcessInstanceQuery()).thenReturn(query);
    when(runtimeService.getVariable(instanceId, Constants.Variable.PROCESS_STARTER, String.class))
        .thenReturn("starter-1");
  }

  @Test
  void withdrawByNonStarterFails() {
    stubRunningInstance("inst-1");

    Try<Void> result = service.withdraw("inst-1", "other-user", "撤回");

    assertThat(result.isFailure()).isTrue();
    assertThat(result.getCause().getMessage()).contains("只有发起人");
    verify(runtimeService, never()).deleteProcessInstance(anyString(), any());
  }

  @Test
  void withdrawByStarterSucceedsWithWithdrawnOutcome() {
    stubRunningInstance("inst-1");

    Try<Void> result = service.withdraw("inst-1", "starter-1", "撤回");

    assertThat(result.isSuccess()).isTrue();
    verify(runtimeService).setVariable("inst-1", Constants.Variable.PROCESS_OUTCOME, "withdrawn");
    verify(runtimeService).deleteProcessInstance("inst-1", "撤回");
  }

  @Test
  void startWithStartPositionCcAddsParticipantLinks() {
    commandService = mock(FlowInstanceCommandService.class);
    service = new FlowableFlowInstanceService(commandService, mock(FlowInstanceQueryService.class));
    service.runtimeService = runtimeService;
    when(commandService.start(anyString(), anyString(), any(), any(), any(), any()))
        .thenReturn(Try.success("inst-1"));

    Try<String> result = service.start(
        "flow-a", "user-1", null, null, null,
        Map.of("userIds", List.of("user-2", "user-3"), "position", "START"));

    assertThat(result.get()).isEqualTo("inst-1");
    verify(runtimeService).addUserIdentityLink("inst-1", "user-2", org.flowable.identitylink.api.IdentityLinkType.PARTICIPANT);
    verify(runtimeService).addUserIdentityLink("inst-1", "user-3", org.flowable.identitylink.api.IdentityLinkType.PARTICIPANT);
    verify(runtimeService, never()).setVariable(anyString(), anyString(), any());
  }

  @Test
  void startWithFinishPositionCcStoresUsersForEndListener() {
    commandService = mock(FlowInstanceCommandService.class);
    service = new FlowableFlowInstanceService(commandService, mock(FlowInstanceQueryService.class));
    service.runtimeService = runtimeService;
    when(commandService.start(anyString(), anyString(), any(), any(), any(), any()))
        .thenReturn(Try.success("inst-1"));

    Try<String> result = service.start(
        "flow-a", "user-1", null, null, null,
        Map.of("userIds", List.of("user-2"), "position", "FINISH"));

    assertThat(result.get()).isEqualTo("inst-1");
    verify(runtimeService, never()).addUserIdentityLink(anyString(), anyString(), any());
    verify(runtimeService).setVariable("inst-1", Constants.Variable.START_CC_FINISH_USERS, List.of("user-2"));
  }

  @Test
  void startWithoutCcIsUnaffected() {
    commandService = mock(FlowInstanceCommandService.class);
    service = new FlowableFlowInstanceService(commandService, mock(FlowInstanceQueryService.class));
    service.runtimeService = runtimeService;
    when(commandService.start(anyString(), anyString(), any(), any(), any(), any()))
        .thenReturn(Try.success("inst-1"));

    Try<String> result = service.start("flow-a", "user-1", null, null, null, (Map<String, Object>) null);

    assertThat(result.get()).isEqualTo("inst-1");
    verify(runtimeService, never()).addUserIdentityLink(anyString(), anyString(), any());
    verify(runtimeService, never()).setVariable(anyString(), anyString(), any());
  }
}
