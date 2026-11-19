package com.ouroboros.workflow.approval;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 引擎无关的发起命令；业务记录和回调沿用 {@link FlowInstanceVariables} 契约。 */
public record FlowStartCommand(
    String flowKey,
    String userId,
    Map<String, List<String>> taskAssignee,
    Map<String, Object> instanceVariables,
    Map<String, Object> formData,
    Map<String, Object> cc,
    String idempotencyKey,
    Map<String, List<String>> approvers) {

  public FlowStartCommand(String flowKey, String userId, Map<String, List<String>> taskAssignee,
      Map<String, Object> instanceVariables, Map<String, Object> formData, Map<String, Object> cc,
      String idempotencyKey) {
    this(flowKey, userId, taskAssignee, instanceVariables, formData, cc, idempotencyKey, null);
  }

  public FlowStartCommand {
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      Object supplied = instanceVariables == null ? null : instanceVariables.get(FlowInstanceVariables.IDEMPOTENCY_KEY);
      idempotencyKey = supplied == null || supplied.toString().isBlank()
          ? UUID.randomUUID().toString() : supplied.toString();
    }
  }
}
