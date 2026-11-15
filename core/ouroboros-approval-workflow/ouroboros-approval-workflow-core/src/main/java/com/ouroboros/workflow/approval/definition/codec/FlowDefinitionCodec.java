package com.ouroboros.workflow.approval.definition.codec;

import io.vavr.control.Try;

import com.ouroboros.workflow.approval.definition.FlowDefinition;
import com.ouroboros.workflow.approval.definition.graph.StructuredFlowDefinition;
import com.ouroboros.workflow.approval.definition.v2.FlowDefinitionV2;

public interface FlowDefinitionCodec {
  Try<StructuredFlowDefinition> decode(String rawJson);

  Try<FlowDefinition> encodeV1(StructuredFlowDefinition definition);

  Try<FlowDefinitionV2> encodeV2(StructuredFlowDefinition definition);
}
