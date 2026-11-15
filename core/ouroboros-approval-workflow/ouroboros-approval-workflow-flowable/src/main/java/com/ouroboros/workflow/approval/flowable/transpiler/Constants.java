package com.ouroboros.workflow.approval.flowable.transpiler;

public class Constants {

  public static final String OUROBOROS_NAMESPACE_PREFIX = "ouroborosApproval";
  public static final String OUROBOROS_NAMESPACE = "ouroboros/workflow/approval/1.0";
  public static final String PROCESS_PAUSE_USER_VARIABLE_NAME = "process_pause_user";
  public static final String PROCESS_PAUSE_TIME_VARIABLE_NAME = "process_pause_time";
  public static final String PROCESS_PAUSE_REASON_VARIABLE_NAME = "process_pause_reason";
  public static final String PROCESS_RESUME_USER_VARIABLE_NAME = "process_resume_user";
  public static final String PROCESS_RESUME_TIME_VARIABLE_NAME = "process_resume_time";
  public static final String PROCESS_CANCEL_USER_VARIABLE_NAME = "process_cancel_user";
  public static final String PROCESS_CANCEL_TIME_VARIABLE_NAME = "process_cancel_time";
  public static final String PROCESS_CANCEL_REASON_VARIABLE_NAME = "process_cancel_reason";
  public static final String TASK_PAYLOAD_KEY = "__task_payload";
  public static final String TASK_FORM_TYPE_KEY = "__task_form_type";
  public static final String TASK_FORM_ID_KEY = "__task_form_id";
  public static final String TASK_FORM_DATA_KEY = "__task_form_data";
  public static final String TRUSTED_SOURCE_FACT_ID_KEY = "__trusted_source_fact_id";
  public static final String CANDIDATE_FORM_DATA_KEY = "__candidate_form_data";
  public static final String SYSTEM_ACTOR_USER_ID = "__approval_system__";

  public static class SpecialNode {
    public static final String START_EVENT_ID = "__start";
    public static final String START_EVENT_NAME = "开始节点";
    public static final String END_EVENT_ID = "__end_event";
    public static final String END_EVENT_NAME = "结束节点";
    public static final String FAIL_END_EVENT_ID = "__fail_end_event";
    public static final String FAIL_END_EVENT_NAME = "失败终止结束节点";
    public static final String INITIALIZE_FLOW_ID = "__initial_service_task";
    public static final String INITIALIZE_FLOW_NAME = "初始化";
    public static final String INITIAL_FORK_GATEWAY_ID = "__initial_fork_gateway";
    public static final String INITIAL_FORK_GATEWAY_NAME = "流程初始化分支网关";
    public static final String INITIAL_JOIN_GATEWAY_ID = "__initial_join_gateway";
    public static final String INITIAL_JOIN_GATEWAY_NAME = "流程初始化汇聚网关";
    public static final String FAIL_NODE_ID = "__fail_service_task";
    public static final String FAIL_NODE_NAME = "失败结束节点";

    public static final String RESET_MULTIPLE_APPROVAL_ID_SUFFIX = "_reset_multiple_approval_service_task";
    public static final String RESET_MULTIPLE_APPROVAL_NAME = "重置会签状态";
    public static final String DECISION_ID_SUFFIX = "_decision_gateway";
    public static final String DECISION_NAME_SUFFIX = "审批结果决策";
    public static final String APPROVED_ID_SUFFIX = "_approved_decision";
    public static final String APPROVED_NAME_SUFFIX = "审批通过";
    public static final String ADD_SIGN_AFTER_CURRENT_GATEWAY_ID_SUFFIX = "_add_sign_after_current_gateway";
    public static final String ADD_SIGN_AFTER_CURRENT_GATEWAY_NAME_SUFFIX = "后加签分流网关";
    public static final String ADD_SIGN_AFTER_CURRENT_GATE_TASK_NAME_SUFFIX = "后加签等待";
    public static final String TIMEOUT_BOUNDARY_ID_SUFFIX = "_timeout_boundary";
    public static final String TIMEOUT_BOUNDARY_NAME_SUFFIX = "超时策略";
    public static final String TIMEOUT_SERVICE_TASK_ID_SUFFIX = "_timeout_service_task";
    public static final String TIMEOUT_SERVICE_TASK_NAME_SUFFIX = "超时自动动作";
    public static final String OUTCOME_ID_SUFFIX = "_set_outcome_service_task";

    public static final String JOIN_ID_SUFFIX = "_join_gateway";
    public static final String JOIN_NAME_SUFFIX = "汇聚网关";
  }

  public static class Variable {
    public static final String PROCESS_STARTER = "__processStarter";
    public static final String PROCESS_FLOW_KEY = "__processFlowKey";
    public static final String USER_SELECTED_ASSIGNEES = "__userSelectedAssignees";
    public static final String PROCESS_APPROVED = "__processApproved";
    public static final String PROCESS_OUTCOME = "__processOutcome";
    public static final String PROCESS_LAST_FINISHED_TASK_ID = "__processLastFinishedTaskId";
    public static final String PARENT_FLOW_ID = "__parentFlowId";
    public static final String HAS_PARENT_FLOW = "__hasParentFlow";
    public static final String MULTIPLE_APPROVAL_ASSIGNEE_LIST = "AssigneeList";

    /** 业务回调直连：回调逻辑流绑定键（契约键见 {@link com.ouroboros.workflow.approval.FlowInstanceVariables}）。 */
    public static final String CALLBACK_FLOW = com.ouroboros.workflow.approval.FlowInstanceVariables.CALLBACK_FLOW;
    /** 业务回调直连：业务记录类型。 */
    public static final String BUSINESS_RECORD_TYPE = com.ouroboros.workflow.approval.FlowInstanceVariables.BUSINESS_RECORD_TYPE;
    /** 业务回调直连：业务记录主键。 */
    public static final String BUSINESS_RECORD_ID = com.ouroboros.workflow.approval.FlowInstanceVariables.BUSINESS_RECORD_ID;
    public static final String APPROVAL_CASE_ID = com.ouroboros.workflow.approval.FlowInstanceVariables.APPROVAL_CASE_ID;
    public static final String SUBMISSION_ROUND = com.ouroboros.workflow.approval.FlowInstanceVariables.SUBMISSION_ROUND;
    /** 发起时抄送（FINISH 时机）：终态抄送用户列表。 */
    public static final String START_CC_FINISH_USERS = com.ouroboros.workflow.approval.FlowInstanceVariables.START_CC_FINISH_USERS;
    /** 催办幂等：任务本地变量记录最近提醒时间。 */
    public static final String REMIND_LAST_AT = com.ouroboros.workflow.approval.FlowInstanceVariables.REMIND_LAST_AT;

    public static final String LOCAL_APPROVAL_ACTION = "__approval_action";
    public static final String TASK_KIND = "__task_kind";
    public static final String TASK_APPROVED = "__task_approved";
    public static final String APPROVAL_ACTION_SUFFIX = "_approval_action";
    public static final String APPROVED_SUFFIX = "_approved";
    public static final String NR_OF_APPROVED_SUFFIX = "_nrOfApproved";
    public static final String NR_OF_REJECTED_SUFFIX = "_nrOfRejected";
    public static final String MULTIPLE_APPROVAL_COMPLETED_SUFFIX = "_multiple_approval_completed";
    public static final String MULTIPLE_APPROVED_PERCENTAGE_SUFFIX = "_approved_percentage";
    public static final String MULTIPLE_APPROVAL_IS_SEQUENTIAL_SUFFIX = "_is_sequential";
    public static final String MULTIPLE_APPROVAL_FINAL_TASK_ID_SUFFIX = "_multiple_approval_final_task_id";
    public static final String MULTIPLE_APPROVAL_SESSION = "__multiple_approval_session";

    public static final String ADD_SIGN_PENDING_USERS = "__add_sign_pending_users";
    public static final String ADD_SIGN_AFTER_CURRENT_PENDING = "__add_sign_after_current_pending";
    public static final String DETOUR_BEFORE_CURRENT_ACTIVE = "__detour_before_current_active";
    public static final String DETOUR_TARGET_USERS = "__detour_target_users";
    public static final String DETOUR_SOURCE_NODE = "__detour_source_node";
    public static final String REDIRECT_AFTER_CURRENT_ACTIVE = "__redirect_after_current_active";
    public static final String REDIRECT_TARGET_USERS = "__redirect_target_users";
    public static final String ADD_SIGN_WAITING = "__add_sign_waiting";
    public static final String ADD_SIGN_SOURCE_TASK_ID = "__add_sign_source_task_id";
    public static final String ADD_SIGN_GROUP_ID = "__add_sign_group_id";
    public static final String ADD_SIGN_TASK_KIND = "__add_sign_task_kind";
    public static final String ADD_SIGN_RESUME_SOURCE_TASK_ID = "__add_sign_resume_source_task_id";
    public static final String ADD_SIGN_AFTER_CURRENT_GATE_ACTIVE_SUFFIX = "_add_sign_after_current_gate_active";
    public static final String ADD_SIGN_AFTER_CURRENT_GATE_TASK_ID_SUFFIX = "_add_sign_after_current_gate_task_id";

    public static final String ADD_SIGN_GROUP_TOTAL_SUFFIX = "_total";
    public static final String ADD_SIGN_GROUP_COMPLETED_SUFFIX = "_completed";
    public static final String ADD_SIGN_GROUP_POSITION_SUFFIX = "_position";
    public static final String ADD_SIGN_GROUP_PROCESS_INSTANCE_ID_SUFFIX = "_process_instance_id";
    public static final String ADD_SIGN_GROUP_PROCESS_DEFINITION_ID_SUFFIX = "_process_definition_id";
    public static final String ADD_SIGN_GROUP_SOURCE_TASK_ID_SUFFIX = "_source_task_id";
    public static final String ADD_SIGN_GROUP_SOURCE_TASK_NAME_SUFFIX = "_source_task_name";
    public static final String ADD_SIGN_GROUP_SOURCE_TASK_ASSIGNEE_SUFFIX = "_source_task_assignee";
    public static final String ADD_SIGN_GROUP_SOURCE_NODE_SUFFIX = "_source_node";
    public static final String ADD_SIGN_GROUP_SOURCE_TASK_FORM_KEY_SUFFIX = "_source_task_form_key";
    public static final String ADD_SIGN_GROUP_SOURCE_TASK_FORM_DATA_SUFFIX = "_source_task_form_data";
    public static final String ADD_SIGN_GROUP_SOURCE_TASK_PAYLOAD_SUFFIX = "_source_task_payload";
    public static final String ADD_SIGN_GROUP_SIGNERS_SUFFIX = "_signers";
    public static final String ADD_SIGN_GROUP_GATE_TASK_ID_SUFFIX = "_gate_task_id";
    public static final String ADD_SIGN_GROUP_RESUME_TASK_CREATED_SUFFIX = "_resume_task_created";
    public static final String ADD_SIGN_GROUP_PREFIX = "__add_sign_group_";
    public static final String FREE_APPROVAL_DETOUR_ACTIVE = "__free_approval_detour_active";
    public static final String FREE_APPROVAL_CONSULT_USERS = "__free_approval_consult_users";
    public static final String FREE_APPROVAL_SOURCE_NODE = "__free_approval_source_node";
    public static final String FREE_APPROVAL_SOURCE_ASSIGNEE = "__free_approval_source_assignee";

    public static String getMultipleApprovalAssigneeListVariableName(String approvalTaskId) {
      return approvalTaskId + "_" + MULTIPLE_APPROVAL_ASSIGNEE_LIST;
    }

    public static String getMultipleApprovalSessionVariableName(String approvalTaskId) {
      return approvalTaskId + MULTIPLE_APPROVAL_SESSION;
    }

    public static String addSignAfterCurrentGateActiveVariableName(String sourceNodeId) {
      return sourceNodeId + ADD_SIGN_AFTER_CURRENT_GATE_ACTIVE_SUFFIX;
    }

    public static String addSignAfterCurrentGateTaskId(String sourceNodeId) {
      return sourceNodeId + ADD_SIGN_AFTER_CURRENT_GATE_TASK_ID_SUFFIX;
    }

    public static String addSignGroupVariableName(String groupId, String suffix) {
      return ADD_SIGN_GROUP_PREFIX + groupId + suffix;
    }
  }

  public static class Property {
    public static final String PERCENTAGE = "percentage";
    public static final String MULTIPLE_APPROVAL_TASK_ID = "multipleApprovalTaskId";
    public static final String IS_SEQUENTIAL = "isSequential";
    public static final String REJECT_ACTION = "rejectAction";
    public static final String APPROVAL_TASK_ID = "approvalTaskId";
    public static final String TIMEOUT_ACTION = "timeoutAction";
    public static final String TIMEOUT_TRANSFER_TO = "timeoutTransferTo";
    public static final String PROCESS_OUTCOME = "processOutcome";
  }

  public static class Value {
    public static final String APPROVED = "approve";
    public static final String REJECTED = "reject";
    public static final String ROLLBACK = "rollback";
    public static final String COMPLETE = "complete";
    public static final String TIMEOUT_ACTION_REMIND = "remind";
    public static final String TIMEOUT_ACTION_AUTO_APPROVE = "autoApprove";
    public static final String TIMEOUT_ACTION_AUTO_REJECT = "autoReject";
    public static final String TIMEOUT_ACTION_TRANSFER = "transfer";
    public static final String TASK_KIND_APPROVAL = "approval";
    public static final String TASK_KIND_HANDLE = "handle";
    public static final String ADD_SIGN = "addSign";
    public static final String FREE_APPROVE = "freeApprove";
    public static final String ADD_SIGN_TASK_KIND_ADDED = "ADD_SIGN";
    public static final String ADD_SIGN_TASK_KIND_RESUME = "RESUME";
    public static final String ADD_SIGN_POSITION_BEFORE_CURRENT = "BEFORE_CURRENT";
    public static final String ADD_SIGN_POSITION_AFTER_CURRENT = "AFTER_CURRENT";
    public static final String EXCLUSIVE_FORK_TYPE = "exclusive";
    public static final String MULTIPLE_APPROVAL_MODE_SEQUENTIAL = "串行";
  }
}
