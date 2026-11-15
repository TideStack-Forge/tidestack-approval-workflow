package com.ouroboros.workflow.approval.lifecycle;

/**
 * 与业务审批生命周期正交的技术处理状态。
 */
public enum ApprovalTechnicalState {
  /** 无未处理技术故障。 */
  HEALTHY,
  /** 已知可恢复故障，正在自动补偿。 */
  RECOVERING,
  /** 远程调用结果不确定，禁止盲目重放非幂等操作。 */
  UNKNOWN,
  /** 已确认无法恢复；必须同时将业务状态收敛为 CANCELED。 */
  EXHAUSTED
}
