package com.ouroboros.workflow.approval.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.ouroboros.workflow.approval.definition.FlowNode;
import com.ouroboros.workflow.approval.definition.codec.CompositeFlowDefinitionCodec;
import com.ouroboros.workflow.approval.definition.graph.StructuredFlowDefinition;
import com.ouroboros.workflow.approval.definition.nodes.ApprovalNode;

public class StructuredFlowRuntimeNavigatorTest {
  private final CompositeFlowDefinitionCodec codec = new CompositeFlowDefinitionCodec();
  private final StructuredFlowRuntimeNavigator navigator = new StructuredFlowRuntimeNavigator();

  @Test
  void getFlowNodesReturnsOrderedStartAndApprovalNodesForStructuredDefinition() {
    StructuredFlowDefinition definition = codec.decode(v2FlowJson()).get();

    List<FlowNode> flowNodes = navigator.getFlowNodes(definition);

    assertEquals(Arrays.asList("start-a", "approve-a", "approve-c", "approve-b"), flowNodes.stream()
        .map(FlowNode::getId)
        .collect(Collectors.toList()));
  }

  @Test
  void getUserSelectCandidateApprovalNodesFollowsSelectedBranchAndContinuePath() {
    StructuredFlowDefinition definition = codec.decode(v2FlowJson()).get();

    List<ApprovalNode> approvalNodes = navigator.getUserSelectCandidateApprovalNodes(
        definition,
        edge -> "approve-a".equals(edge.getTargetId())
    );

    assertEquals(Arrays.asList("approve-a", "approve-c"), approvalNodes.stream()
        .map(ApprovalNode::getId)
        .collect(Collectors.toList()));
  }

  private String v2FlowJson() {
    return String.join("\n",
        "{",
        "  \"formatVersion\": 2,",
        "  \"id\": \"flow-a\",",
        "  \"name\": \"采购审批\",",
        "  \"category\": \"\",",
        "  \"icon\": \"shenpi\",",
        "  \"description\": \"\",",
        "  \"formType\": \"default\",",
        "  \"formKey\": \"purchase-form\",",
        "  \"nodes\": [",
        "    { \"type\": \"发起节点\", \"name\": \"发起节点\", \"id\": \"start-a\", \"permission\": [] },",
        "    { \"type\": \"审批节点\", \"name\": \"经理审批\", \"id\": \"approve-a\", \"subType\": \"单签\", \"candidates\": [{ \"type\": \"UserSelect\", \"mode\": \"single\" }], \"permission\": [], \"actions\": [\"reject\"] },",
        "    { \"type\": \"审批节点\", \"name\": \"直属审批\", \"id\": \"approve-b\", \"subType\": \"单签\", \"candidates\": [{ \"type\": \"UserSelect\", \"mode\": \"single\" }], \"permission\": [], \"actions\": [\"reject\"] },",
        "    { \"type\": \"审批节点\", \"name\": \"财务审批\", \"id\": \"approve-c\", \"subType\": \"单签\", \"candidates\": [{ \"type\": \"UserSelect\", \"mode\": \"single\" }], \"permission\": [], \"actions\": [\"reject\"] }",
        "  ],",
        "  \"edges\": [",
        "    { \"id\": \"e1\", \"type\": \"SEQUENCE\", \"sourceId\": \"start-a\", \"targetId\": \"approve-a\", \"condition\": { \"left\": { \"type\": \"field\", \"field\": \"amount\" }, \"op\": \"greater\", \"right\": 1000 }, \"order\": 1 },",
        "    { \"id\": \"e2\", \"type\": \"SEQUENCE\", \"sourceId\": \"start-a\", \"targetId\": \"approve-b\", \"condition\": { \"left\": { \"type\": \"field\", \"field\": \"amount\" }, \"op\": \"less_or_equal\", \"right\": 1000 }, \"order\": 2 },",
        "    { \"id\": \"e3\", \"type\": \"SEQUENCE\", \"sourceId\": \"approve-a\", \"targetId\": \"approve-c\" },",
        "    { \"id\": \"e4\", \"type\": \"SEQUENCE\", \"sourceId\": \"approve-b\", \"targetId\": \"approve-c\" }",
        "  ]",
        "}");
  }
}
