package com.ouroboros.workflow.approval;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import io.vavr.control.Try;

import com.ouroboros.exception.PlatformException;
import com.ouroboros.workflow.approval.definition.FlowDefinition;
import com.ouroboros.workflow.approval.definition.FlowNode;
import com.ouroboros.workflow.approval.definition.nodes.ApprovalNode;
import com.ouroboros.workflow.approval.model.FlowDefinitionVersion;

public interface FlowDefinitionService {
  Try<FlowDefinition> getFlowDefinition(String flowKey);

  /** Canonical graph read; legacy implementations retain compatibility through the existing definition codec. */
  default Try<com.ouroboros.workflow.approval.definition.graph.StructuredFlowDefinition> getStructuredFlowDefinition(String flowKey) {
    return getFlowDefinition(flowKey).flatMap(definition -> new com.ouroboros.workflow.approval.definition.codec.CompositeFlowDefinitionCodec()
        .decode(com.ouroboros.util.Json.toJsonString(definition)));
  }

  /** 读取流程定义 V2 文档（内部引擎编译产物）；默认实现表示当前引擎不支持该能力。 */
  default Try<JsonNode> getFlowDefinitionV2(String flowKey, Integer version) {
    return Try.failure(new PlatformException("[" + ApprovalWorkflowErrorCodes.WORKFLOW_ENGINE_NOT_FOUND
        + "] 当前流程引擎不支持读取流程定义 V2 文档"));
  }

  List<FlowNode> getFlowNodes(String flowKey);

  List<ApprovalNode> getUserSelectCandidateApprovalNodes(String flowKey);

  List<ApprovalNode> getUserSelectCandidateApprovalNodes(String flowKey, Map<String, Object> conditionContext);

  Try<List<FlowDefinitionVersion>> listFlowDefinitionVersions(String flowKey);

  Try<FlowDefinitionVersion> getFlowDefinitionVersion(String flowKey, Integer version);
}
