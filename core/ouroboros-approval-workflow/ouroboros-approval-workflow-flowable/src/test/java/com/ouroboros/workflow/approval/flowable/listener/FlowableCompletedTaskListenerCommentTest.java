package com.ouroboros.workflow.approval.flowable.listener;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.flowable.engine.TaskService;
import org.flowable.engine.task.Comment;
import org.flowable.identitylink.api.IdentityLink;
import org.flowable.task.service.delegate.DelegateTask;
import org.junit.jupiter.api.Test;

import com.ouroboros.workflow.approval.event.TaskEventDispatcher;
import com.ouroboros.workflow.approval.flowable.transpiler.Constants;
import com.ouroboros.workflow.approval.listener.FlowTaskListener;
import com.ouroboros.workflow.approval.model.events.FlowTaskEvent;

class FlowableCompletedTaskListenerCommentTest {
  @Test
  void singleRejectEventUsesLatestTaskComment() {
    EventCollector collector = installTaskEventCollector();
    SingleApproveCompletedTaskListener listener =
        newSingleListener(mockTaskServiceWithComments("task-1"), collector.dispatcher);

    listener.dispatchEvent(mockTask("task-1", "approve-a"), Constants.Value.REJECTED);

    assertEquals("newer-comment", collector.listener.lastEvent().getComment());
  }

  @Test
  void singleRejectEventLeavesCommentNullWhenTaskServiceMissing() {
    EventCollector collector = installTaskEventCollector();
    SingleApproveCompletedTaskListener listener = newSingleListener(null, collector.dispatcher);

    assertDoesNotThrow(() -> listener.dispatchEvent(mockTask("task-2", "approve-a"), Constants.Value.REJECTED));

    assertEquals(null, collector.listener.lastEvent().getComment());
  }

  @Test
  void multipleProcessingRejectEventLeavesCommentNullWhenTaskServiceMissing() {
    EventCollector collector = installTaskEventCollector();
    MultipleApprovalCompletedTaskListener listener = newMultipleListener(null, collector.dispatcher);

    DelegateTask task = mockTask("task-3", "multi-a");
    when(task.getVariableLocal(Constants.Variable.LOCAL_APPROVAL_ACTION, String.class)).thenReturn(Constants.Value.REJECTED);
    when(task.getVariable("multi-a" + Constants.Variable.NR_OF_APPROVED_SUFFIX, Integer.class)).thenReturn(1);
    when(task.getVariable("multi-a" + Constants.Variable.NR_OF_REJECTED_SUFFIX, Integer.class)).thenReturn(0);
    when(task.getVariable("multi-a" + Constants.Variable.MULTIPLE_APPROVED_PERCENTAGE_SUFFIX, Integer.class)).thenReturn(50);
    when(task.getVariable("multi-a" + Constants.Variable.MULTIPLE_APPROVAL_IS_SEQUENTIAL_SUFFIX, Boolean.class)).thenReturn(false);
    when(task.getVariable("nrOfInstances", Integer.class)).thenReturn(3);

    assertDoesNotThrow(() -> listener.notify(task));

    assertEquals(null, collector.listener.lastEvent().getComment());
  }

  private TaskService mockTaskServiceWithComments(String taskId) {
    TaskService taskService = mock(TaskService.class);
    Comment olderComment = mock(Comment.class);
    Comment newerComment = mock(Comment.class);

    when(olderComment.getTime()).thenReturn(dateOf(2026, Month.MARCH, 19, 17, 30));
    when(olderComment.getFullMessage()).thenReturn("older-comment");
    when(newerComment.getTime()).thenReturn(dateOf(2026, Month.MARCH, 19, 17, 31));
    when(newerComment.getFullMessage()).thenReturn("newer-comment");

    when(taskService.getTaskComments(taskId)).thenReturn(Arrays.asList(olderComment, newerComment));
    return taskService;
  }

  private EventCollector installTaskEventCollector() {
    TaskEventDispatcher dispatcher = new TaskEventDispatcher();
    CapturingFlowTaskListener listener = new CapturingFlowTaskListener();
    setField(dispatcher, "listeners", Collections.singletonList(listener));
    return new EventCollector(dispatcher, listener);
  }

  private SingleApproveCompletedTaskListener newSingleListener(TaskService taskService, TaskEventDispatcher dispatcher) {
    SingleApproveCompletedTaskListener listener = new SingleApproveCompletedTaskListener();
    setField(listener, "taskService", taskService);
    setField(listener, "taskEventDispatcher", dispatcher);
    return listener;
  }

  private MultipleApprovalCompletedTaskListener newMultipleListener(TaskService taskService, TaskEventDispatcher dispatcher) {
    MultipleApprovalCompletedTaskListener listener = new MultipleApprovalCompletedTaskListener();
    setField(listener, "taskService", taskService);
    setField(listener, "taskEventDispatcher", dispatcher);
    return listener;
  }

  private DelegateTask mockTask(String taskId, String nodeId) {
    DelegateTask task = mock(DelegateTask.class);

    when(task.getId()).thenReturn(taskId);
    when(task.getName()).thenReturn("审批任务");
    when(task.getAssignee()).thenReturn("user-a");
    when(task.getCandidates()).thenReturn(Collections.<IdentityLink>emptySet());
    when(task.getProcessInstanceId()).thenReturn("instance-1");
    when(task.getProcessDefinitionId()).thenReturn("process-definition-1");
    when(task.getTaskDefinitionKey()).thenReturn(nodeId);
    when(task.getVariable(Constants.Variable.PROCESS_FLOW_KEY, String.class)).thenReturn("flow-a");
    when(task.getVariable(Constants.Variable.getMultipleApprovalAssigneeListVariableName(nodeId), List.class))
        .thenReturn(Collections.singletonList("user-a"));
    return task;
  }

  private Date dateOf(int year, Month month, int dayOfMonth, int hour, int minute) {
    return Date.from(LocalDateTime.of(year, month, dayOfMonth, hour, minute)
        .atZone(ZoneId.systemDefault())
        .toInstant());
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

    @Override
    public void onEvent(FlowTaskEvent event) {
      lastEvent = event;
    }

    private FlowTaskEvent lastEvent() {
      if (lastEvent == null) {
        throw new AssertionError("No task event captured");
      }
      return lastEvent;
    }
  }

  private static final class EventCollector {
    private final TaskEventDispatcher dispatcher;
    private final CapturingFlowTaskListener listener;

    private EventCollector(TaskEventDispatcher dispatcher, CapturingFlowTaskListener listener) {
      this.dispatcher = dispatcher;
      this.listener = listener;
    }
  }
}
