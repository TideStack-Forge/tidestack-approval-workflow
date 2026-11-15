package com.ouroboros.workflow.approval;

/** Engine-owned validation; inputs never establish their own business or task ownership. */
public interface ApprovalBusinessBindingValidator {
  void validate(String flowKey, String recordType, String recordId, String instanceId, String taskId);

  /** Prepare remote reads without a transaction; the returned check runs under the canonical case lock. */
  default java.util.function.Supplier<ApprovalInstanceState> prepareInstanceInspection(String flowKey, String instanceId) {
    return () -> inspectInstance(flowKey, instanceId);
  }

  /** Called in the receiver transaction after locking the canonical case and source cursor. */
  default ApprovalInstanceState inspectInstance(String flowKey, String instanceId) {
    throw new com.ouroboros.exception.BusinessException("审批引擎尚不支持可信状态基线核验，保留原 Outbox 责任");
  }
}
