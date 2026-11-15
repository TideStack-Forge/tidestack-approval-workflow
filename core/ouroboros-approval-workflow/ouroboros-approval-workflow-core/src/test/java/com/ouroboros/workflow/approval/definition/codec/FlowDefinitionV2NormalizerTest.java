package com.ouroboros.workflow.approval.definition.codec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

public class FlowDefinitionV2NormalizerTest {
  @Test
  void normalizeToJsonNodeUpgradesLegacyV1IntoFormatVersion2() {
    FlowDefinitionV2Normalizer normalizer = new FlowDefinitionV2Normalizer();

    io.vavr.control.Try<JsonNode> normalized = normalizer.normalizeToJsonNode(v1FlowJson());

    assertTrue(normalized.isSuccess());
    assertEquals(2, normalized.get().path("formatVersion").asInt());
    assertEquals("flow-a", normalized.get().path("id").asText());
    assertTrue(normalized.get().path("nodes").isArray());
    assertTrue(normalized.get().path("edges").isArray());
    assertEquals(4, normalized.get().path("edges").size());
  }

  @Test
  void normalizeToJsonNodeKeepsNativeV2UnknownFieldsWhileCanonicalizingStableNodeShapes() {
    FlowDefinitionV2Normalizer normalizer = new FlowDefinitionV2Normalizer();

    io.vavr.control.Try<JsonNode> normalized = normalizer.normalizeToJsonNode(v2FlowJsonWithEditorMeta());

    assertTrue(normalized.isSuccess());
    assertEquals("wide", normalized.get().path("editorMeta").path("layout").asText());
    assertEquals("StartNode", normalized.get().path("nodes").get(0).path("type").asText());
    assertEquals("EndNode", normalized.get().path("nodes").get(1).path("type").asText());
    assertEquals("child-flow", normalized.get().path("nodes").get(2).path("subFlowKey").asText());
    assertTrue(normalized.get().path("nodes").get(2).path("subProcessId").isMissingNode());
    // edge.name 不是稳定 V2 contract，但归一化不再主动删除它：
    // 两条解析路径（canonicalize 与 V2FlowDefinitionCodec.decode）必须给出同一份对象
    assertEquals("老分支名", normalized.get().path("edges").get(0).path("name").asText());
  }

  @Test
  void normalizeToJsonNodeRejectsMalformedJson() {
    FlowDefinitionV2Normalizer normalizer = new FlowDefinitionV2Normalizer();

    io.vavr.control.Try<JsonNode> normalized = normalizer.normalizeToJsonNode("{\"formatVersion\":2,\"id\":\"flow-a\"");

    assertTrue(normalized.isFailure());
  }

  @Test
  void normalizeToJsonNodeRejectsUnsupportedExplicitFormatVersion() {
    FlowDefinitionV2Normalizer normalizer = new FlowDefinitionV2Normalizer();

    io.vavr.control.Try<JsonNode> normalized = normalizer.normalizeToJsonNode(String.join("\n",
        "{",
        "  \"formatVersion\": 3,",
        "  \"id\": \"flow-a\",",
        "  \"name\": \"采购审批\",",
        "  \"nodes\": [],",
        "  \"edges\": []",
        "}"));

    assertTrue(normalized.isFailure());
  }

  @Test
  void normalizeToJsonNodeRejectsStableV2BranchEdge() {
    FlowDefinitionV2Normalizer normalizer = new FlowDefinitionV2Normalizer();

    io.vavr.control.Try<JsonNode> normalized = normalizer.normalizeToJsonNode(v2FlowWithEdgeType("BRANCH"));

    assertTrue(normalized.isFailure());
  }

  @Test
  void normalizeToJsonNodeRejectsStableV2AndJoin() {
    FlowDefinitionV2Normalizer normalizer = new FlowDefinitionV2Normalizer();

    io.vavr.control.Try<JsonNode> normalized = normalizer.normalizeToJsonNode(v2JoinFlow("AND"));

    assertTrue(normalized.isFailure());
  }

  private String v1FlowJson() {
    return String.join("\n",
        "{",
        "  \"id\": \"flow-a\",",
        "  \"name\": \"采购审批\",",
        "  \"category\": \"\",",
        "  \"icon\": \"shenpi\",",
        "  \"description\": \"\",",
        "  \"formType\": \"default\",",
        "  \"formKey\": \"purchase-form\",",
        "  \"nodes\": [",
        "    {",
        "      \"type\": \"发起节点\",",
        "      \"name\": \"发起节点\",",
        "      \"id\": \"start-a\",",
        "      \"permission\": [],",
        "      \"next\": \"fork-a\"",
        "    },",
        "    {",
        "      \"type\": \"分流节点\",",
        "      \"name\": \"金额判断\",",
        "      \"id\": \"fork-a\",",
        "      \"logic\": \"exclusive\",",
        "      \"branches\": [",
        "        {",
        "          \"id\": \"branch-1\",",
        "          \"name\": \"金额大于1000\",",
        "          \"condition\": {",
        "            \"left\": {",
        "              \"type\": \"field\",",
        "              \"field\": \"amount\"",
        "            },",
        "            \"op\": \"greater\",",
        "            \"right\": 1000",
        "          },",
        "          \"first\": \"approve-a\"",
        "        },",
        "        {",
        "          \"id\": \"branch-2\",",
        "          \"name\": \"默认\",",
        "          \"condition\": {",
        "            \"left\": {",
        "              \"type\": \"field\",",
        "              \"field\": \"amount\"",
        "            },",
        "            \"op\": \"less_or_equal\",",
        "            \"right\": 1000",
        "          },",
        "          \"first\": \"approve-b\"",
        "        }",
        "      ],",
        "      \"next\": \"end-a\"",
        "    },",
        "    {",
        "      \"type\": \"审批节点\",",
        "      \"name\": \"经理审批\",",
        "      \"id\": \"approve-a\",",
        "      \"subType\": \"单签\",",
        "      \"candidates\": [],",
        "      \"permission\": [],",
        "      \"actions\": [\"reject\"],",
        "      \"next\": \"\"",
        "    },",
        "    {",
        "      \"type\": \"审批节点\",",
        "      \"name\": \"直属审批\",",
        "      \"id\": \"approve-b\",",
        "      \"subType\": \"单签\",",
        "      \"candidates\": [],",
        "      \"permission\": [],",
        "      \"actions\": [\"reject\"],",
        "      \"next\": \"\"",
        "    },",
        "    {",
        "      \"type\": \"结束节点\",",
        "      \"name\": \"结束\",",
        "      \"id\": \"end-a\",",
        "      \"next\": \"\"",
        "    }",
        "  ]",
        "}");
  }

  private String v2FlowJsonWithEditorMeta() {
    return String.join("\n",
        "{",
        "  \"formatVersion\": 2,",
        "  \"id\": \"flow-a\",",
        "  \"name\": \"采购审批\",",
        "  \"category\": \"\",",
        "  \"icon\": \"shenpi\",",
        "  \"description\": \"\",",
        "  \"formType\": \"default\",",
        "  \"formKey\": \"purchase-form\",",
        "  \"editorMeta\": {",
        "    \"layout\": \"wide\"",
        "  },",
        "  \"nodes\": [",
        "    {",
        "      \"type\": \"发起节点\",",
        "      \"name\": \"发起节点\",",
        "      \"id\": \"start-a\",",
        "      \"permission\": []",
        "    },",
        "    {",
        "      \"type\": \"结束节点\",",
        "      \"name\": \"结束\",",
        "      \"id\": \"end-a\"",
        "    },",
        "    {",
        "      \"type\": \"子流程节点\",",
        "      \"name\": \"子流程\",",
        "      \"id\": \"subflow-a\",",
        "      \"subProcessId\": \"child-flow\",",
        "      \"inParameters\": [],",
        "      \"outParameters\": [],",
        "      \"rejectAction\": \"reject\"",
        "    }",
        "  ],",
        "  \"edges\": [",
        "    {",
        "      \"id\": \"edge-seq-1\",",
        "      \"type\": \"SEQUENCE\",",
        "      \"sourceId\": \"start-a\",",
        "      \"targetId\": \"end-a\",",
        "      \"name\": \"老分支名\"",
        "    },",
        "    {",
        "      \"id\": \"edge-seq-2\",",
        "      \"type\": \"SEQUENCE\",",
        "      \"sourceId\": \"start-a\",",
        "      \"targetId\": \"subflow-a\"",
        "    }",
        "  ]",
        "}");
  }

  private String v2FlowWithEdgeType(String edgeType) {
    return String.join("\n",
        "{",
        "  \"formatVersion\": 2,",
        "  \"id\": \"flow-edge-type\",",
        "  \"name\": \"边类型测试\",",
        "  \"nodes\": [",
        "    { \"type\": \"发起节点\", \"id\": \"start-a\" },",
        "    { \"type\": \"审批节点\", \"id\": \"approve-a\", \"subType\": \"单签\", \"candidates\": [] }",
        "  ],",
        "  \"edges\": [",
        "    { \"id\": \"edge-a\", \"type\": \"" + edgeType + "\", \"sourceId\": \"start-a\", \"targetId\": \"approve-a\" }",
        "  ]",
        "}");
  }

  private String v2JoinFlow(String joinType) {
    return String.join("\n",
        "{",
        "  \"formatVersion\": 2,",
        "  \"id\": \"flow-join\",",
        "  \"name\": \"汇聚测试\",",
        "  \"nodes\": [",
        "    { \"type\": \"发起节点\", \"id\": \"start-a\" },",
        "    { \"type\": \"审批节点\", \"id\": \"approve-a\", \"subType\": \"单签\", \"candidates\": [] },",
        "    { \"type\": \"审批节点\", \"id\": \"approve-b\", \"subType\": \"单签\", \"candidates\": [] },",
        "    { \"type\": \"JoinNode\", \"id\": \"join-a\", \"joinType\": \"" + joinType + "\" },",
        "    { \"type\": \"结束节点\", \"id\": \"end-a\" }",
        "  ],",
        "  \"edges\": [",
        "    { \"type\": \"SEQUENCE\", \"sourceId\": \"start-a\", \"targetId\": \"approve-a\" },",
        "    { \"type\": \"SEQUENCE\", \"sourceId\": \"start-a\", \"targetId\": \"approve-b\" },",
        "    { \"type\": \"SEQUENCE\", \"sourceId\": \"approve-a\", \"targetId\": \"join-a\" },",
        "    { \"type\": \"SEQUENCE\", \"sourceId\": \"approve-b\", \"targetId\": \"join-a\" },",
        "    { \"type\": \"SEQUENCE\", \"sourceId\": \"join-a\", \"targetId\": \"end-a\" }",
        "  ]",
        "}");
  }
}
