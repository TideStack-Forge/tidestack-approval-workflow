package com.ouroboros.workflow.approval;

public interface ApprovalWorkflowCandidateFormDataAccessor {
  Object getFieldValue(String processInstanceId, String taskInstanceId, String taskName, String fieldName);
}
