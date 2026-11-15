package com.ouroboros.workflow.approval;

/**
 * 流程部署结果（引擎无关的兼容扩展）。
 *
 * <p>现有 {@code deploy} 返回的字符串语义是内置引擎的 {@code processDefinitionId}；本结果把
 * 部署模式与外部定义引用显式化，供 {@code WorkflowModelService} 区分内置/外部部署。
 *
 * @param executionMode        BUILT_IN / EXTERNAL
 * @param integrationMode      RESULT_ONLY / FULL_PROCESS（内置部署为 null）
 * @param authority            流程真相源：EXTERNAL_ENGINE / OUROBOROS
 * @param adapterId            外部适配器 ID（内置部署为 null）
 * @param mappingId            映射 ID（内置部署为 null）
 * @param mappingVersion       映射版本（内置部署为 null）
 * @param processDefinitionId  内置引擎部署 ID（外部权威模式不写入）
 * @param externalDefinitionRef 外部定义引用（内置部署为 null）
 */
public record FlowDeploymentResult(
    String executionMode,
    String integrationMode,
    String authority,
    String adapterId,
    String mappingId,
    Integer mappingVersion,
    String processDefinitionId,
    String externalDefinitionRef) {

  /**
   * 内置引擎部署结果。
   */
  public static FlowDeploymentResult builtIn(String processDefinitionId) {
    return new FlowDeploymentResult("BUILT_IN", null, "OUROBOROS", null, null, null, processDefinitionId, null);
  }

  /**
   * 本地引擎执行、外部系统仅作镜像投影的部署结果。
   */
  public static FlowDeploymentResult mirror(
      FlowDeploymentResult builtIn,
      String adapterId,
      String mappingId,
      Integer mappingVersion,
      String externalDefinitionRef) {
    return new FlowDeploymentResult(
        "BUILT_IN", null, "OUROBOROS", adapterId, mappingId, mappingVersion,
        builtIn.processDefinitionId(), externalDefinitionRef);
  }
}
