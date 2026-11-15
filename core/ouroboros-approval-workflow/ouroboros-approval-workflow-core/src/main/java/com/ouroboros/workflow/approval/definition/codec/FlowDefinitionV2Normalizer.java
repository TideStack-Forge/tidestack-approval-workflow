package com.ouroboros.workflow.approval.definition.codec;

import com.fasterxml.jackson.databind.JsonNode;
import io.vavr.control.Try;

import com.ouroboros.util.Json;

public class FlowDefinitionV2Normalizer {
  final CompositeFlowDefinitionCodec codec = new CompositeFlowDefinitionCodec();
  final FlowDefinitionV2Canonicalizer canonicalizer = new FlowDefinitionV2Canonicalizer();

  public Try<JsonNode> normalizeToJsonNode(String rawJson) {
    return codec.detectFormatVersion(rawJson)
        .flatMap(formatVersion -> formatVersion == 2
            ? codec.decode(rawJson)
                .flatMap(ignored -> Json.tryToBean(JsonNode.class, rawJson))
                .map(canonicalizer::canonicalize)
            : codec.decode(rawJson)
                .flatMap(codec::encodeV2)
                .flatMap(Json::tryToJsonString)
                .flatMap(json -> Json.tryToBean(JsonNode.class, json)));
  }

  public Try<String> normalizeToJson(String rawJson) {
    return normalizeToJsonNode(rawJson)
        .flatMap(Json::tryToJsonString);
  }
}
