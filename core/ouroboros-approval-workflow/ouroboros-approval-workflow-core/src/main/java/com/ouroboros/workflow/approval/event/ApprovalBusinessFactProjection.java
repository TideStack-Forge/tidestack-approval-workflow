package com.ouroboros.workflow.approval.event;

import com.ouroboros.workflow.approval.callback.CallbackBinding;
import com.ouroboros.workflow.approval.model.events.FlowInstanceEvent;
import com.ouroboros.workflow.approval.model.events.FlowTaskEvent;

/** Transfers a source fact to the business owner at the existing Outbox/callback boundary. */
public interface ApprovalBusinessFactProjection {
  /** The same owner also coordinates legacy case updates with its required business callback. */
  default boolean ownsInstance(FlowInstanceEvent event) { return false; }

  /** The owner commits receipt, required callback and business Event together, or propagates failure. */
  boolean projectInstance(FlowInstanceEvent event, CallbackBinding binding, Runnable requiredCallback);

  /** Task facts never execute a lifecycle callback or advance its state cursor. */
  boolean projectTask(FlowTaskEvent event);
}
