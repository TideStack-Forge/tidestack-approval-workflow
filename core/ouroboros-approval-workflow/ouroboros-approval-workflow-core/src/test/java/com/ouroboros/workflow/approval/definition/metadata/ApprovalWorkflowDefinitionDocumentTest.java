package com.ouroboros.workflow.approval.definition.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.ouroboros.workflow.approval.definition.nodes.NoticeNode;

class ApprovalWorkflowDefinitionDocumentTest {
  @Test
  void documentIsTheSingleInterfaceForStaticAndRuntimeShapes() {
    ApprovalWorkflowDefinitionDocument document = ApprovalWorkflowDefinitionDocument.decode(staticDefinition()).get();

    JsonNode staticJson = document.toStaticJsonNode().get();
    JsonNode runtimeJson = document.toRuntimeJsonNode().get();

    assertEquals("expense-approval", document.metadata().getName());
    assertEquals(1, staticJson.path("formatVersion").asInt());
    assertEquals("expense-approval", staticJson.path("name").asText());
    assertEquals("expenseForm", staticJson.path("form").path("formId").asText());
    assertFalse(staticJson.has("formKey"));
    assertEquals(2, runtimeJson.path("formatVersion").asInt());
    assertEquals("expense-approval", runtimeJson.path("id").asText());
    assertEquals("expenseForm", runtimeJson.path("formKey").asText());
    assertEquals(120, runtimeJson.path("editorLayout").path("nodePositions").path("start").path("x").asInt());
    assertFalse(runtimeJson.has("version"));
  }

  @Test
  void documentDecodeKeepsLegacyNoticeNodeReadableForPersistedRuntimeDefinitions() {
    ApprovalWorkflowDefinitionDocument english = ApprovalWorkflowDefinitionDocument
        .decode(legacyRuntimeDefinition("NoticeNode"))
        .get();
    ApprovalWorkflowDefinitionDocument chinese = ApprovalWorkflowDefinitionDocument
        .decode(legacyRuntimeDefinition("通知节点"))
        .get();

    assertInstanceOf(NoticeNode.class, english.metadata().getNodes().get(2));
    assertInstanceOf(NoticeNode.class, chinese.metadata().getNodes().get(2));
    assertEquals("NoticeNode", english.toStaticJsonNode().get().path("nodes").get(2).path("type").asText());
    assertEquals("NoticeNode", chinese.toStaticJsonNode().get().path("nodes").get(2).path("type").asText());
  }

  private String staticDefinition() {
    return """
        {
          "formatVersion": 1,
          "name": "expense-approval",
          "title": "费用审批",
          "description": "费用申请审批流程",
          "version": 4,
          "enabled": true,
          "form": {
            "formType": "businessModel",
            "formId": "expenseForm"
          },
          "editorLayout": {
            "nodePositions": {
              "start": { "x": 120, "y": 80 },
              "end": { "x": 420, "y": 80 }
            }
          },
          "nodes": [
            { "type": "发起节点", "name": "发起节点", "id": "start", "permission": [] },
            { "type": "结束节点", "name": "结束", "id": "end" }
          ],
          "edges": [
            { "id": "e1", "type": "SEQUENCE", "sourceId": "start", "targetId": "end" }
          ]
        }
        """;
  }

  private String legacyRuntimeDefinition(String noticeType) {
    return """
        {
          "formatVersion": 2,
          "id": "legacy-notice-flow",
          "name": "旧通知节点流程",
          "description": "旧 NoticeNode/通知节点 运行时定义",
          "formType": "business-model",
          "formKey": "expenseForm",
          "nodes": [
            { "type": "StartNode", "id": "start", "name": "发起节点", "permission": [] },
            { "type": "ApprovalNode", "id": "approve", "name": "审批", "subType": "单签", "candidates": [] },
            { "type": "%s", "id": "notice", "name": "结果通知", "notice": { "channel": [], "content": "done", "candidates": [] } }
          ],
          "edges": [
            { "id": "e1", "type": "SEQUENCE", "sourceId": "start", "targetId": "approve" },
            { "id": "e2", "type": "SEQUENCE", "sourceId": "approve", "targetId": "notice" }
          ]
        }
        """.formatted(noticeType);
  }
}
