package com.ouroboros.workflow.approval.semantics;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.ouroboros.workflow.approval.definition.FlowNode;
import com.ouroboros.workflow.approval.definition.graph.StructuredFlowDefinition;
import com.ouroboros.workflow.approval.definition.nodes.JoinNode;
import com.ouroboros.workflow.approval.definition.v2.FlowEdgeType;
import com.ouroboros.workflow.approval.definition.v2.FlowEdgeV2;
import com.ouroboros.workflow.approval.semantics.JoinType;

/**
 * Stable V2 split 语义 helper。
 *
 * <p>当前产品级 V2 语义不再通过“全无条件边 -> AND / 混合条件边 -> OR”做隐式推导。
 * 任意节点只要 {@code outgoing > 1} 就形成分流起点，默认按 XOR 理解；
 * 只有当这些分支明确汇入 {@code JoinNode(joinType=OR)} 时，才按 OR split 处理。
 */
public final class StableSplitSemantics {

  private StableSplitSemantics() {
  }

  public static SplitType inferSplitType(StructuredFlowDefinition definition, List<FlowEdgeV2> outgoingEdges) {
    if (outgoingEdges == null || outgoingEdges.size() <= 1) {
      return SplitType.XOR;
    }
    if (definition != null && hasCommonExplicitOrJoin(definition, outgoingEdges)) {
      return SplitType.OR;
    }
    return SplitType.XOR;
  }

  private static boolean hasCommonExplicitOrJoin(StructuredFlowDefinition definition, List<FlowEdgeV2> outgoingEdges) {
    Set<String> commonJoinIds = null;
    for (FlowEdgeV2 edge : outgoingEdges) {
      if (edge == null || edge.getType() != FlowEdgeType.SEQUENCE) {
        continue;
      }
      Set<String> reachableJoinIds = collectReachableOrJoinIds(definition, edge.getTargetId());
      if (reachableJoinIds.isEmpty()) {
        return false;
      }
      if (commonJoinIds == null) {
        commonJoinIds = new LinkedHashSet<String>(reachableJoinIds);
      } else {
        commonJoinIds.retainAll(reachableJoinIds);
      }
      if (commonJoinIds.isEmpty()) {
        return false;
      }
    }
    return commonJoinIds != null && !commonJoinIds.isEmpty();
  }

  private static Set<String> collectReachableOrJoinIds(StructuredFlowDefinition definition, String startNodeId) {
    if (definition == null || startNodeId == null) {
      return Collections.emptySet();
    }
    ArrayDeque<String> queue = new ArrayDeque<String>();
    Set<String> visited = new HashSet<String>();
    Set<String> reachableJoinIds = new LinkedHashSet<String>();
    queue.add(startNodeId);

    while (!queue.isEmpty()) {
      String currentId = queue.removeFirst();
      if (!visited.add(currentId)) {
        continue;
      }
      FlowNode node = definition.getNode(currentId);
      if (node instanceof JoinNode) {
        JoinType joinType = JoinType.fromString(((JoinNode) node).getJoinType());
        if (joinType == JoinType.OR) {
          reachableJoinIds.add(node.getId());
          continue;
        }
      }
      definition.getOutgoingEdges(currentId).stream()
          .filter(edge -> edge.getType() == FlowEdgeType.SEQUENCE)
          .map(FlowEdgeV2::getTargetId)
          .filter(Objects::nonNull)
          .forEach(queue::addLast);
    }

    return reachableJoinIds;
  }
}
