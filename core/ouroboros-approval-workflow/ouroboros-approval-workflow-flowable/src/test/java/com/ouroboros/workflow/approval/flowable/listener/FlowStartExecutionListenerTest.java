package com.ouroboros.workflow.approval.flowable.listener;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.flowable.engine.RepositoryService;
import org.flowable.engine.impl.persistence.entity.ExecutionEntity;
import org.flowable.engine.repository.ProcessDefinition;
import org.junit.jupiter.api.Test;

import com.ouroboros.workflow.approval.event.InstanceEventDispatcher;
import com.ouroboros.workflow.approval.flowable.transpiler.Constants;
import com.ouroboros.workflow.approval.listener.FlowInstanceListener;
import com.ouroboros.workflow.approval.model.events.FlowInstanceEvent;

class FlowStartExecutionListenerTest {
  @Test
  void notifyUsesExplicitFlowKeyWhenRuntimeVariableExists() {
    CollectingFlowInstanceListener collector = new CollectingFlowInstanceListener();
    FlowStartExecutionListener listener = newListener(null, newDispatcher(collector));

    ExecutionEntity execution = mockExecution("instance-1");
    when(execution.getProcessInstanceId()).thenReturn("instance-1");
    when(execution.getVariable(Constants.Variable.PROCESS_FLOW_KEY, String.class)).thenReturn("parent-flow");
    when(execution.getVariable(Constants.Variable.PROCESS_STARTER, String.class)).thenReturn("starter-1");

    listener.notify(execution);

    FlowInstanceEvent event = collector.lastEvent();
    assertEquals(FlowInstanceEvent.EventType.STARTED, event.getEventType());
    assertEquals("parent-flow", event.getFlowKey());
    assertEquals("starter-1", event.getStarter());
  }

  @Test
  void notifyFallsBackToProcessDefinitionKeyWhenFlowKeyVariableMissing() {
    RepositoryService repositoryService = mock(RepositoryService.class);
    ProcessDefinition processDefinition = mock(ProcessDefinition.class);
    when(repositoryService.getProcessDefinition("process-definition-child")).thenReturn(processDefinition);
    when(processDefinition.getKey()).thenReturn("child-smoke-flow");

    CollectingFlowInstanceListener collector = new CollectingFlowInstanceListener();
    FlowStartExecutionListener listener = newListener(repositoryService, newDispatcher(collector));

    ExecutionEntity execution = mockExecution("instance-child-1");
    when(execution.getProcessInstanceId()).thenReturn("instance-child-1");
    when(execution.getProcessDefinitionId()).thenReturn("process-definition-child");
    when(execution.getVariable(Constants.Variable.PROCESS_FLOW_KEY, String.class)).thenReturn(null);
    when(execution.getVariable(Constants.Variable.PROCESS_STARTER, String.class)).thenReturn("starter-1");

    listener.notify(execution);

    FlowInstanceEvent event = collector.lastEvent();
    assertEquals(FlowInstanceEvent.EventType.STARTED, event.getEventType());
    assertEquals("child-smoke-flow", event.getFlowKey());
    assertEquals("starter-1", event.getStarter());
  }

  @Test
  void notifyLeavesFlowKeyNullWhenRepositoryServiceMissing() {
    CollectingFlowInstanceListener collector = new CollectingFlowInstanceListener();
    FlowStartExecutionListener listener = newListener(null, newDispatcher(collector));

    ExecutionEntity execution = mockExecution("instance-child-2");
    when(execution.getProcessInstanceId()).thenReturn("instance-child-2");
    when(execution.getProcessDefinitionId()).thenReturn("process-definition-child");
    when(execution.getVariable(Constants.Variable.PROCESS_FLOW_KEY, String.class)).thenReturn(null);
    when(execution.getVariable(Constants.Variable.PROCESS_STARTER, String.class)).thenReturn("starter-2");

    assertDoesNotThrow(() -> listener.notify(execution));

    FlowInstanceEvent event = collector.lastEvent();
    assertEquals(FlowInstanceEvent.EventType.STARTED, event.getEventType());
    assertEquals(null, event.getFlowKey());
    assertEquals("starter-2", event.getStarter());
  }

  @Test
  void notifyPropagatesInstanceListenerFailures() {
    FlowStartExecutionListener listener =
        newListener(null, newDispatcher(new ThrowingFlowInstanceListener("dispatch failed")));

    ExecutionEntity execution = mockExecution("instance-throw-1");
    when(execution.getProcessInstanceId()).thenReturn("instance-throw-1");
    when(execution.getVariable(Constants.Variable.PROCESS_FLOW_KEY, String.class)).thenReturn("parent-flow");
    when(execution.getVariable(Constants.Variable.PROCESS_STARTER, String.class)).thenReturn("starter-1");

    RuntimeException error = assertThrows(RuntimeException.class, () -> listener.notify(execution));
    assertEquals("dispatch failed", error.getMessage());
  }

  private ExecutionEntity mockExecution(String processInstanceId) {
    ExecutionEntity execution = mock(ExecutionEntity.class);
    ExecutionEntity processInstance = mock(ExecutionEntity.class);

    when(execution.getProcessInstance()).thenReturn(processInstance);
    when(processInstance.getSuperExecution()).thenReturn(null);
    when(processInstance.getProcessInstanceId()).thenReturn(processInstanceId);
    return execution;
  }

  private FlowStartExecutionListener newListener(
      RepositoryService repositoryService,
      InstanceEventDispatcher dispatcher
  ) {
    FlowStartExecutionListener listener = new FlowStartExecutionListener();
    setField(listener, "repositoryService", repositoryService);
    setField(listener, "instanceEventDispatcher", dispatcher);
    return listener;
  }

  private InstanceEventDispatcher newDispatcher(FlowInstanceListener listener) {
    InstanceEventDispatcher dispatcher = new InstanceEventDispatcher();
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

  private static final class CollectingFlowInstanceListener implements FlowInstanceListener {
    private final List<FlowInstanceEvent> events = new ArrayList<FlowInstanceEvent>();

    @Override
    public void onEvent(FlowInstanceEvent event) {
      events.add(event);
    }

    private FlowInstanceEvent lastEvent() {
      if (events.isEmpty()) {
        throw new AssertionError("No instance event captured");
      }
      return events.get(events.size() - 1);
    }
  }

  private static final class ThrowingFlowInstanceListener implements FlowInstanceListener {
    private final String message;

    private ThrowingFlowInstanceListener(String message) {
      this.message = message;
    }

    @Override
    public void onEvent(FlowInstanceEvent event) {
      throw new RuntimeException(message);
    }
  }
}
