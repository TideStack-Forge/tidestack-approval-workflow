package com.ouroboros.workflow.approval.flowable.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.ouroboros.workflow.approval.event.TaskEventDispatcher;
import com.ouroboros.workflow.approval.flowable.transpiler.Constants;
import com.ouroboros.workflow.approval.model.TaskState;
import com.ouroboros.workflow.approval.model.events.FlowTaskEvent;

import io.vavr.control.Try;

/**
 * M7 催办动作测试：权限、幂等、事件发布。
 */
class RemindActionTest {

  private org.flowable.engine.TaskService taskService;
  private TaskEventDispatcher taskEventDispatcher;
  private FlowableFlowTaskService service;

  @BeforeEach
  void setUp() {
    taskService = mock(org.flowable.engine.TaskService.class);
    taskEventDispatcher = mock(TaskEventDispatcher.class);
    service = new FlowableFlowTaskService(taskService, Collections.emptyList());
    service.historyService = mock(HistoryService.class);
    org.flowable.variable.api.history.HistoricVariableInstanceQuery historyQuery =
        mock(org.flowable.variable.api.history.HistoricVariableInstanceQuery.class);
    when(service.historyService.createHistoricVariableInstanceQuery()).thenReturn(historyQuery);
    when(historyQuery.processInstanceId(anyString())).thenReturn(historyQuery);
    when(historyQuery.variableName(anyString())).thenReturn(historyQuery);
    when(historyQuery.singleResult()).thenReturn(null);
    service.repositoryService = mock(RepositoryService.class);
    org.flowable.engine.repository.ProcessDefinitionQuery definitionQuery =
        mock(org.flowable.engine.repository.ProcessDefinitionQuery.class);
    when(service.repositoryService.createProcessDefinitionQuery()).thenReturn(definitionQuery);
    when(definitionQuery.processDefinitionId(anyString())).thenReturn(definitionQuery);
    when(definitionQuery.singleResult()).thenReturn(null);
    service.runtimeService = mock(RuntimeService.class);
    service.taskEventDispatcher = taskEventDispatcher;
  }

  private Task stubActiveTask(String taskId, String assignee) {
    Task task = mock(Task.class);
    when(task.getId()).thenReturn(taskId);
    when(task.getName()).thenReturn("审批任务");
    when(task.getAssignee()).thenReturn(assignee);
    when(task.getProcessInstanceId()).thenReturn("inst-1");
    when(task.getProcessDefinitionId()).thenReturn("pd-1");
    TaskQuery query = mock(TaskQuery.class);
    when(taskService.createTaskQuery()).thenReturn(query);
    when(query.taskId(taskId)).thenReturn(query);
    when(query.singleResult()).thenReturn(task);
    when(taskService.getIdentityLinksForTask(taskId)).thenReturn(List.of());
    return task;
  }

  @Test
  void remindByAssigneeDispatchesTimeoutRemindEvent() {
    stubActiveTask("task-1", "user-1");
    when(taskService.getVariable("task-1", Constants.Variable.REMIND_LAST_AT, LocalDateTime.class))
        .thenReturn(null);

    Try<Void> result = service.remind("task-1", "user-1");

    assertThat(result.isSuccess()).isTrue();
    verify(taskService).setVariableLocal(org.mockito.ArgumentMatchers.eq("task-1"),
        org.mockito.ArgumentMatchers.eq(Constants.Variable.REMIND_LAST_AT), any());
    ArgumentCaptor<FlowTaskEvent> captor = ArgumentCaptor.forClass(FlowTaskEvent.class);
    verify(taskEventDispatcher).dispatchEvent(captor.capture());
    assertThat(captor.getValue().getTaskId()).isEqualTo("task-1");
    assertThat(captor.getValue().getState()).isEqualTo(TaskState.TIMEOUT_REMIND);
  }

  @Test
  void remindByUnauthorizedUserFails() {
    stubActiveTask("task-1", "user-1");
    when(taskService.getVariable("task-1", Constants.Variable.REMIND_LAST_AT, LocalDateTime.class))
        .thenReturn(null);

    Try<Void> result = service.remind("task-1", "stranger");

    assertThat(result.isFailure()).isTrue();
    assertThat(result.getCause().getMessage()).contains("无权催办");
    verify(taskEventDispatcher, never()).dispatchEvent(any());
  }

  @Test
  void remindIsIdempotentWithinWindow() {
    stubActiveTask("task-1", "user-1");
    when(taskService.getVariable("task-1", Constants.Variable.REMIND_LAST_AT, LocalDateTime.class))
        .thenReturn(LocalDateTime.now());

    Try<Void> result = service.remind("task-1", "user-1");

    assertThat(result.isSuccess()).isTrue();
    verify(taskService, never()).setVariableLocal(anyString(), anyString(), any());
    verify(taskEventDispatcher, never()).dispatchEvent(any());
  }

  @Test
  void remindMissingTaskFails() {
    TaskQuery query = mock(TaskQuery.class);
    when(taskService.createTaskQuery()).thenReturn(query);
    when(query.taskId("missing")).thenReturn(query);
    when(query.singleResult()).thenReturn(null);

    Try<Void> result = service.remind("missing", "user-1");

    assertThat(result.isFailure()).isTrue();
    assertThat(result.getCause().getMessage()).contains("任务不存在");
  }
}
