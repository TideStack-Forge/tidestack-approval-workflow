package com.ouroboros.workflow.approval.flowable.service;

import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ouroboros.workflow.approval.ApprovalWorkflowCandidateFormDataAccessor;
import com.ouroboros.workflow.approval.flowable.transpiler.Constants;

@Component
public class FlowableApprovalWorkflowCandidateFormDataAccessor implements ApprovalWorkflowCandidateFormDataAccessor {
  private static volatile RuntimeService sharedRuntimeService;
  private static volatile TaskService sharedTaskService;

  private RuntimeService runtimeService;
  private TaskService taskService;

  public FlowableApprovalWorkflowCandidateFormDataAccessor() {
  }

  @Autowired
  public FlowableApprovalWorkflowCandidateFormDataAccessor(RuntimeService runtimeService, TaskService taskService) {
    this.runtimeService = runtimeService;
    this.taskService = taskService;
    sharedRuntimeService = runtimeService;
    sharedTaskService = taskService;
  }

  @Override
  public Object getFieldValue(String processInstanceId, String taskInstanceId, String taskName, String fieldName) {
    if (StringUtils.isBlank(fieldName)) {
      return null;
    }

    Object candidateFormFieldValue = getCandidateFormFieldValue(processInstanceId, fieldName);
    if (candidateFormFieldValue != null) {
      return candidateFormFieldValue;
    }

    return getTaskFormFieldValue(taskInstanceId, fieldName);
  }

  private Object getCandidateFormFieldValue(String processInstanceId, String fieldName) {
    if (StringUtils.isBlank(processInstanceId) || getRuntimeService() == null) {
      return null;
    }
    Object formData = getRuntimeService().getVariable(processInstanceId, Constants.CANDIDATE_FORM_DATA_KEY);
    if (!(formData instanceof Map)) {
      return null;
    }
    return ((Map<?, ?>) formData).get(fieldName);
  }

  private Object getTaskFormFieldValue(String taskInstanceId, String fieldName) {
    if (StringUtils.isBlank(taskInstanceId) || getTaskService() == null) {
      return null;
    }
    Object formData = getTaskService().getVariableLocal(taskInstanceId, Constants.TASK_FORM_DATA_KEY);
    if (!(formData instanceof Map)) {
      return null;
    }
    return ((Map<?, ?>) formData).get(fieldName);
  }

  private RuntimeService getRuntimeService() {
    return runtimeService != null ? runtimeService : sharedRuntimeService;
  }

  private TaskService getTaskService() {
    return taskService != null ? taskService : sharedTaskService;
  }
}
