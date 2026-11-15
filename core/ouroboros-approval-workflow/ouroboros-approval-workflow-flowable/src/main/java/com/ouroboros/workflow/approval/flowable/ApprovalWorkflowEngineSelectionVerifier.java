package com.ouroboros.workflow.approval.flowable;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

import com.ouroboros.exception.PlatformException;
import com.ouroboros.workflow.approval.ApprovalWorkflowErrorCodes;

/**
 * 审批流引擎选择校验。
 *
 * <p>{@link ApprovalWorkflowFlowableAutoConfiguration} 只在 {@code ouroboros.workflow.approval.engine}
 * 缺失或等于 {@code flowable} 时装配，且它是唯一注册 {@code FlowManageService}/{@code FlowInstanceService}/
 * {@code FlowTaskService} 的地方。若把属性拼错（如 {@code activiti}、{@code camunda}），条件不成立、
 * 引擎 Bean 全部缺失，故障要到运行期调用时以 null 依赖的形式暴露。
 *
 * <p>本类不带任何条件，无条件参与自动配置，因此无论属性取何值都会执行校验：
 * 只有 {@code flowable}（或留空）被接受，其余取值在启动期直接失败。
 */
@AutoConfiguration
@Configuration
public class ApprovalWorkflowEngineSelectionVerifier {

  private final String configuredEngine;

  public ApprovalWorkflowEngineSelectionVerifier(
      @Value("${ouroboros.workflow.approval.engine:flowable}") String configuredEngine) {
    this.configuredEngine = configuredEngine;
  }

  @PostConstruct
  public void verifyEngineSelection() {
    if (!"flowable".equals(configuredEngine)) {
      throw new PlatformException("[" + ApprovalWorkflowErrorCodes.WORKFLOW_ENGINE_NOT_FOUND
          + "] 不支持的审批流引擎: " + configuredEngine
          + "（ouroboros.workflow.approval.engine 仅接受 flowable，或留空使用默认值）");
    }
  }
}
