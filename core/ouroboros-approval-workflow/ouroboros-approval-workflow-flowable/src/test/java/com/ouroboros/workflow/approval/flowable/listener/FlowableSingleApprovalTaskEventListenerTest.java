package com.ouroboros.workflow.approval.flowable.listener;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.flowable.engine.RepositoryService;
import org.flowable.engine.TaskService;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.task.service.delegate.DelegateTask;
import org.junit.jupiter.api.Test;

import com.ouroboros.workflow.approval.event.TaskEventDispatcher;
import com.ouroboros.workflow.approval.flowable.transpiler.Constants;
import com.ouroboros.workflow.approval.listener.FlowTaskListener;
import com.ouroboros.workflow.approval.model.TaskState;
import com.ouroboros.workflow.approval.model.events.FlowTaskEvent;

class FlowableSingleApprovalTaskEventListenerTest {
  @Test
  void createSingleApprovalEventFallsBackToProcessDefinitionKeyWhenFlowKeyMissing() {
    EventCollector collector = installTaskEventCollector();

    CreateSingleApprovalTaskListener taskListener = newCreateSingleListener(
        mockRepositoryService("process-definition-child", "child-smoke-flow"),
        null,
        collector.dispatcher);
    taskListener.dispatchEvent(mockTask("task-1", "approve-a", null, dateOf(2026, Month.MARCH, 19, 20, 0)),
        Collections.singletonList("user-a"));

    FlowTaskEvent event = collector.listener.lastEvent();
    assertEquals("child-smoke-flow", event.getFlowKey());
    assertEquals(TaskState.CREATED, event.getState());
    assertEquals(Collections.singletonList("user-a"), event.getCandidateUsers());
  }

  @Test
  void createSingleApprovalEventUsesCurrentTimeWhenCreateTimeMissing() {
    EventCollector collector = installTaskEventCollector();

    CreateSingleApprovalTaskListener taskListener = newCreateSingleListener(null, null, collector.dispatcher);
    taskListener.dispatchEvent(mockTask("task-create-null-time", "approve-a", "execution-flow", null),
        Collections.singletonList("user-a"));

    FlowTaskEvent event = collector.listener.lastEvent();
    assertNotNull(event.getEventTime());
    assertEquals(TaskState.CREATED, event.getState());
  }

  @Test
  void singleCompletedEventFallsBackToProcessDefinitionKeyWhenFlowKeyMissing() {
    EventCollector collector = installTaskEventCollector();

    SingleApproveCompletedTaskListener taskListener = newSingleCompletedListener(
        mockRepositoryService("process-definition-child", "child-smoke-flow"),
        null,
        collector.dispatcher);
    taskListener.dispatchEvent(mockTask("task-2", "approve-a", null, dateOf(2026, Month.MARCH, 19, 20, 0)),
        Constants.Value.APPROVED);

    FlowTaskEvent event = collector.listener.lastEvent();
    assertEquals("child-smoke-flow", event.getFlowKey());
    assertEquals(TaskState.APPROVED, event.getState());
    assertEquals(Constants.Value.APPROVED, event.getActionName());
  }

  @Test
  void singleCompletedEventLeavesFlowKeyNullWhenRepositoryServiceMissing() {
    EventCollector collector = installTaskEventCollector();

    SingleApproveCompletedTaskListener taskListener = newSingleCompletedListener(null, null, collector.dispatcher);
    assertDoesNotThrow(() -> taskListener.dispatchEvent(
        mockTask("task-3", "approve-a", null, dateOf(2026, Month.MARCH, 19, 20, 0)),
        Constants.Value.APPROVED
    ));

    FlowTaskEvent event = collector.listener.lastEvent();
    assertEquals(null, event.getFlowKey());
    assertEquals(TaskState.APPROVED, event.getState());
  }

  @Test
  void createSingleApprovalNoCandidateModeDoesNotCrashWhenEngineServicesMissing() {
    EventCollector collector = installTaskEventCollector();
    DelegateTask task = mockTask("task-no-service", "approve-a", "execution-flow", dateOf(2026, Month.MARCH, 19, 20, 0));
    Map<String, List<String>> selectedAssignees = new HashMap<String, List<String>>();

    when(task.getVariable(Constants.Variable.USER_SELECTED_ASSIGNEES)).thenReturn(selectedAssignees);
    when(task.getVariable(Constants.Variable.PROCESS_STARTER)).thenReturn("starter-1");

    assertDoesNotThrow(() -> newCreateSingleListener(null, null, collector.dispatcher).notify(task));
    assertEquals(0, collector.listener.eventCount());
  }

  @Test
  void createSingleApprovalEmptySelectedAssigneesStillFallsBackToNoCandidateHandling() {
    EventCollector collector = installTaskEventCollector();
    DelegateTask task = mockTask("task-empty-selected", "approve-a", "execution-flow", dateOf(2026, Month.MARCH, 19, 20, 0));
    Map<String, List<String>> selectedAssignees = new HashMap<String, List<String>>();
    selectedAssignees.put("approve-a", Collections.<String>emptyList());

    when(task.getVariable(Constants.Variable.USER_SELECTED_ASSIGNEES)).thenReturn(selectedAssignees);
    when(task.getVariable(Constants.Variable.PROCESS_STARTER)).thenReturn("starter-1");

    assertDoesNotThrow(() -> newCreateSingleListener(null, null, collector.dispatcher).notify(task));
    assertEquals(0, collector.listener.eventCount());
  }

  private RepositoryService mockRepositoryService(String processDefinitionId, String flowKey) {
    RepositoryService repositoryService = mock(RepositoryService.class);
    ProcessDefinition processDefinition = mock(ProcessDefinition.class);

    when(processDefinition.getKey()).thenReturn(flowKey);
    when(repositoryService.getProcessDefinition(processDefinitionId)).thenReturn(processDefinition);
    return repositoryService;
  }

  private EventCollector installTaskEventCollector() {
    TaskEventDispatcher dispatcher = new TaskEventDispatcher();
    CapturingFlowTaskListener listener = new CapturingFlowTaskListener();
    setField(dispatcher, "listeners", Collections.singletonList(listener));
    return new EventCollector(dispatcher, listener);
  }

  private CreateSingleApprovalTaskListener newCreateSingleListener(
      RepositoryService repositoryService,
      TaskService taskService,
      TaskEventDispatcher dispatcher
  ) {
    CreateSingleApprovalTaskListener listener = new CreateSingleApprovalTaskListener();
    setField(listener, "repositoryService", repositoryService);
    setField(listener, "taskService", taskService);
    setField(listener, "taskEventDispatcher", dispatcher);
    return listener;
  }

  private SingleApproveCompletedTaskListener newSingleCompletedListener(
      RepositoryService repositoryService,
      TaskService taskService,
      TaskEventDispatcher dispatcher
  ) {
    SingleApproveCompletedTaskListener listener = new SingleApproveCompletedTaskListener();
    setField(listener, "repositoryService", repositoryService);
    setField(listener, "taskService", taskService);
    setField(listener, "taskEventDispatcher", dispatcher);
    return listener;
  }

  private DelegateTask mockTask(String taskId, String nodeId, String flowKey, Date createTime) {
    DelegateTask task = mock(DelegateTask.class);

    when(task.getId()).thenReturn(taskId);
    when(task.getName()).thenReturn("审批任务");
    when(task.getAssignee()).thenReturn("user-a");
    when(task.getProcessInstanceId()).thenReturn("instance-1");
    when(task.getProcessDefinitionId()).thenReturn("process-definition-child");
    when(task.getTaskDefinitionKey()).thenReturn(nodeId);
    when(task.getCreateTime()).thenReturn(createTime);
    when(task.getVariable(Constants.Variable.PROCESS_FLOW_KEY, String.class)).thenReturn(flowKey);

    return task;
  }

  private Date dateOf(int year, Month month, int day, int hour, int minute) {
    return Date.from(LocalDateTime.of(year, month, day, hour, minute)
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

  private static final class EventCollector {
    private final TaskEventDispatcher dispatcher;
    private final CapturingFlowTaskListener listener;

    private EventCollector(TaskEventDispatcher dispatcher, CapturingFlowTaskListener listener) {
      this.dispatcher = dispatcher;
      this.listener = listener;
    }
  }
}
