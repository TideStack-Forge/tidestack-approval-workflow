package com.ouroboros.workflow.approval.definition.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.ouroboros.util.Json;

import io.vavr.control.Try;

class ApprovalWorkflowDefinitionMetadataCodecTest {
  private final ApprovalWorkflowDefinitionMetadataCodec codec = new ApprovalWorkflowDefinitionMetadataCodec();

  @Test
  void decodesLegacyRuntimeDefinitionIntoStaticMetadata() {
    Try<ApprovalWorkflowDefinitionMetadata> result = codec.decodeMetadata(legacyRuntimeDefinition());

    assertTrue(result.isSuccess());
    ApprovalWorkflowDefinitionMetadata metadata = result.get();
    assertEquals(1, metadata.getFormatVersion());
    assertEquals("purchase-approval", metadata.getName());
    assertEquals("采购审批", metadata.getTitle());
    assertEquals(1, metadata.getVersion());
    assertTrue(metadata.isEnabledEffective());
    assertEquals("businessModel", metadata.getForm().getFormType());
    assertEquals("purchaseRequest", metadata.getForm().getFormId());
  }

  @Test
  void encodesStaticMetadataWithoutRuntimeFields() {
    ApprovalWorkflowDefinitionMetadata metadata = codec.decodeMetadata(legacyRuntimeDefinition()).get();
    metadata.setVersion(7);

    JsonNode staticJson = Json.tryToBean(JsonNode.class, codec.encodeMetadata(metadata).get()).get();

    assertEquals(1, staticJson.path("formatVersion").asInt());
    assertEquals("purchase-approval", staticJson.path("name").asText());
    assertEquals("采购审批", staticJson.path("title").asText());
    assertEquals(7, staticJson.path("version").asInt());
    assertEquals("businessModel", staticJson.path("form").path("formType").asText());
    assertEquals("purchaseRequest", staticJson.path("form").path("formId").asText());
    assertFalse(staticJson.has("id"));
    assertFalse(staticJson.has("formType"));
    assertFalse(staticJson.has("formKey"));
    assertFalse(staticJson.has("definitionId"));
    assertFalse(staticJson.has("source"));
    assertFalse(staticJson.has("adopted"));
    assertFalse(staticJson.has("processDefinitionId"));
  }

  @Test
  void convertsStaticMetadataToExistingRuntimeDefinitionShape() {
    ApprovalWorkflowDefinitionMetadata metadata = codec.decodeMetadata(legacyRuntimeDefinition()).get();
    metadata.setVersion(7);

    JsonNode runtimeJson = Json.tryToBean(JsonNode.class, codec.toRuntimeDefinitionJson(metadata).get()).get();

    assertEquals(2, runtimeJson.path("formatVersion").asInt());
    assertEquals("purchase-approval", runtimeJson.path("id").asText());
    assertEquals("采购审批", runtimeJson.path("name").asText());
    assertEquals("businessModel", runtimeJson.path("formType").asText());
    assertEquals("purchaseRequest", runtimeJson.path("formKey").asText());
    assertFalse(runtimeJson.has("version"));
    assertFalse(runtimeJson.has("source"));
    assertFalse(runtimeJson.has("adopted"));
    assertFalse(runtimeJson.has("processDefinitionId"));
  }

  private String legacyRuntimeDefinition() {
    return String.join("\n",
        "{",
        "  \"formatVersion\": 2,",
        "  \"id\": \"purchase-approval\",",
        "  \"name\": \"采购审批\",",
        "  \"description\": \"采购申请审批流程\",",
        "  \"formType\": \"businessModel\",",
        "  \"formKey\": \"purchaseRequest\",",
        "  \"nodes\": [",
        "    {\"type\": \"发起节点\", \"name\": \"发起节点\", \"id\": \"purchase-approval-start\"},",
        "    {\"type\": \"结束节点\", \"name\": \"结束\", \"id\": \"purchase-approval-end\"}",
        "  ],",
        "  \"edges\": [",
        "    {\"id\": \"purchase-approval-edge\", \"type\": \"SEQUENCE\", \"sourceId\": \"purchase-approval-start\", \"targetId\": \"purchase-approval-end\"}",
        "  ]",
        "}");
  }
}
