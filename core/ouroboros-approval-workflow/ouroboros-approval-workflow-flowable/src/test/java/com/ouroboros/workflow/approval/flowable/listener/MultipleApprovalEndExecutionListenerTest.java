package com.ouroboros.workflow.approval.flowable.listener;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.flowable.bpmn.model.FlowElement;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.TaskService;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.flowable.task.api.history.HistoricTaskInstanceQuery;
import org.flowable.variable.api.history.HistoricVariableInstance;
import org.flowable.variable.api.history.HistoricVariableInstanceQuery;
import org.junit.jupiter.api.Test;

import com.ouroboros.workflow.approval.event.TaskEventDispatcher;
import com.ouroboros.workflow.approval.flowable.transpiler.Constants;
import com.ouroboros.workflow.approval.listener.FlowTaskListener;
import com.ouroboros.workflow.approval.model.TaskState;
import com.ouroboros.workflow.approval.model.events.FlowTaskEvent;

class MultipleApprovalEndExecutionListenerTest {
  @Test
  void notifySkipsEventWhenHistoryServiceMissing() {
    CapturingFlowTaskListener collector = new CapturingFlowTaskListener();
    MultipleApprovalEndExecutionListener listener = newListener(null, null, null, newDispatcher(collector));
    DelegateExecution execution = mockExecution(Boolean.TRUE, "task-1");

    assertDoesNotThrow(() -> listener.notify(execution));

    assertEquals(0, collector.eventCount());
  }

  @Test
  void notifyRejectEventLeavesCommentNullWhenTaskServiceMissing() {
    HistoryService historyService = mock(HistoryService.class);
    HistoricTaskInstanceQuery taskQuery = mock(HistoricTaskInstanceQuery.class);
    HistoricTaskInstance historicTask = mock(HistoricTaskInstance.class);
    HistoricVariableInstanceQuery variableQuery = mock(HistoricVariableInstanceQuery.class);
    HistoricVariableInstance actionVariable = mock(HistoricVariableInstance.class);

    when(historyService.createHistoricTaskInstanceQuery()).thenReturn(taskQuery);
    when(taskQuery.taskId("task-9")).thenReturn(taskQuery);
    when(taskQuery.singleResult()).thenReturn(historicTask);

    when(historicTask.getId()).thenReturn("task-9");
    when(historicTask.getName()).thenReturn("会签审批");
    when(historicTask.getAssignee()).thenReturn("approver-1");

    when(historyService.createHistoricVariableInstanceQuery()).thenReturn(variableQuery);
    when(variableQuery.processInstanceId("instance-1")).thenReturn(variableQuery);
    when(variableQuery.taskId("task-9")).thenReturn(variableQuery);
    when(variableQuery.variableName(Constants.Variable.LOCAL_APPROVAL_ACTION)).thenReturn(variableQuery);
    when(variableQuery.singleResult()).thenReturn(actionVariable);
    when(actionVariable.getValue()).thenReturn(Constants.Value.REJECTED);

    CapturingFlowTaskListener collector = new CapturingFlowTaskListener();
    MultipleApprovalEndExecutionListener listener =
        newListener(historyService, null, null, newDispatcher(collector));
    DelegateExecution execution = mockExecution(Boolean.FALSE, "task-9");

    assertDoesNotThrow(() -> listener.notify(execution));

    FlowTaskEvent event = collector.lastEvent();
    assertEquals("task-9", event.getTaskId());
    assertEquals("会签审批", event.getTaskName());
    assertEquals("approver-1", event.getAssignee());
    assertEquals(Collections.singletonList("user-a"), event.getCandidateUsers());
    assertEquals("flow-a", event.getFlowKey());
    assertEquals(TaskState.REJECTED, event.getState());
    assertEquals("instance-1", event.getInstanceId());
    assertEquals(Constants.Value.REJECTED, event.getActionName());
    assertEquals(null, event.getComment());
  }

  private MultipleApprovalEndExecutionListener newListener(
      HistoryService historyService,
      TaskService taskService,
      RepositoryService repositoryService,
      TaskEventDispatcher dispatcher
  ) {
    MultipleApprovalEndExecutionListener listener = new MultipleApprovalEndExecutionListener();
    setField(listener, "historyService", historyService);
    setField(listener, "taskService", taskService);
    setField(listener, "repositoryService", repositoryService);
    setField(listener, "taskEventDispatcher", dispatcher);
    return listener;
  }

  private DelegateExecution mockExecution(Boolean approved, String finalTaskId) {
    DelegateExecution execution = mock(DelegateExecution.class);
    FlowElement flowElement = mock(FlowElement.class);

    when(flowElement.getId()).thenReturn("multi-a");
    when(execution.isMultiInstanceRoot()).thenReturn(true);
    when(execution.getCurrentFlowElement()).thenReturn(flowElement);
    when(execution.getProcessInstanceId()).thenReturn("instance-1");
    when(execution.getProcessDefinitionId()).thenReturn("process-definition-1");
    when(execution.getVariable("multi-a" + Constants.Variable.MULTIPLE_APPROVAL_FINAL_TASK_ID_SUFFIX, String.class))
        .thenReturn(finalTaskId);
    // 生产代码读的是无类型 getVariable(String)（类型校验集中到 BpmnUtil.readStringListVariable），
    // 因此这里 stub 无类型重载；stub 带 Class 的重载会被 mock 当作未命中而返回 null。
    when(execution.getVariable(Constants.Variable.getMultipleApprovalAssigneeListVariableName("multi-a")))
        .thenReturn(Collections.singletonList("user-a"));
    when(execution.getVariable("multi-a" + Constants.Variable.APPROVED_SUFFIX, Boolean.class)).thenReturn(approved);
    when(execution.getVariable(Constants.Variable.PROCESS_FLOW_KEY, String.class)).thenReturn("flow-a");

    return execution;
  }

  private TaskEventDispatcher newDispatcher(FlowTaskListener listener) {
    TaskEventDispatcher dispatcher = new TaskEventDispatcher();
    setField(dispatcher, "listeners", Collections.singletonList(listener));
    return dispatcher;
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
    private final List<FlowTaskEvent> events = new ArrayList<FlowTaskEvent>();

    @Override
    public void onEvent(FlowTaskEvent event) {
      events.add(event);
    }

    private FlowTaskEvent lastEvent() {
      if (events.isEmpty()) {
        throw new AssertionError("No task event captured");
      }
      return events.get(events.size() - 1);
    }

    private int eventCount() {
      return events.size();
    }
  }
}
