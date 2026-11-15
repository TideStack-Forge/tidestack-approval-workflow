package com.ouroboros.workflow.approval;

/**
 * 审批流错误码常量（AWF 层）。
 *
 * <p>本类为 AWF 侧错误码的最小落点：AWF 引擎实现与服务抛出的业务异常统一在此登记错误码，
 * 异常 message 内嵌 {@code [CODE]}。EXT（外部流程引擎对接）侧错误码仍走
 * {@code com.ouroboros.workflow.approval.external.EngineIntegrationErrorCodes} 三处同步。
 */
public final class ApprovalWorkflowErrorCodes {

  /** 自选审批人数量超出节点允许区间（如 SINGLE 节点选择多人）。 */
  public static final String WORKFLOW_ASSIGNEE_OUT_OF_RANGE = "WORKFLOW_ASSIGNEE_OUT_OF_RANGE";

  /** 统一事件序列化失败（outbox 入队阶段，事件无法序列化为 JSON）。 */
  public static final String WORKFLOW_EVENT_SERIALIZATION_FAILED = "WORKFLOW_EVENT_SERIALIZATION_FAILED";

  /** 统一事件事实 ID 已被不同事件占用。 */
  public static final String WORKFLOW_EVENT_FACT_CONFLICT = "WORKFLOW_EVENT_FACT_CONFLICT";

  /** 发起需求解析失败（从流程定义生成发起需求契约时出错）。 */
  public static final String WORKFLOW_START_REQUIREMENTS_RESOLVE_FAILED =
      "WORKFLOW_START_REQUIREMENTS_RESOLVE_FAILED";

  /** 当前流程引擎未实现催办动作。 */
  public static final String WORKFLOW_REMIND_UNSUPPORTED = "WORKFLOW_REMIND_UNSUPPORTED";

  /** 当前业务记录已有进行中的审批轮次，不能并发再次发起。 */
  public static final String WORKFLOW_SUBMISSION_CONFLICT = "WORKFLOW_SUBMISSION_CONFLICT";

  /** 审批案件状态或轮次不满足请求的生命周期转换。 */
  public static final String WORKFLOW_CASE_STATE_CONFLICT = "WORKFLOW_CASE_STATE_CONFLICT";

  /** 审批流引擎选择非法（{@code ouroboros.workflow.approval.engine} 取值不受支持）。 */
  public static final String WORKFLOW_ENGINE_NOT_FOUND = "WORKFLOW_ENGINE_NOT_FOUND";

  private ApprovalWorkflowErrorCodes() {
  }
}
