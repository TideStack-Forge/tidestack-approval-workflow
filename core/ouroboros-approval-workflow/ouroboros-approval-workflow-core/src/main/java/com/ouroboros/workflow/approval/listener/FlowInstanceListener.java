package com.ouroboros.workflow.approval.listener;

import com.ouroboros.workflow.approval.model.events.FlowInstanceEvent;

/**
 * 流程实例监听器
 */
public interface FlowInstanceListener {
  default void onEvent(FlowInstanceEvent event) {
    switch (event.getEventType()) {
      case STARTED:
        onStarted(event);
        break;
      case APPROVED:
        onApproved(event);
        break;
      case REJECTED:
        onRejected(event);
        break;
      case SUSPENDED:
        onSuspended(event);
        break;
      case RESUMED:
        onResumed(event);
        break;
      case CANCELLED:
        onCanceled(event);
        break;
      case FAILED:
        onFailed(event);
        break;
    }
  }

  default void onStarted(FlowInstanceEvent event) {
  }

  default void onApproved(FlowInstanceEvent event) {
  }

  default void onRejected(FlowInstanceEvent event) {
  }

  default void onSuspended(FlowInstanceEvent event) {
  }

  default void onResumed(FlowInstanceEvent event) {
  }

  default void onCanceled(FlowInstanceEvent event) {
  }

  default void onFailed(FlowInstanceEvent event) {
  }
}
