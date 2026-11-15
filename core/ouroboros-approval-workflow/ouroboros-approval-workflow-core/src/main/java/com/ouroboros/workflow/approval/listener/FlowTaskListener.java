package com.ouroboros.workflow.approval.listener;

import com.ouroboros.workflow.approval.model.events.FlowTaskEvent;

/**
 * 流程任务监听器
 */
public interface FlowTaskListener {
  default void onEvent(FlowTaskEvent event) {
    if (event.getFactKind() == FlowTaskEvent.FactKind.ACTION_COMPLETED) {
      onActionCompleted(event);
      return;
    }
    switch (event.getState()) {
      case CREATED:
        onCreated(event);
        break;
      case PROCESSING:
        onProcessing(event);
        break;
      case TIMEOUT_REMIND:
        onTimeoutRemind(event);
        break;
      case APPROVED:
        onApproved(event);
        break;
      case REJECTED:
        onRejected(event);
        break;
      case HANDLED:
        onHandled(event);
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
      case UNCREATED:
        break;
    }
  }

  default void onActionCompleted(FlowTaskEvent event) {
  }

  /**
   * 任务创建
   *
   * @param event
   */
  default void onCreated(FlowTaskEvent event) {

  }

  /**
   * 任务通过
   *
   * @param event
   */
  default void onApproved(FlowTaskEvent event) {

  }

  /**
   * 任务拒绝
   *
   * @param event
   */
  default void onRejected(FlowTaskEvent event) {

  }

  default void onHandled(FlowTaskEvent event) {

  }

  default void onProcessing(FlowTaskEvent event) {

  }

  default void onTimeoutRemind(FlowTaskEvent event) {

  }

  /**
   * 挂起（未实现）
   *
   * @param event
   */
  default void onSuspended(FlowTaskEvent event) {
  }

  /**
   * 恢复（未实现）
   *
   * @param event
   */
  default void onResumed(FlowTaskEvent event) {
  }

  /**
   * 取消（未实现）
   *
   * @param event
   */
  default void onCanceled(FlowTaskEvent event) {
  }
}
