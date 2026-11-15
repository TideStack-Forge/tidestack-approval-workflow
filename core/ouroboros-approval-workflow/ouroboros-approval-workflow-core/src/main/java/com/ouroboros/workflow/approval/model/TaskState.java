package com.ouroboros.workflow.approval.model;

public enum TaskState {
  UNCREATED,
  /**
   * 任务创建
   */
  CREATED,
  /**
   * 任务审批通过
   */
  APPROVED,
  /**
   * 任务审批拒绝
   */
  REJECTED,
  /**
   * 任务已办理
   */
  HANDLED,
  /**
   * 任务正在处理
   */
  PROCESSING,
  /**
   * 任务超时提醒
   */
  TIMEOUT_REMIND,
  /**
   * 任务被撤销
   */
  CANCELLED,
  /**
   * 任务被暂停
   */
  SUSPENDED,
  /**
   * 任务被恢复
   */
  RESUMED
}
