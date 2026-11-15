package com.ouroboros.workflow.approval;

/**
 * 流程实例变量的跨层契约键（flowable 写入变量、外部集成读取变量共用）。
 *
 * <p>业务模型投影模板（startProcess.json.ftl / processCallback.json.ftl）以字面量写入同名键，
 * 修改本类常量时必须同步模板字面量。
 */
public final class FlowInstanceVariables {

  /** 业务回调直连：回调逻辑流绑定键（由业务模型投影的 startProcess 模板写入）。 */
  public static final String CALLBACK_FLOW = "callbackFlow";

  /** 业务回调直连：业务记录类型。 */
  public static final String BUSINESS_RECORD_TYPE = "businessRecordType";

  /** 业务回调直连：业务记录主键。 */
  public static final String BUSINESS_RECORD_ID = "businessRecordId";

  /** 稳定审批案件 ID；每次重新提交共享同一个案件。 */
  public static final String APPROVAL_CASE_ID = "approvalCaseId";

  /** 当前审批案件的执行轮次；每次重新提交递增。 */
  public static final String SUBMISSION_ROUND = "submissionRound";

  /** 发起时抄送（FINISH 时机）：终态抄送用户列表。 */
  public static final String START_CC_FINISH_USERS = "__startCcFinishUsers";

  /** 催办幂等：任务本地变量记录最近提醒时间。 */
  public static final String REMIND_LAST_AT = "__remindLastAt";

  private FlowInstanceVariables() {
  }
}
