package com.ouroboros.workflow.approval.definition.startrequirements;

import java.util.Map;

import io.vavr.control.Try;

/**
 * 发起需求解析器（引擎无关化，设计文档 L659）。
 *
 * <p>内置与外部引擎各提供实现；路由层（RoutedFlowDefinitionService）按映射分派。
 * 内置实现从流程定义的自选节点生成；外部实现从映射 startOptions 声明或外部定义解析。
 */
public interface StartRequirementsResolver {

  /**
   * 解析流程的发起需求。
   *
   * @param flowKey  流程定义 ID
   * @param formData 发起表单数据（可空；用于条件节点解析与 FORM_FIELD 范围）
   */
  Try<StartRequirements> getStartRequirements(String flowKey, Map<String, Object> formData);
}
