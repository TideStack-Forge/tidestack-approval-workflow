package com.ouroboros.workflow.approval.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;

import com.ouroboros.workflow.approval.definition.FlowNode;
import com.ouroboros.workflow.approval.definition.NodeBranch;
import com.ouroboros.workflow.approval.definition.graph.StructuredFlowDefinition;
import com.ouroboros.workflow.approval.definition.nodes.ForkNode;
import com.ouroboros.workflow.approval.definition.nodes.StartNode;
import com.ouroboros.workflow.approval.definition.v2.FlowEdgeType;

public class DefinitionUtil {
  /**
   * 从发起节点出发的深度优先**前序**遍历，并顺带建立 prev/next 指针。
   *
   * <p>方法名此前是 {@code topologicalSort}，但它不是拓扑排序：只做 DFS 前序
   * （`ordered.add(node)` 在递归子节点之前），既没有入度统计也没有后序。
   * 例如 A→B、A→C、C→B 会返回 [A, B, C]（B 排在它的前驱 C 之前）。
   *
   * <p>改名为前序遍历是因为**没有任何调用方需要真正的拓扑序**：
   * <ul>
   *   <li>转译器（{@code TranspileContext}）只把结果当"节点处理顺序"，
   *       每个 processor 各自读自己的节点与出边；</li>
   *   <li>{@code StructuredFlowRuntimeNavigator} 只从结果里筛出发起/审批节点，与顺序无关；</li>
   *   <li>V1 兼容路径（{@code FlowDefinition.getTreeGraphNodes}）依赖的正是这套遍历顺序
   *       建立的 prev/next 指针。</li>
   * </ul>
   * 因此这里刻意**保持既有遍历顺序不变**（改顺序会连带改变 prev/next 与回退目标），
   * 只让名字与语义一致。
   */
  static public List<TreeGraphNode> traversePreOrder(List<FlowNode> nodes) {
    var nodeMap = nodes.stream()
        .map(TreeGraphNode::new)
        .collect(Collectors.toMap(
            n -> n.getFlowNode().getId(),
            Function.identity(),
            (v1, v2) -> v2
        ));

    // 设置next和prev，设置next的时候会自动设置prev
    nodeMap.values()
        .forEach(n -> {
          final var nextId = n.getFlowNode().getNext();
          if (StringUtils.isBlank(nextId)) {
            return;
          }
          n.setNextNode(nodeMap.get(nextId));
        });

    // 获取起始节点
    var list = nodes.stream()
        .filter(StartNode.class::isInstance)
        .map(startNode -> nodeMap.get(startNode.getId()))
        .collect(ArrayList<TreeGraphNode>::new, ArrayList::add, ArrayList::addAll);

    // 取其他节点
    var others = list.stream()
        .map(n -> getSubsequentNodes(nodeMap, n))
        .collect(ArrayList<TreeGraphNode>::new, ArrayList::addAll, ArrayList::addAll);

    list.addAll(others);

    return list;
  }

  /** 见 {@link #traversePreOrder(List)}：结构化定义入口，语义相同。 */
  static public List<TreeGraphNode> traversePreOrder(StructuredFlowDefinition definition) {
    List<FlowNode> nodes = ObjectUtils.defaultIfNull(definition.getNodes(), Collections.<FlowNode>emptyList());
    var nodeMap = nodes
        .stream()
        .map(TreeGraphNode::new)
        .collect(Collectors.toMap(
            n -> n.getFlowNode().getId(),
            Function.identity(),
            (v1, v2) -> v2
        ));
    var ordered = new ArrayList<TreeGraphNode>();
    var visited = new LinkedHashSet<String>();

    var startNodes = nodes
        .stream()
        .filter(StartNode.class::isInstance)
        .map(startNode -> nodeMap.get(startNode.getId()))
        .collect(Collectors.toList());

    for (var startNode : startNodes) {
      traverseStructured(definition, nodeMap, startNode, ordered, visited);
    }

    for (var node : nodes) {
      if (!visited.contains(node.getId())) {
        traverseStructured(definition, nodeMap, nodeMap.get(node.getId()), ordered, visited);
      }
    }

    return ordered;
  }

  private static void traverseStructured(
      StructuredFlowDefinition definition,
      Map<String, TreeGraphNode> nodeMap,
      TreeGraphNode node,
      List<TreeGraphNode> ordered,
      LinkedHashSet<String> visited) {
    if (node == null) {
      return;
    }
    if (!visited.add(node.getFlowNode().getId())) {
      return;
    }
    ordered.add(node);

    var outgoingEdges = definition.getOutgoingEdges(node.getFlowNode().getId());
    TreeGraphNode firstNext = null;
    for (var edge : outgoingEdges) {
      if (edge.getType() != FlowEdgeType.SEQUENCE) {
        continue;
      }
      var target = nodeMap.get(edge.getTargetId());
      if (target == null) {
        continue;
      }
      if (target.getPrevNode() == null) {
        target.setPrevNode(node);
      }
      if (firstNext == null) {
        firstNext = target;
      }
      traverseStructured(definition, nodeMap, target, ordered, visited);
    }
    if (firstNext != null && node.getNextNode() == null) {
      node.setNextNode(firstNext);
    }
  }

  static private List<TreeGraphNode> getSubsequentNodes(Map<String, TreeGraphNode> nodeMap, TreeGraphNode node) {
    ArrayList<TreeGraphNode> nextNodes = new ArrayList<TreeGraphNode>();

    // 如果是分流节点，需要先将分支节点加入
    if (node.getFlowNode() instanceof ForkNode) {
      ForkNode forkNode = (ForkNode) node.getFlowNode();
      List<TreeGraphNode> branches = forkNode.getBranches().stream()
          .map(NodeBranch::getFirst)
          .map(nodeMap::get)
          .filter(Objects::nonNull)
          // 取各个分支的后续节点，并将入口点和后续节点合并
          .<TreeGraphNode>flatMap(entryNode -> {
            List<TreeGraphNode> branchNextNodes = getSubsequentNodes(nodeMap, entryNode);
            return Stream.concat(Stream.of(entryNode), branchNextNodes.stream());
          })
          // 设置分支节点的父节点，要做个判断，防止将下级的分流节点的父节点设为当前分流节点
          .peek(child -> {
            if (child.getParentNode() == null) {
              child.setParentNode(node);
            }
          })
          .collect(Collectors.toList());
      nextNodes.addAll(branches);
    }

    // 取下个节点，如果没有下个节点则直接返回
    TreeGraphNode next = node.getNextNode();
    if (next == null) {
      return nextNodes;
    }

    // 加入下一个节点，并递归获取下下个节点
    nextNodes.add(next);
    nextNodes.addAll(getSubsequentNodes(nodeMap, next));
    return nextNodes;
  }

}
