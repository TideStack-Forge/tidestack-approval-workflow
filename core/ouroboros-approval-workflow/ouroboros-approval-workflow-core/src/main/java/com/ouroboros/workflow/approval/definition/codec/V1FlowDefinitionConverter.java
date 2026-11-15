package com.ouroboros.workflow.approval.definition.codec;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;

import com.ouroboros.util.Json;
import com.ouroboros.workflow.approval.definition.FlowDefinition;
import com.ouroboros.workflow.approval.definition.FlowNode;
import com.ouroboros.workflow.approval.definition.NodeBranch;
import com.ouroboros.workflow.approval.definition.graph.StructuredFlowDefinition;
import com.ouroboros.workflow.approval.definition.nodes.ForkNode;
import com.ouroboros.workflow.approval.definition.nodes.JoinNode;
import com.ouroboros.workflow.approval.definition.v2.FlowEdgeType;
import com.ouroboros.workflow.approval.definition.v2.FlowEdgeV2;

/**
 * 将 V1 FlowDefinition 转换为 V2 结构化定义。
 */
public class V1FlowDefinitionConverter {
  public StructuredFlowDefinition convert(FlowDefinition definition) {
    StructuredFlowDefinition structured = createStructuredDefinition(definition);
    List<FlowNode> sourceNodes = ObjectUtils.defaultIfNull(definition.getNodes(), Collections.<FlowNode>emptyList());
    Map<String, FlowNode> sourceNodeMap = buildSourceNodeMap(sourceNodes);

    List<FlowNode> v2Nodes = new ArrayList<FlowNode>();
    List<FlowEdgeV2> v2Edges = new ArrayList<FlowEdgeV2>();
    Map<String, ForkNode> forkNodes = collectForkNodesAndCloneLinearNodes(sourceNodes, v2Nodes);

    appendJoinNodes(forkNodes, v2Nodes);
    appendIncomingEdges(sourceNodes, forkNodes, v2Edges);
    appendForkExitEdges(forkNodes, sourceNodeMap, v2Edges);

    structured.setNodes(v2Nodes);
    structured.setEdges(deduplicateAndSort(v2Edges));
    return structured;
  }

  private StructuredFlowDefinition createStructuredDefinition(FlowDefinition definition) {
    StructuredFlowDefinition structured = new StructuredFlowDefinition();
    structured.setFormatVersion(2);
    structured.setId(definition.getId());
    structured.setName(definition.getName());
    structured.setCategory(definition.getCategory());
    structured.setIcon(definition.getIcon());
    structured.setDescription(definition.getDescription());
    structured.setFormType(definition.getFormType());
    structured.setFormKey(definition.getFormKey());
    structured.setNotification(definition.getNotification());
    return structured;
  }

  private Map<String, FlowNode> buildSourceNodeMap(List<FlowNode> sourceNodes) {
    Map<String, FlowNode> sourceNodeMap = new LinkedHashMap<String, FlowNode>();
    for (FlowNode node : sourceNodes) {
      sourceNodeMap.put(node.getId(), node);
    }
    return sourceNodeMap;
  }

  private Map<String, ForkNode> collectForkNodesAndCloneLinearNodes(
      List<FlowNode> sourceNodes, List<FlowNode> v2Nodes) {
    Map<String, ForkNode> forkNodes = new LinkedHashMap<String, ForkNode>();
    for (FlowNode node : sourceNodes) {
      if (node instanceof ForkNode) {
        forkNodes.put(node.getId(), (ForkNode) node);
        continue;
      }
      v2Nodes.add(cloneNodeWithoutNext(node));
    }
    return forkNodes;
  }

  private FlowNode cloneNodeWithoutNext(FlowNode node) {
    FlowNode cloned = Json.toBean(FlowNode.class, Json.toJsonString(node));
    cloned.setNext(null);
    return cloned;
  }

  private void appendJoinNodes(Map<String, ForkNode> forkNodes, List<FlowNode> v2Nodes) {
    for (ForkNode forkNode : forkNodes.values()) {
      if (!isOrFork(forkNode)) {
        continue;
      }
      JoinNode joinNode = new JoinNode();
      joinNode.setId(buildJoinId(forkNode.getId()));
      joinNode.setName(StringUtils.defaultIfBlank(forkNode.getName(), forkNode.getId()) + " Join");
      joinNode.setJoinType("OR");
      v2Nodes.add(joinNode);
    }
  }

  private void appendIncomingEdges(
      List<FlowNode> sourceNodes, Map<String, ForkNode> forkNodes, List<FlowEdgeV2> v2Edges) {
    for (FlowNode source : sourceNodes) {
      if (source instanceof ForkNode || StringUtils.isBlank(source.getNext())) {
        continue;
      }

      ForkNode targetFork = forkNodes.get(source.getNext());
      if (targetFork == null) {
        v2Edges.add(buildSequenceEdge(source.getId(), source.getNext(), null, null, null));
        continue;
      }
      appendForkBranchEdges(source, targetFork, v2Edges);
    }
  }

  private void appendForkBranchEdges(FlowNode source, ForkNode targetFork, List<FlowEdgeV2> v2Edges) {
    List<NodeBranch> branches = sortedBranches(targetFork);
    String edgeTypeHint = isOrFork(targetFork) ? "or" : "xor";
    for (int i = 0; i < branches.size(); i++) {
      NodeBranch branch = branches.get(i);
      if (StringUtils.isBlank(branch.getFirst())) {
        continue;
      }
      v2Edges.add(buildSequenceEdge(
          source.getId(),
          branch.getFirst(),
          StringUtils.defaultIfBlank(branch.getId(), buildEdgeId(source.getId(), branch.getFirst(), edgeTypeHint)),
          branch.getName(),
          branch.getCondition(),
          i + 1));
    }
  }

  private void appendForkExitEdges(
      Map<String, ForkNode> forkNodes,
      Map<String, FlowNode> sourceNodeMap,
      List<FlowEdgeV2> v2Edges) {
    for (ForkNode forkNode : forkNodes.values()) {
      if (isOrFork(forkNode)) {
        appendOrForkExitEdges(forkNode, sourceNodeMap, forkNodes, v2Edges);
        continue;
      }
      appendLinearForkExitEdges(forkNode, sourceNodeMap, forkNodes, v2Edges);
    }
  }

  private void appendOrForkExitEdges(
      ForkNode forkNode,
      Map<String, FlowNode> sourceNodeMap,
      Map<String, ForkNode> forkNodes,
      List<FlowEdgeV2> v2Edges) {
    String joinId = buildJoinId(forkNode.getId());
    appendBranchTailEdges(forkNode, joinId, sourceNodeMap, forkNodes, v2Edges);
    if (StringUtils.isNotBlank(forkNode.getNext())) {
      v2Edges.add(buildSequenceEdge(joinId, forkNode.getNext(), null, null, null));
    }
  }

  private void appendLinearForkExitEdges(
      ForkNode forkNode,
      Map<String, FlowNode> sourceNodeMap,
      Map<String, ForkNode> forkNodes,
      List<FlowEdgeV2> v2Edges) {
    if (StringUtils.isBlank(forkNode.getNext())) {
      return;
    }
    appendBranchTailEdges(forkNode, forkNode.getNext(), sourceNodeMap, forkNodes, v2Edges);
  }

  private void appendBranchTailEdges(
      ForkNode forkNode,
      String targetId,
      Map<String, FlowNode> sourceNodeMap,
      Map<String, ForkNode> forkNodes,
      List<FlowEdgeV2> v2Edges) {
    for (NodeBranch branch : sortedBranches(forkNode)) {
      if (StringUtils.isBlank(branch.getFirst())) {
        continue;
      }
      for (String tailId : collectBranchTailIds(branch.getFirst(), sourceNodeMap, forkNodes)) {
        v2Edges.add(buildSequenceEdge(tailId, targetId, null, null, null));
      }
    }
  }

  private List<NodeBranch> sortedBranches(ForkNode forkNode) {
    List<NodeBranch> branches = new ArrayList<NodeBranch>(
        ObjectUtils.defaultIfNull(forkNode.getBranches(), Collections.<NodeBranch>emptyList()));
    branches.sort(Comparator.comparing(branch -> branch.getId() == null ? "" : branch.getId()));
    return branches;
  }

  private boolean isOrFork(ForkNode forkNode) {
    return StringUtils.equalsIgnoreCase("inclusive", forkNode.getLogic())
        || StringUtils.equalsIgnoreCase("or", forkNode.getLogic());
  }

  private String buildJoinId(String forkId) {
    return forkId + "__join";
  }

  private String buildEdgeId(String sourceId, String targetId, String tag) {
    return "seq-" + sourceId + "-" + targetId + (StringUtils.isBlank(tag) ? "" : "-" + tag);
  }

  private FlowEdgeV2 buildSequenceEdge(
      String sourceId,
      String targetId,
      String edgeId,
      String edgeName,
      com.ouroboros.workflow.approval.definition.condition.Condition condition,
      Integer order) {
    FlowEdgeV2 edge = new FlowEdgeV2();
    edge.setId(StringUtils.defaultIfBlank(edgeId, buildEdgeId(sourceId, targetId, null)));
    edge.setType(FlowEdgeType.SEQUENCE);
    edge.setSourceId(sourceId);
    edge.setTargetId(targetId);
    edge.setName(edgeName);
    edge.setCondition(condition);
    edge.setOrder(order);
    return edge;
  }

  private FlowEdgeV2 buildSequenceEdge(
      String sourceId,
      String targetId,
      String edgeId,
      String edgeName,
      com.ouroboros.workflow.approval.definition.condition.Condition condition) {
    return buildSequenceEdge(sourceId, targetId, edgeId, edgeName, condition, null);
  }

  private List<String> collectBranchTailIds(
      String startId,
      Map<String, FlowNode> nodeMap,
      Map<String, ForkNode> forkNodes) {
    LinkedHashSet<String> tails = new LinkedHashSet<String>();
    String current = startId;
    LinkedHashSet<String> visited = new LinkedHashSet<String>();
    while (StringUtils.isNotBlank(current) && visited.add(current)) {
      FlowNode node = nodeMap.get(current);
      if (node == null) {
        tails.add(current);
        break;
      }
      if (forkNodes.containsKey(current)) {
        tails.add(current);
        break;
      }
      if (StringUtils.isBlank(node.getNext())) {
        tails.add(current);
        break;
      }
      current = node.getNext();
    }
    if (tails.isEmpty() && StringUtils.isNotBlank(startId)) {
      tails.add(startId);
    }
    return new ArrayList<String>(tails);
  }

  private List<FlowEdgeV2> deduplicateAndSort(List<FlowEdgeV2> edges) {
    Map<String, FlowEdgeV2> unique = new LinkedHashMap<String, FlowEdgeV2>();
    for (FlowEdgeV2 edge : edges) {
      String key = edge.getSourceId() + "->" + edge.getTargetId() + "#"
          + StringUtils.defaultString(edge.getCondition() == null ? null : edge.getCondition().toExpression());
      if (!unique.containsKey(key)) {
        unique.put(key, edge);
      }
    }
    ArrayList<FlowEdgeV2> result = new ArrayList<FlowEdgeV2>(unique.values());
    result.sort(Comparator
        .comparing((FlowEdgeV2 edge) -> edge.getSourceId() == null ? "" : edge.getSourceId())
        .thenComparing(edge -> edge.getTargetId() == null ? "" : edge.getTargetId())
        .thenComparing(edge -> edge.getId() == null ? "" : edge.getId()));
    return result;
  }
}
