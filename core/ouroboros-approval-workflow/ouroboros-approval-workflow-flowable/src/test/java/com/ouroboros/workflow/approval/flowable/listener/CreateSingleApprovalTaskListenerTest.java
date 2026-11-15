package com.ouroboros.workflow.approval.flowable.listener;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.flowable.task.service.delegate.DelegateTask;
import org.junit.jupiter.api.Test;

import com.ouroboros.workflow.approval.event.TaskEventDispatcher;
import com.ouroboros.workflow.approval.flowable.transpiler.Constants;

class CreateSingleApprovalTaskListenerTest {
  @Test
  void notifyPrefersUserSelectedAssignees() {
    DelegateTask task = mock(DelegateTask.class);
    CreateSingleApprovalTaskListener listener = newListener();

    Map<String, List<String>> userSelectedAssignees = new HashMap<String, List<String>>();
    userSelectedAssignees.put("approve-a", Collections.singletonList("user-selected"));

    when(task.getTaskDefinitionKey()).thenReturn("approve-a");
    when(task.getVariable(Constants.Variable.USER_SELECTED_ASSIGNEES)).thenReturn(userSelectedAssignees);

    listener.notify(task);

    verify(task).setAssignee("user-selected");
  }

  private CreateSingleApprovalTaskListener newListener() {
    CreateSingleApprovalTaskListener listener = new CreateSingleApprovalTaskListener();
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
