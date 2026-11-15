package com.ouroboros.workflow.approval.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.apache.commons.lang3.ObjectUtils;

import com.ouroboros.workflow.approval.definition.Candidate;
import com.ouroboros.workflow.approval.definition.FlowNode;
import com.ouroboros.workflow.approval.definition.candidates.UserSelectCandidate;
import com.ouroboros.workflow.approval.definition.graph.StructuredFlowDefinition;
import com.ouroboros.workflow.approval.definition.nodes.ApprovalNode;
import com.ouroboros.workflow.approval.definition.nodes.StartNode;
import com.ouroboros.workflow.approval.definition.v2.FlowEdgeType;
import com.ouroboros.workflow.approval.definition.v2.FlowEdgeV2;

public class StructuredFlowRuntimeNavigator {
  public List<FlowNode> getFlowNodes(StructuredFlowDefinition definition) {
    return DefinitionUtil.traversePreOrder(definition).stream()
        .map(TreeGraphNode::getFlowNode)
        .filter(node -> node instanceof StartNode || node instanceof ApprovalNode)
        .collect(Collectors.toList());
  }

  public List<ApprovalNode> getUserSelectCandidateApprovalNodes(StructuredFlowDefinition definition) {
    return getFlowNodes(definition).stream()
        .filter(ApprovalNode.class::isInstance)
        .map(ApprovalNode.class::cast)
        .filter(this::isUserSelectCandidateApprovalNode)
        .collect(Collectors.toList());
  }

  public List<ApprovalNode> getUserSelectCandidateApprovalNodes(
      StructuredFlowDefinition definition,
      Predicate<FlowEdgeV2> branchMatcher) {
    if (definition == null) {
      return Collections.emptyList();
    }
    if (branchMatcher == null) {
      return getUserSelectCandidateApprovalNodes(definition);
    }

    List<ApprovalNode> result = new ArrayList<ApprovalNode>();
    FlowNode currentNode = definition.getStartFlowNode().orElse(null);

    while (currentNode != null) {
      if (currentNode instanceof ApprovalNode) {
        ApprovalNode approvalNode = (ApprovalNode) currentNode;
        if (isUserSelectCandidateApprovalNode(approvalNode)) {
          result.add(approvalNode);
        }
      }

      List<FlowEdgeV2> outgoing = definition.getOutgoingEdges(currentNode.getId()).stream()
          .filter(edge -> edge.getType() == FlowEdgeType.SEQUENCE)
          .collect(Collectors.toList());

      if (outgoing.isEmpty()) {
        currentNode = null;
        continue;
      }

      if (outgoing.size() == 1) {
        currentNode = definition.getNode(outgoing.get(0).getTargetId());
        continue;
      }

      Optional<FlowEdgeV2> selectedEdge = outgoing.stream().filter(branchMatcher).findFirst();
      if (!selectedEdge.isPresent()) {
        return Collections.emptyList();
      }
      currentNode = definition.getNode(selectedEdge.get().getTargetId());
    }

    return result;
  }

  private boolean isUserSelectCandidateApprovalNode(ApprovalNode node) {
    return ObjectUtils.defaultIfNull(node.getCandidates(), Collections.<Candidate>emptyList()).stream()
        .anyMatch(UserSelectCandidate.class::isInstance);
  }
}
