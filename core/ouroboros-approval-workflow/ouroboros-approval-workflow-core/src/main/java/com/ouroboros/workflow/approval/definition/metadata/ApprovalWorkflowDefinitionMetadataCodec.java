package com.ouroboros.workflow.approval.definition.metadata;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ouroboros.util.Json;
import com.ouroboros.workflow.approval.definition.codec.FlowDefinitionV2Normalizer;
import com.ouroboros.workflow.approval.definition.metadata.ApprovalWorkflowDefinitionMetadata.FormBinding;
import com.ouroboros.workflow.approval.definition.v2.FlowDefinitionV2;

import io.vavr.control.Try;

public class ApprovalWorkflowDefinitionMetadataCodec {
  private final FlowDefinitionV2Normalizer normalizer = new FlowDefinitionV2Normalizer();

  public Try<ApprovalWorkflowDefinitionMetadata> decodeMetadata(String rawJson) {
    return Json.tryToBean(JsonNode.class, rawJson)
        .flatMap(node -> decodeMetadataNode(extractDefinitionNode(node)))
        .flatMap(this::canonicalize);
  }

  public Try<String> encodeMetadata(ApprovalWorkflowDefinitionMetadata metadata) {
    return canonicalize(metadata)
        .flatMap(Json::tryToPrettyJsonString);
  }

  public Try<String> toRuntimeDefinitionJson(ApprovalWorkflowDefinitionMetadata metadata) {
    return Try.of(() -> toRuntimeDefinition(metadata))
        .flatMap(Json::tryToJsonString)
        .flatMap(json -> Json.tryToBean(JsonNode.class, json))
        .map(node -> copyEditorLayout(metadata, node))
        .flatMap(Json::tryToJsonString)
        .flatMap(normalizer::normalizeToJson);
  }

  public Try<JsonNode> toRuntimeDefinitionJsonNode(ApprovalWorkflowDefinitionMetadata metadata) {
    return toRuntimeDefinitionJson(metadata)
        .flatMap(json -> Json.tryToBean(JsonNode.class, json));
  }

  private Try<ApprovalWorkflowDefinitionMetadata> decodeMetadataNode(JsonNode node) {
    if (isStaticMetadata(node)) {
      return Json.tryToBean(ApprovalWorkflowDefinitionMetadata.class, node.toString());
    }
    return normalizer.normalizeToJson(node.toString())
        .map(this::fromRuntimeDefinitionJson);
  }

  private JsonNode extractDefinitionNode(JsonNode node) {
    JsonNode flowDefinition = node.path("flowDefinition");
    return flowDefinition.isMissingNode() || flowDefinition.isNull() ? node : flowDefinition;
  }

  private boolean isStaticMetadata(JsonNode node) {
    return node.has("title") || node.has("form");
  }

  private Try<ApprovalWorkflowDefinitionMetadata> canonicalize(ApprovalWorkflowDefinitionMetadata metadata) {
    return validateMetadata(metadata)
        .flatMap(ignored -> toRuntimeDefinitionJson(metadata))
        .map(this::fromRuntimeDefinitionJson)
        .map(canonical -> {
          canonical.setVersion(metadata.getVersion());
          canonical.setEnabled(metadata.getEnabled());
          return canonical;
        });
  }

  private Try<Void> validateMetadata(ApprovalWorkflowDefinitionMetadata metadata) {
    return Try.run(() -> {
      if (metadata == null) {
        throw new IllegalArgumentException("审批流定义不能为空");
      }
      if (!Integer.valueOf(ApprovalWorkflowDefinitionMetadata.FORMAT_VERSION).equals(metadata.getFormatVersion())) {
        throw new IllegalArgumentException("审批流定义 formatVersion 不支持");
      }
      if (StringUtils.isBlank(metadata.getName())) {
        throw new IllegalArgumentException("审批流定义 name 不能为空");
      }
      if (StringUtils.isBlank(metadata.getTitle())) {
        throw new IllegalArgumentException("审批流定义 title 不能为空");
      }
      if (metadata.getVersion() == null || metadata.getVersion() <= 0) {
        throw new IllegalArgumentException("审批流定义 version 必须为正整数");
      }
      FormBinding form = metadata.getForm();
      if (form == null || StringUtils.isBlank(form.getFormType()) || StringUtils.isBlank(form.getFormId())) {
        throw new IllegalArgumentException("审批流定义 form.formType 和 form.formId 不能为空");
      }
    });
  }

  private FlowDefinitionV2 toRuntimeDefinition(ApprovalWorkflowDefinitionMetadata metadata) {
    FlowDefinitionV2 definition = new FlowDefinitionV2();
    definition.setFormatVersion(2);
    definition.setId(metadata.getName());
    definition.setName(metadata.getDisplayTitle());
    definition.setCategory("");
    definition.setDescription(StringUtils.defaultString(metadata.getDescription()));
    definition.setFormType(StringUtils.defaultString(metadata.getForm().getFormType()));
    definition.setFormKey(StringUtils.defaultString(metadata.getForm().getFormId()));
    definition.setNotification(metadata.getNotification());
    definition.setNodes(metadata.getNodes());
    definition.setEdges(metadata.getEdges());
    return definition;
  }

  private JsonNode copyEditorLayout(ApprovalWorkflowDefinitionMetadata metadata, JsonNode definition) {
    if (metadata.getEditorLayout() != null && definition instanceof ObjectNode) {
      ((ObjectNode) definition).set("editorLayout", metadata.getEditorLayout().deepCopy());
    }
    return definition;
  }

  private ApprovalWorkflowDefinitionMetadata fromRuntimeDefinitionJson(String json) {
    JsonNode definitionNode = Json.toBean(JsonNode.class, json);
    FlowDefinitionV2 definition = Json.toBean(FlowDefinitionV2.class, json);
    ApprovalWorkflowDefinitionMetadata metadata = new ApprovalWorkflowDefinitionMetadata();
    metadata.setFormatVersion(ApprovalWorkflowDefinitionMetadata.FORMAT_VERSION);
    metadata.setName(definition.getId());
    metadata.setTitle(definition.getName());
    metadata.setDescription(definition.getDescription());
    metadata.setVersion(1);
    metadata.setEnabled(true);
    FormBinding form = new FormBinding();
    form.setFormType(definition.getFormType());
    form.setFormId(definition.getFormKey());
    metadata.setForm(form);
    metadata.setNotification(definition.getNotification());
    JsonNode editorLayout = definitionNode.path("editorLayout");
    if (!editorLayout.isMissingNode() && !editorLayout.isNull()) {
      metadata.setEditorLayout(editorLayout.deepCopy());
    }
    metadata.setNodes(definition.getNodes());
    metadata.setEdges(definition.getEdges());
    return metadata;
  }
}
