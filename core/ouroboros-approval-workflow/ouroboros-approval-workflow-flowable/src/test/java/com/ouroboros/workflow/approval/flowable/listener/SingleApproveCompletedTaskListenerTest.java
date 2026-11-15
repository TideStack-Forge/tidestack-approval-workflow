package com.ouroboros.workflow.approval.flowable.listener;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.Collections;

import org.flowable.task.service.delegate.DelegateTask;
import org.junit.jupiter.api.Test;

import com.ouroboros.workflow.approval.event.TaskEventDispatcher;
import com.ouroboros.workflow.approval.flowable.service.FlowableAddSignTaskManager;
import com.ouroboros.workflow.approval.flowable.transpiler.Constants;

class SingleApproveCompletedTaskListenerTest {
  @Test
  void notifyPromotesApproveActionToFlowVariables() {
    DelegateTask task = mock(DelegateTask.class);
    SingleApproveCompletedTaskListener listener = newListener();

    when(task.getTaskDefinitionKey()).thenReturn("approve-a");
    when(task.getVariableLocal(Constants.Variable.LOCAL_APPROVAL_ACTION, String.class)).thenReturn(Constants.Value.APPROVED);

    listener.notify(task);

    verify(task).setVariableLocal(Constants.Variable.TASK_APPROVED, true);
    verify(task).setVariable("approve-a" + Constants.Variable.APPROVAL_ACTION_SUFFIX, Constants.Value.APPROVED);
    verify(task).setVariable("approve-a" + Constants.Variable.APPROVED_SUFFIX, true);
  }

  @Test
  void notifyPromotesRejectActionToFlowVariables() {
    DelegateTask task = mock(DelegateTask.class);
    SingleApproveCompletedTaskListener listener = newListener();

    when(task.getTaskDefinitionKey()).thenReturn("approve-a");
    when(task.getVariableLocal(Constants.Variable.LOCAL_APPROVAL_ACTION, String.class)).thenReturn(Constants.Value.REJECTED);

    listener.notify(task);

    verify(task).setVariableLocal(Constants.Variable.TASK_APPROVED, false);
    verify(task).setVariable("approve-a" + Constants.Variable.APPROVAL_ACTION_SUFFIX, Constants.Value.REJECTED);
    verify(task).setVariable("approve-a" + Constants.Variable.APPROVED_SUFFIX, false);
  }

  @Test
  void notifyActivatesAfterCurrentIndependentAddSignGroupWhenPendingFlagExists() {
    DelegateTask task = mock(DelegateTask.class);
    FlowableAddSignTaskManager addSignTaskManager = mock(FlowableAddSignTaskManager.class);
    SingleApproveCompletedTaskListener listener = newListener(addSignTaskManager);

    when(task.getTaskDefinitionKey()).thenReturn("approve-a");
    when(task.getProcessInstanceId()).thenReturn("instance-1");
    when(task.getVariableLocal(Constants.Variable.LOCAL_APPROVAL_ACTION, String.class)).thenReturn(Constants.Value.APPROVED);
    when(task.getVariableLocal(Constants.Variable.ADD_SIGN_AFTER_CURRENT_PENDING, Boolean.class)).thenReturn(Boolean.TRUE);
    when(task.getVariableLocal(Constants.Variable.ADD_SIGN_GROUP_ID, String.class)).thenReturn("group-1");

    listener.notify(task);

    verify(addSignTaskManager).activateAfterCurrentGroup("instance-1", "group-1");
    verify(task).removeVariableLocal(Constants.Variable.ADD_SIGN_AFTER_CURRENT_PENDING);
  }

  private SingleApproveCompletedTaskListener newListener() {
    return newListener(null);
  }

  private SingleApproveCompletedTaskListener newListener(FlowableAddSignTaskManager addSignTaskManager) {
    SingleApproveCompletedTaskListener listener = new SingleApproveCompletedTaskListener();
    TaskEventDispatcher dispatcher = new TaskEventDispatcher();
    setField(dispatcher, "listeners", Collections.emptyList());
    setField(listener, "taskEventDispatcher", dispatcher);
    setField(listener, "addSignTaskManager", addSignTaskManager);
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
