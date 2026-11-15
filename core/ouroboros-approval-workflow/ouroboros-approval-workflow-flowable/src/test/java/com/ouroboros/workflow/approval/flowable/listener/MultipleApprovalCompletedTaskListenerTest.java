package com.ouroboros.workflow.approval.flowable.listener;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.flowable.task.service.delegate.DelegateTask;
import org.junit.jupiter.api.Test;

import com.ouroboros.workflow.approval.flowable.transpiler.Constants;

class MultipleApprovalCompletedTaskListenerTest {
  @Test
  void notifyMarksApprovalCompletedWhenThresholdReached() {
    DelegateTask task = mock(DelegateTask.class);
    when(task.getTaskDefinitionKey()).thenReturn("multi-a");
    when(task.getVariable(Constants.Variable.getMultipleApprovalSessionVariableName("multi-a"), String.class)).thenReturn("session-1");
    when(task.getVariable("multi-a" + Constants.Variable.NR_OF_APPROVED_SUFFIX, Integer.class)).thenReturn(1);
    when(task.getVariable("multi-a" + Constants.Variable.NR_OF_REJECTED_SUFFIX, Integer.class)).thenReturn(0);
    when(task.getVariable("multi-a" + Constants.Variable.MULTIPLE_APPROVED_PERCENTAGE_SUFFIX, Integer.class)).thenReturn(66);
    when(task.getVariable("multi-a" + Constants.Variable.MULTIPLE_APPROVAL_IS_SEQUENTIAL_SUFFIX, Boolean.class)).thenReturn(false);
    when(task.getVariable("nrOfInstances", Integer.class)).thenReturn(3);
    when(task.getVariableLocal(Constants.Variable.LOCAL_APPROVAL_ACTION, String.class)).thenReturn(Constants.Value.APPROVED);

    new MultipleApprovalCompletedTaskListener().notify(task);

    verify(task).setVariableLocal(Constants.Variable.TASK_APPROVED, true);
    verify(task).setVariable("multi-a" + Constants.Variable.NR_OF_APPROVED_SUFFIX, 2);
    verify(task).setVariable("multi-a" + Constants.Variable.NR_OF_REJECTED_SUFFIX, 0);
    verify(task).setVariable("multi-a" + Constants.Variable.APPROVED_SUFFIX, true);
    verify(task).setVariable("multi-a" + Constants.Variable.MULTIPLE_APPROVAL_COMPLETED_SUFFIX, true);
    verify(task).setVariable(Constants.Variable.MULTIPLE_APPROVAL_SESSION + "session-1" + Constants.Variable.MULTIPLE_APPROVAL_COMPLETED_SUFFIX, true);
    verify(task).setVariable(Constants.Variable.MULTIPLE_APPROVAL_SESSION + "session-1" + Constants.Variable.APPROVED_SUFFIX, true);
  }

  @Test
  void notifyMarksSequentialRejectAsCompleted() {
    DelegateTask task = mock(DelegateTask.class);
    when(task.getTaskDefinitionKey()).thenReturn("multi-a");
    when(task.getVariable(Constants.Variable.getMultipleApprovalSessionVariableName("multi-a"), String.class)).thenReturn("session-2");
    when(task.getVariable("multi-a" + Constants.Variable.NR_OF_APPROVED_SUFFIX, Integer.class)).thenReturn(1);
    when(task.getVariable("multi-a" + Constants.Variable.NR_OF_REJECTED_SUFFIX, Integer.class)).thenReturn(0);
    when(task.getVariable("multi-a" + Constants.Variable.MULTIPLE_APPROVED_PERCENTAGE_SUFFIX, Integer.class)).thenReturn(100);
    when(task.getVariable("multi-a" + Constants.Variable.MULTIPLE_APPROVAL_IS_SEQUENTIAL_SUFFIX, Boolean.class)).thenReturn(true);
    when(task.getVariable("nrOfInstances", Integer.class)).thenReturn(2);
    when(task.getVariableLocal(Constants.Variable.LOCAL_APPROVAL_ACTION, String.class)).thenReturn(Constants.Value.REJECTED);

    new MultipleApprovalCompletedTaskListener().notify(task);

    verify(task).setVariableLocal(Constants.Variable.TASK_APPROVED, false);
    verify(task).setVariable("multi-a" + Constants.Variable.NR_OF_APPROVED_SUFFIX, 1);
    verify(task).setVariable("multi-a" + Constants.Variable.NR_OF_REJECTED_SUFFIX, 1);
    verify(task).setVariable("multi-a" + Constants.Variable.APPROVED_SUFFIX, false);
    verify(task).setVariable("multi-a" + Constants.Variable.MULTIPLE_APPROVAL_COMPLETED_SUFFIX, true);
    verify(task).setVariable(Constants.Variable.MULTIPLE_APPROVAL_SESSION + "session-2" + Constants.Variable.MULTIPLE_APPROVAL_COMPLETED_SUFFIX, true);
    verify(task).setVariable(Constants.Variable.MULTIPLE_APPROVAL_SESSION + "session-2" + Constants.Variable.APPROVED_SUFFIX, false);
  }
}
