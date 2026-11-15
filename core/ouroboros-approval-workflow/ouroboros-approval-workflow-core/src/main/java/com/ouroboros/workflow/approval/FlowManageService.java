package com.ouroboros.workflow.approval;

import io.vavr.control.Try;

import com.ouroboros.util.Json;
import com.ouroboros.workflow.approval.definition.FlowDefinition;

public interface FlowManageService {
  Try<String> deploy(String flowKey, String flowDefinitionJson);

  Try<Void> offline(String processDefinitionId);

  default Try<String> deployNewVersion(String flowKey, String flowDefinitionJson) {
    return deploy(flowKey, flowDefinitionJson);
  }

  default Try<String> deploy(String flowKey, FlowDefinition flowDefinition) {
    return deploy(flowKey, Json.toJsonString(flowDefinition));
  }

  /**
   * 运行时部署入口（引擎无关）：内置路径委托 {@link #deployNewVersion} 并包装为内置结果；
   * 外部权威路径由路由门面重写为仅验证外部定义。
   *
   * @param flowKey             流程定义 Key
   * @param flowDefinitionJson  流程定义 JSON
   * @return 引擎无关部署结果
   */
  default Try<FlowDeploymentResult> deployForRuntime(String flowKey, String flowDefinitionJson) {
    return deployNewVersion(flowKey, flowDefinitionJson).map(FlowDeploymentResult::builtIn);
  }
}
