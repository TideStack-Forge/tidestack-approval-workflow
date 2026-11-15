package com.ouroboros.workflow.approval.definition.startrequirements;

import java.util.List;

/**
 * 发起需求契约（引擎无关化，设计文档 L659）。
 *
 * <p>内置与外部引擎共用同一契约：前端渲染同一套 picker、提交同一结构，
 * 差异在适配层翻译（内置 → taskAssignee + 引擎变量；外部 → ExternalStartCommand 字段）。
 *
 * @param requirements 自选节点需求列表（无自选节点时为空列表）
 * @param ccEnabled    该流程定义是否支持发起时抄送
 */
public record StartRequirements(List<Requirement> requirements, boolean ccEnabled) {

  public static StartRequirements none(boolean ccEnabled) {
    return new StartRequirements(List.of(), ccEnabled);
  }
}
