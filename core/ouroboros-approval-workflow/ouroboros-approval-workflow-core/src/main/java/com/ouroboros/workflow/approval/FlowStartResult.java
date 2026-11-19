package com.ouroboros.workflow.approval;

import com.ouroboros.exception.BusinessException;

/**
 * 统一发起结果。executionId 是平台执行引用，不承诺存在本地任务；已受理不等于已启动。
 * startFactId 用于外部启动结果未知时的恢复，externalUrl 用于外部交互。
 */
public record FlowStartResult(
    String executionId,
    Status status,
    String externalInstanceId,
    String externalUrl,
    String startFactId,
    boolean taskQueryAvailable) {

  public enum Status { STARTING, STARTED, START_UNKNOWN, FAILED }

  public static FlowStartResult started(String executionId) {
    return new FlowStartResult(executionId, Status.STARTED, null, null, null, true);
  }

  /** 旧返回值只承诺请求已受理；失败事实不能投影成成功。 */
  public String acceptedExecutionId() {
    if (status == Status.FAILED) {
      throw new BusinessException("审批启动失败，启动事实: " + startFactId);
    }
    return executionId;
  }
}
