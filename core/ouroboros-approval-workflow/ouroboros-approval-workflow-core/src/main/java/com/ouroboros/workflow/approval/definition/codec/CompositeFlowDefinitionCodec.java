package com.ouroboros.workflow.approval.definition.codec;

import java.util.Map;

import io.vavr.control.Try;

import com.ouroboros.util.Json;
import com.ouroboros.workflow.approval.definition.FlowDefinition;
import com.ouroboros.workflow.approval.definition.graph.StructuredFlowDefinition;
import com.ouroboros.workflow.approval.definition.v2.FlowDefinitionV2;

public class CompositeFlowDefinitionCodec implements FlowDefinitionCodec {
  final V1FlowDefinitionCodec v1Codec = new V1FlowDefinitionCodec();
  final V2FlowDefinitionCodec v2Codec = new V2FlowDefinitionCodec();

  @Override
  public Try<StructuredFlowDefinition> decode(String rawJson) {
    return detectFormatVersion(rawJson)
        .flatMap(formatVersion -> formatVersion == 2 ? v2Codec.decode(rawJson) : v1Codec.decode(rawJson));
  }

  @Override
  public Try<FlowDefinition> encodeV1(StructuredFlowDefinition definition) {
    return v1Codec.encodeV1(definition);
  }

  @Override
  public Try<FlowDefinitionV2> encodeV2(StructuredFlowDefinition definition) {
    return v2Codec.encodeV2(definition);
  }

  Try<Integer> detectFormatVersion(String rawJson) {
    return Json.tryToMap(rawJson)
        .mapTry(this::resolveFormatVersion);
  }

  int resolveFormatVersion(Map<String, Object> map) {
    Object formatVersion = map.get("formatVersion");
    if (formatVersion == null) {
      return 1;
    }
    if (formatVersion instanceof Number) {
      Number number = (Number) formatVersion;
      int version = number.intValue();
      if (version == 1 || version == 2) {
        return version;
      }
      throw new FlowDefinitionCodecException("Unsupported definition formatVersion: " + version);
    }
    String version = String.valueOf(formatVersion).trim();
    if ("".equals(version) || "1".equals(version)) {
      return 1;
    }
    if ("2".equals(version)) {
      return 2;
    }
    throw new FlowDefinitionCodecException("Unsupported definition formatVersion: " + version);
  }
}
