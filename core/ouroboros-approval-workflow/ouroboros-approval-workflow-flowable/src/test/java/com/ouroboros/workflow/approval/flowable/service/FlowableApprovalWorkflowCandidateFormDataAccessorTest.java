package com.ouroboros.workflow.approval.flowable.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;

import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.junit.jupiter.api.Test;

import com.ouroboros.workflow.approval.flowable.transpiler.Constants;

class FlowableApprovalWorkflowCandidateFormDataAccessorTest {
  @Test
  void getFieldValueShouldReadCandidateFormDataBeforeTaskFormData() {
    RuntimeService runtimeService = mock(RuntimeService.class);
    TaskService taskService = mock(TaskService.class);
    FlowableApprovalWorkflowCandidateFormDataAccessor accessor =
        new FlowableApprovalWorkflowCandidateFormDataAccessor(runtimeService, taskService);

    when(runtimeService.getVariable("instance-1", Constants.CANDIDATE_FORM_DATA_KEY))
        .thenReturn(Collections.singletonMap("managerId", "user-from-candidate-form-data"));

    Object value = accessor.getFieldValue("instance-1", "task-1", "经理审批", "managerId");

    assertEquals("user-from-candidate-form-data", value);
    verify(taskService, never()).getVariableLocal("task-1", Constants.TASK_FORM_DATA_KEY);
  }

  @Test
  void getFieldValueShouldFallbackToTaskFormData() {
    RuntimeService runtimeService = mock(RuntimeService.class);
    TaskService taskService = mock(TaskService.class);
    FlowableApprovalWorkflowCandidateFormDataAccessor accessor =
        new FlowableApprovalWorkflowCandidateFormDataAccessor(runtimeService, taskService);

    when(runtimeService.getVariable("instance-1", Constants.CANDIDATE_FORM_DATA_KEY)).thenReturn(null);
    when(taskService.getVariableLocal("task-1", Constants.TASK_FORM_DATA_KEY))
        .thenReturn(Collections.singletonMap("managerId", "user-from-task-form"));

    Object value = accessor.getFieldValue("instance-1", "task-1", "经理审批", "managerId");

    assertEquals("user-from-task-form", value);
  }

  @Test
  void getFieldValueShouldReturnNullWhenFieldIsMissing() {
    RuntimeService runtimeService = mock(RuntimeService.class);
    TaskService taskService = mock(TaskService.class);
    FlowableApprovalWorkflowCandidateFormDataAccessor accessor =
        new FlowableApprovalWorkflowCandidateFormDataAccessor(runtimeService, taskService);

    when(runtimeService.getVariable("instance-1", Constants.CANDIDATE_FORM_DATA_KEY))
        .thenReturn(Collections.singletonMap("other", "user-a"));
    when(taskService.getVariableLocal("task-1", Constants.TASK_FORM_DATA_KEY))
        .thenReturn(Collections.singletonMap("other", "user-a"));

    assertNull(accessor.getFieldValue("instance-1", "task-1", "经理审批", "managerId"));
  }
}
