package com.ouroboros.workflow.approval;

import java.util.Map;

/**
 * 已由上层完成认证和关联校验的内部任务动作。
 *
 * <p>该命令不携带 userId；调用方必须提供稳定的来源事实 ID，供引擎和审计链路关联。
 * {@link Origin} 由引擎用于区分超时策略和已认证外部回调的最小授权集合。
 */
public record TrustedFlowTaskAction(
    String taskId,
    String actionType,
    String comment,
    Map<String, Object> formData,
    Object payload,
    String sourceFactId,
    Origin origin) {

  public enum Origin {
    TIMEOUT,
    EXTERNAL_CALLBACK
  }
}
