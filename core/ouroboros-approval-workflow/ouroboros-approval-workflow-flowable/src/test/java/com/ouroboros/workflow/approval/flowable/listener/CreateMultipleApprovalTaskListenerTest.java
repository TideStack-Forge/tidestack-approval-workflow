package com.ouroboros.workflow.approval.flowable.listener;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.Collections;

import org.flowable.task.service.delegate.DelegateTask;
import org.junit.jupiter.api.Test;

import com.ouroboros.workflow.approval.event.TaskEventDispatcher;
import com.ouroboros.workflow.approval.flowable.transpiler.Constants;

class CreateMultipleApprovalTaskListenerTest {
  @Test
  void notifyCopiesSessionToTaskLocalVariable() {
    DelegateTask task = mock(DelegateTask.class);
    CreateMultipleApprovalTaskListener listener = newListener();
    when(task.getTaskDefinitionKey()).thenReturn("multi-a");
    when(task.getVariable(Constants.Variable.getMultipleApprovalSessionVariableName("multi-a"))).thenReturn("session-1");

    listener.notify(task);

    verify(task).setVariableLocal(Constants.Variable.MULTIPLE_APPROVAL_SESSION, "session-1");
  }

  private CreateMultipleApprovalTaskListener newListener() {
    CreateMultipleApprovalTaskListener listener = new CreateMultipleApprovalTaskListener();
    TaskEventDispatcher dispatcher = new TaskEventDispatcher();
    setField(dispatcher, "listeners", Collections.emptyList());
    setField(listener, "taskEventDispatcher", dispatcher);
    return listener;
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
}
