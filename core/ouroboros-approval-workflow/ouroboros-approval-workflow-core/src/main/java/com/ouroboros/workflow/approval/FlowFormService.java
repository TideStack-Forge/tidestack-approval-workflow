package com.ouroboros.workflow.approval;

import java.util.List;
import java.util.Map;

import io.vavr.control.Try;

import com.ouroboros.uischema.schema.SchemaNode;
import com.ouroboros.workflow.approval.definition.NodeFormFieldPermission;

public interface FlowFormService {
  String getType();

  Try<SchemaNode> getInitialFormSchema(String formKey, List<NodeFormFieldPermission> permissions);

  Try<String> submitInitialForm(String instanceId, String formKey, String userId,
                                List<NodeFormFieldPermission> permissions, Map<String, Object> formData);

  Try<SchemaNode> getTaskFormSchema(String instanceId, String taskId, String formKey, String formId,
                                    List<NodeFormFieldPermission> permissions, List<String> actions);

  Try<Void> submitTaskForm(String instanceId, String taskId, String formKey, String formId, String userId,
                           List<NodeFormFieldPermission> permissions, Map<String, Object> formData);
}
