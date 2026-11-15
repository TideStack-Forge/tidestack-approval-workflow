package com.ouroboros.workflow.approval.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.ouroboros.workflow.approval.util.TreeGraphNode;
import com.ouroboros.workflow.approval.definition.graph.StructuredFlowDefinition;
import com.ouroboros.workflow.approval.definition.codec.CompositeFlowDefinitionCodec;

public class DefinitionUtilStructuredFlowTest {
  @Test
  public void traversePreOrderFromStructuredFlowVisitsStartBranchChildrenAndEndNode() {
    StructuredFlowDefinition definition = new CompositeFlowDefinitionCodec().decode(String.join("\n",
        "{",
        "  \"formatVersion\": 2,",
        "  \"id\": \"flow-a\",",
        "  \"name\": \"采购审批\",",
        "  \"nodes\": [",
        "    { \"id\": \"start-a\", \"type\": \"发起节点\", \"name\": \"发起节点\" },",
        "    { \"id\": \"approve-a\", \"type\": \"审批节点\", \"name\": \"经理审批\", \"subType\": \"单签\", \"candidates\": [] },",
        "    { \"id\": \"approve-b\", \"type\": \"审批节点\", \"name\": \"直属审批\", \"subType\": \"单签\", \"candidates\": [] },",
        "    { \"id\": \"end-a\", \"type\": \"结束节点\", \"name\": \"结束\", \"end\": { \"content\": \"done\", \"channel\": [\"message\"], \"candidates\": [] } }",
        "  ],",
        "  \"edges\": [",
        "    { \"id\": \"e1\", \"type\": \"SEQUENCE\", \"sourceId\": \"start-a\", \"targetId\": \"approve-a\", \"condition\": { \"left\": { \"type\": \"field\", \"field\": \"amount\" }, \"op\": \"greater\", \"right\": 1000 }, \"order\": 1 },",
        "    { \"id\": \"e2\", \"type\": \"SEQUENCE\", \"sourceId\": \"start-a\", \"targetId\": \"approve-b\", \"condition\": { \"left\": { \"type\": \"field\", \"field\": \"amount\" }, \"op\": \"less_or_equal\", \"right\": 1000 }, \"order\": 2 },",
        "    { \"id\": \"e3\", \"type\": \"SEQUENCE\", \"sourceId\": \"approve-a\", \"targetId\": \"end-a\" },",
        "    { \"id\": \"e4\", \"type\": \"SEQUENCE\", \"sourceId\": \"approve-b\", \"targetId\": \"end-a\" }",
        "  ]",
        "}")).get();

    List<TreeGraphNode> nodes = DefinitionUtil.traversePreOrder(definition);

    assertEquals("start-a", nodes.get(0).getFlowNode().getId());
    assertTrue(nodes.stream().anyMatch(node -> "approve-a".equals(node.getFlowNode().getId())));
    assertTrue(nodes.stream().anyMatch(node -> "approve-b".equals(node.getFlowNode().getId())));
    assertEquals("end-a",
        nodes.stream().filter(TreeGraphNode::isEndNode).findFirst().get().getFlowNode().getId());
  }
}
