package com.ouroboros.workflow.approval.definition.codec;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;

import io.vavr.control.Try;

import com.ouroboros.util.Json;
import com.ouroboros.workflow.approval.definition.FlowDefinition;
import com.ouroboros.workflow.approval.definition.FlowNode;
import com.ouroboros.workflow.approval.definition.NodeBranch;
import com.ouroboros.workflow.approval.definition.graph.StructuredFlowDefinition;
import com.ouroboros.workflow.approval.definition.graph.StructuredFlowGraphValidator;
import com.ouroboros.workflow.approval.definition.nodes.ForkNode;
import com.ouroboros.workflow.approval.definition.v2.FlowDefinitionV2;
import com.ouroboros.workflow.approval.definition.v2.FlowEdgeType;
import com.ouroboros.workflow.approval.definition.v2.FlowEdgeV2;

public class V1FlowDefinitionCodec implements FlowDefinitionCodec {
  final StructuredFlowGraphValidator validator = new StructuredFlowGraphValidator();
  final V1FlowDefinitionConverter converter = new V1FlowDefinitionConverter();

  @Override
  public Try<StructuredFlowDefinition> decode(String rawJson) {
    return Json.tryToBean(FlowDefinition.class, rawJson)
        .mapTry(converter::convert)
        .mapTry(validator::validate);
  }

  @Override
  public Try<FlowDefinition> encodeV1(StructuredFlowDefinition definition) {
    return Try.of(() -> {
      validator.validate(definition);
      FlowDefinition result = new FlowDefinition();
      result.setId(definition.getId());
      result.setName(definition.getName());
      result.setCategory(definition.getCategory());
      result.setIcon(definition.getIcon());
      result.setDescription(definition.getDescription());
      result.setFormType(definition.getFormType());
      result.setFormKey(definition.getFormKey());
      result.setNotification(definition.getNotification());

      List<FlowNode> clonedNodes = cloneNodes(definition.getNodes());
      clonedNodes.forEach(node -> {
        node.setNext("");
        if (node instanceof ForkNode) {
          ForkNode forkNode = (ForkNode) node;
          forkNode.setBranches(new ArrayList<NodeBranch>());
        }
      });

      for (FlowEdgeV2 edge : definition.getEdges()) {
        FlowNode sourceNode = clonedNodes.stream()
            .filter(node -> edge.getSourceId().equals(node.getId()))
            .findFirst()
            .orElse(null);
        if (sourceNode == null) {
          continue;
        }
        if (edge.getType() == FlowEdgeType.BRANCH && sourceNode instanceof ForkNode) {
          ForkNode forkNode = (ForkNode) sourceNode;
          NodeBranch branch = new NodeBranch();
          branch.setId(edge.getId());
          branch.setName(edge.getName());
          branch.setCondition(edge.getCondition());
          branch.setFirst(edge.getTargetId());
          forkNode.getBranches().add(branch);
          continue;
        }
        sourceNode.setNext(edge.getTargetId());
      }

      clonedNodes.stream()
          .filter(ForkNode.class::isInstance)
          .map(ForkNode.class::cast)
          .forEach(forkNode -> forkNode.getBranches().sort((left, right) -> {
            Integer leftOrder = definition.getBranchEdges(forkNode.getId()).stream()
                .filter(edge -> StringUtils.equals(edge.getId(), left.getId()))
                .map(FlowEdgeV2::getOrder)
                .findFirst()
                .orElse(Integer.MAX_VALUE);
            Integer rightOrder = definition.getBranchEdges(forkNode.getId()).stream()
                .filter(edge -> StringUtils.equals(edge.getId(), right.getId()))
                .map(FlowEdgeV2::getOrder)
                .findFirst()
                .orElse(Integer.MAX_VALUE);
            return leftOrder.compareTo(rightOrder);
          }));

      result.setNodes(clonedNodes);
      return result;
    });
  }

  @Override
  public Try<FlowDefinitionV2> encodeV2(StructuredFlowDefinition definition) {
    return new V2FlowDefinitionCodec().encodeV2(definition);
  }

  private List<FlowNode> cloneNodes(List<FlowNode> nodes) {
    List<FlowNode> result = new ArrayList<FlowNode>();
    for (FlowNode node : ObjectUtils.defaultIfNull(nodes, Collections.<FlowNode>emptyList())) {
      result.add(Json.toBean(FlowNode.class, Json.toJsonString(node)));
    }
    return result;
  }
}
