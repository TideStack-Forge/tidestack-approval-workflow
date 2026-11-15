package com.ouroboros.workflow.approval.definition.graph;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;

import com.ouroboros.workflow.approval.definition.FlowNode;
import com.ouroboros.workflow.approval.definition.codec.FlowDefinitionCodecException;
import com.ouroboros.workflow.approval.definition.nodes.CcNode;
import com.ouroboros.workflow.approval.definition.nodes.EndNode;
import com.ouroboros.workflow.approval.definition.nodes.ForkNode;
import com.ouroboros.workflow.approval.definition.nodes.GatewayNode;
import com.ouroboros.workflow.approval.definition.nodes.HandleNode;
import com.ouroboros.workflow.approval.definition.nodes.JoinNode;
import com.ouroboros.workflow.approval.definition.nodes.StartNode;
import com.ouroboros.workflow.approval.definition.v2.FlowEdgeType;
import com.ouroboros.workflow.approval.definition.v2.FlowEdgeV2;
import com.ouroboros.workflow.approval.semantics.JoinType;

public class StructuredFlowGraphValidator {
  public StructuredFlowDefinition validate(StructuredFlowDefinition definition) {
    if (definition == null) {
      throw new FlowDefinitionCodecException("流程定义不能为空");
    }

    List<FlowNode> nodes = ObjectUtils.defaultIfNull(definition.getNodes(), Collections.<FlowNode>emptyList());
    List<FlowEdgeV2> edges = ObjectUtils.defaultIfNull(definition.getEdges(), Collections.<FlowEdgeV2>emptyList());
    validateDuplicateIds(nodes);
    validateStartNodeCount(nodes);
    validateEdges(definition, edges);
    validateOutgoingEdges(definition, nodes);
    validateReachability(definition, nodes);
    detectCycle(definition);
    return definition;
  }

  private void validateDuplicateIds(List<FlowNode> nodes) {
    List<String> duplicateIds = nodes.stream()
        .map(FlowNode::getId)
        .filter(StringUtils::isNotBlank)
        .collect(Collectors.groupingBy(id -> id, Collectors.counting()))
        .entrySet()
        .stream()
        .filter(entry -> entry.getValue() > 1)
        .map(java.util.Map.Entry::getKey)
        .collect(Collectors.toList());
    if (!duplicateIds.isEmpty()) {
      throw new FlowDefinitionCodecException("节点ID重复: " + duplicateIds);
    }
  }

  private void validateStartNodeCount(List<FlowNode> nodes) {
    long startNodeCount = nodes.stream().filter(StartNode.class::isInstance).count();
    if (startNodeCount != 1) {
      throw new FlowDefinitionCodecException("流程必须且只能包含一个发起节点");
    }
  }

  private void validateEdges(
      StructuredFlowDefinition definition,
      List<FlowEdgeV2> edges) {
    boolean isV2 = Integer.valueOf(2).equals(definition.getFormatVersion());
    for (FlowEdgeV2 edge : edges) {
      if (edge.getType() == null) {
        throw new FlowDefinitionCodecException("边类型不能为空");
      }
      if (isV2 && edge.getType() != FlowEdgeType.SEQUENCE) {
        throw new FlowDefinitionCodecException("仅支持SEQUENCE边: " + edge.getId());
      }
      if (StringUtils.isBlank(edge.getSourceId()) || StringUtils.isBlank(edge.getTargetId())) {
        throw new FlowDefinitionCodecException("边的起点和终点不能为空");
      }
      if (definition.getNode(edge.getSourceId()) == null || definition.getNode(edge.getTargetId()) == null) {
        throw new FlowDefinitionCodecException("边引用了不存在的节点: " + edge.getId());
      }
    }
  }

  private void validateOutgoingEdges(
      StructuredFlowDefinition definition,
      List<FlowNode> nodes) {
    boolean isV2 = Integer.valueOf(2).equals(definition.getFormatVersion());
    if (!isV2) {
      return;
    }

    for (FlowNode node : nodes) {
      if (node instanceof ForkNode) {
        throw new FlowDefinitionCodecException("ForkNode is not supported in V2 format");
      }
      if (node instanceof GatewayNode) {
        throw new FlowDefinitionCodecException(
            "GatewayNode is not supported in V2 format. "
            + "Use normal nodes with multiple outgoing edges for branching.");
      }
      if (node instanceof JoinNode) {
        JoinNode joinNode = (JoinNode) node;
        String joinType = joinNode.getJoinType();
        JoinType normalizedJoinType = JoinType.fromString(joinType);
        if (normalizedJoinType == null) {
          throw new FlowDefinitionCodecException("JoinNode仅支持joinType=OR: " + node.getId());
        }
        if (normalizedJoinType != JoinType.OR) {
          throw new FlowDefinitionCodecException("当前V2稳定合同仅支持JoinNode(joinType=OR): " + node.getId());
        }
      }
      if (node instanceof CcNode && ObjectUtils.isEmpty(node.getCandidates())) {
        throw new FlowDefinitionCodecException("CcNode的candidates不能为空: " + node.getId());
      }
      if (node instanceof HandleNode && ObjectUtils.isEmpty(node.getCandidates())) {
        throw new FlowDefinitionCodecException("HandleNode的candidates不能为空: " + node.getId());
      }
      if (node instanceof EndNode && !definition.getOutgoingEdges(node.getId()).isEmpty()) {
        throw new FlowDefinitionCodecException("EndNode不允许存在出边: " + node.getId());
      }
    }
  }

  private void validateReachability(
      StructuredFlowDefinition definition,
      List<FlowNode> nodes) {
    java.util.Optional<FlowNode> startFlowNode = definition.getStartFlowNode();
    if (!startFlowNode.isPresent()) {
      throw new FlowDefinitionCodecException("流程必须包含发起节点");
    }
    FlowNode startNode = startFlowNode.get();
    Set<String> reachableNodes = collectReachableNodes(definition, startNode.getId());
    if (reachableNodes.size() != nodes.size()) {
      throw new FlowDefinitionCodecException("流程存在不可达节点");
    }
  }

  private Set<String> collectReachableNodes(StructuredFlowDefinition definition, String startNodeId) {
    Set<String> visited = new HashSet<String>();
    Deque<String> deque = new ArrayDeque<String>();
    deque.add(startNodeId);

    while (!deque.isEmpty()) {
      String current = deque.removeFirst();
      if (!visited.add(current)) {
        continue;
      }
      definition.getOutgoingEdges(current).stream()
          .map(FlowEdgeV2::getTargetId)
          .filter(Objects::nonNull)
          .forEach(deque::addLast);
    }
    return visited;
  }

  private void detectCycle(StructuredFlowDefinition definition) {
    Set<String> visited = new HashSet<String>();
    Set<String> visiting = new HashSet<String>();
    for (FlowNode node : definition.getNodes()) {
      if (!visited.contains(node.getId())) {
        detectCycle(definition, node.getId(), visited, visiting);
      }
    }
  }

  private void detectCycle(
      StructuredFlowDefinition definition,
      String currentId,
      Set<String> visited,
      Set<String> visiting) {
    if (visiting.contains(currentId)) {
      throw new FlowDefinitionCodecException("当前结构化审批流不支持环路: " + currentId);
    }
    if (visited.contains(currentId)) {
      return;
    }
    visiting.add(currentId);
    definition.getOutgoingEdges(currentId).stream()
        .map(FlowEdgeV2::getTargetId)
        .filter(Objects::nonNull)
        .forEach(targetId -> detectCycle(definition, targetId, visited, visiting));
    visiting.remove(currentId);
    visited.add(currentId);
  }
}
