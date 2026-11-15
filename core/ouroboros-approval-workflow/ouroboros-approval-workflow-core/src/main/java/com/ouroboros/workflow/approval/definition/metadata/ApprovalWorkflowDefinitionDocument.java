package com.ouroboros.workflow.approval.definition.metadata;

import com.fasterxml.jackson.databind.JsonNode;
import com.ouroboros.util.Json;

import io.vavr.control.Try;

/**
 * Canonical interface for approval workflow definition document shapes.
 */
public final class ApprovalWorkflowDefinitionDocument {
  private final ApprovalWorkflowDefinitionMetadata metadata;
  private final ApprovalWorkflowDefinitionMetadataCodec codec = new ApprovalWorkflowDefinitionMetadataCodec();

  private ApprovalWorkflowDefinitionDocument(ApprovalWorkflowDefinitionMetadata metadata) {
    this.metadata = metadata;
  }

  public static Try<ApprovalWorkflowDefinitionDocument> decode(String rawJson) {
    ApprovalWorkflowDefinitionMetadataCodec codec = new ApprovalWorkflowDefinitionMetadataCodec();
    return codec.decodeMetadata(rawJson).map(ApprovalWorkflowDefinitionDocument::new);
  }

  public static Try<ApprovalWorkflowDefinitionDocument> of(ApprovalWorkflowDefinitionMetadata metadata) {
    ApprovalWorkflowDefinitionMetadataCodec codec = new ApprovalWorkflowDefinitionMetadataCodec();
    return codec.encodeMetadata(metadata)
        .flatMap(codec::decodeMetadata)
        .map(ApprovalWorkflowDefinitionDocument::new);
  }

  public ApprovalWorkflowDefinitionMetadata metadata() {
    return metadata;
  }

  public Try<String> toStaticJson() {
    return codec.encodeMetadata(metadata);
  }

  public Try<JsonNode> toStaticJsonNode() {
    return toStaticJson().flatMap(json -> Json.tryToBean(JsonNode.class, json));
  }

  public Try<String> toRuntimeJson() {
    return codec.toRuntimeDefinitionJson(metadata);
  }

  public Try<JsonNode> toRuntimeJsonNode() {
    return codec.toRuntimeDefinitionJsonNode(metadata);
  }
}
