package com.ouroboros.workflow.approval.definition.codec;

import java.util.Iterator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ouroboros.workflow.approval.definition.nodes.BuiltInFlowNodeTypes;

public class FlowDefinitionV2Canonicalizer {
  public JsonNode canonicalize(JsonNode definition) {
    if (!(definition instanceof ObjectNode)) {
      return definition;
    }

    ObjectNode root = ((ObjectNode) definition).deepCopy();
    canonicalizeNodes(root.get("nodes"));
    canonicalizeEdges(root.get("edges"));
    return root;
  }

  private void canonicalizeNodes(JsonNode nodesNode) {
    if (!(nodesNode instanceof ArrayNode)) {
      return;
    }

    Iterator<JsonNode> iterator = nodesNode.elements();
    while (iterator.hasNext()) {
      JsonNode node = iterator.next();
      if (!(node instanceof ObjectNode)) {
        continue;
      }

      ObjectNode objectNode = (ObjectNode) node;
      JsonNode typeNode = objectNode.get("type");
      if (typeNode == null || !typeNode.isTextual()) {
        continue;
      }

      String canonicalType = BuiltInFlowNodeTypes.normalize(typeNode.asText());
      objectNode.put("type", canonicalType);
      if (BuiltInFlowNodeTypes.SUBFLOW.equals(canonicalType)) {
        canonicalizeSubflowNode(objectNode);
      }
    }
  }

  private void canonicalizeEdges(JsonNode edgesNode) {
    if (!(edgesNode instanceof ArrayNode)) {
      return;
    }

    // 这里此前会删除 edge.name，导致"同一份 JSON 两条解析路径得到不同对象"：
    // V2FlowDefinitionCodec.decode 直接解析原始 JSON 会保留 name，而经过 canonicalize 的路径会丢掉它。
    // edge.name 不是稳定 V2 contract（gateway-join-semantics §边语义），但也不是需要主动清除的脏数据，
    // 因此两条路径统一保留，由使用方按契约自行忽略。
    Iterator<JsonNode> iterator = edgesNode.elements();
    while (iterator.hasNext()) {
      iterator.next();
    }
  }

  private void canonicalizeSubflowNode(ObjectNode node) {
    JsonNode subProcessIdNode = node.get("subProcessId");
    if (!node.has("subFlowKey") && subProcessIdNode != null) {
      node.set("subFlowKey", subProcessIdNode.deepCopy());
    }
    node.remove("subProcessId");
  }

}
