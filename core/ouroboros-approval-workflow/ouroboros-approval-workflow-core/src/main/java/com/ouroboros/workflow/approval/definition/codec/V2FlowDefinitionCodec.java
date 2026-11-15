package com.ouroboros.workflow.approval.definition.codec;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.ObjectUtils;

import io.vavr.control.Try;

import com.ouroboros.util.Json;
import com.ouroboros.workflow.approval.definition.FlowNode;
import com.ouroboros.workflow.approval.definition.graph.StructuredFlowDefinition;
import com.ouroboros.workflow.approval.definition.graph.StructuredFlowGraphValidator;
import com.ouroboros.workflow.approval.definition.v2.FlowDefinitionV2;
import com.ouroboros.workflow.approval.definition.v2.FlowEdgeV2;

public class V2FlowDefinitionCodec implements FlowDefinitionCodec {
  final StructuredFlowGraphValidator validator = new StructuredFlowGraphValidator();

  @Override
  public Try<StructuredFlowDefinition> decode(String rawJson) {
    return Json.tryToBean(FlowDefinitionV2.class, rawJson)
        .mapTry(this::toStructuredFlowDefinition)
        .mapTry(validator::validate);
  }

  @Override
  public Try<com.ouroboros.workflow.approval.definition.FlowDefinition> encodeV1(StructuredFlowDefinition definition) {
    return new V1FlowDefinitionCodec().encodeV1(definition);
  }

  @Override
  public Try<FlowDefinitionV2> encodeV2(StructuredFlowDefinition definition) {
    return Try.of(() -> {
      validator.validate(definition);

      FlowDefinitionV2 result = new FlowDefinitionV2();
      result.setFormatVersion(2);
      result.setId(definition.getId());
      result.setName(definition.getName());
      result.setCategory(definition.getCategory());
      result.setIcon(definition.getIcon());
      result.setDescription(definition.getDescription());
      result.setFormType(definition.getFormType());
      result.setFormKey(definition.getFormKey());
      result.setNotification(definition.getNotification());
      if (definition.getEditorLayout() != null) {
        result.setEditorLayout(definition.getEditorLayout().deepCopy());
      }

      List<FlowNode> nodes = new ArrayList<FlowNode>();
      for (var node : ObjectUtils.defaultIfNull(definition.getNodes(), Collections.<FlowNode>emptyList())) {
        var cloned = Json.toBean(FlowNode.class, Json.toJsonString(node));
        cloned.setNext(null);
        nodes.add(cloned);
      }
      result.setNodes(nodes);

      List<FlowEdgeV2> edges = new ArrayList<FlowEdgeV2>();
      for (var edge : ObjectUtils.defaultIfNull(definition.getEdges(), Collections.<FlowEdgeV2>emptyList())) {
        edges.add(Json.toBean(FlowEdgeV2.class, Json.toJsonString(edge)));
      }
      result.setEdges(edges);
      return result;
    });
  }

  StructuredFlowDefinition toStructuredFlowDefinition(FlowDefinitionV2 definition) {
    StructuredFlowDefinition structured = new StructuredFlowDefinition();
    structured.setFormatVersion(definition.getFormatVersion() == null ? 2 : definition.getFormatVersion());
    structured.setId(definition.getId());
    structured.setName(definition.getName());
    structured.setCategory(definition.getCategory());
    structured.setIcon(definition.getIcon());
    structured.setDescription(definition.getDescription());
    structured.setFormType(definition.getFormType());
    structured.setFormKey(definition.getFormKey());
    structured.setNotification(definition.getNotification());
    if (definition.getEditorLayout() != null) {
      structured.setEditorLayout(definition.getEditorLayout().deepCopy());
    }
    structured.setNodes(ObjectUtils.defaultIfNull(definition.getNodes(), Collections.<FlowNode>emptyList()));
    structured.setEdges(ObjectUtils.defaultIfNull(definition.getEdges(), Collections.<FlowEdgeV2>emptyList()));
    return structured;
  }
}
