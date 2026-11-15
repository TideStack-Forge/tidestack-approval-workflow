package com.ouroboros.workflow.approval.definition.nodes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.ouroboros.workflow.approval.definition.codec.CompositeFlowDefinitionCodec;
import com.ouroboros.workflow.approval.definition.graph.StructuredFlowDefinition;
import io.vavr.control.Try;

public class JoinNodeTest {
  @Test
  public void orJoinTypeCaseInsensitiveDecoding() {
    CompositeFlowDefinitionCodec codec = new CompositeFlowDefinitionCodec();
    Try<StructuredFlowDefinition> definitionTry = codec.decode(joinWithLowercaseType());

    assertTrue(definitionTry.isSuccess());
    StructuredFlowDefinition definition = definitionTry.get();
    
    JoinNode joinNode = (JoinNode) definition.getNodes().stream()
        .filter(node -> node instanceof JoinNode)
        .findFirst()
        .get();
    
    assertEquals("or", joinNode.getJoinType());
  }

  private String joinWithLowercaseType() {
    return String.join("\n",
        "{",
        "  \"formatVersion\": 2,",
        "  \"id\": \"flow-test\",",
        "  \"nodes\": [",
        "    { \"type\": \"发起节点\", \"id\": \"start\" },",
        "    { \"type\": \"审批节点\", \"id\": \"approve-a\", \"subType\": \"单签\", \"candidates\": [] },",
        "    { \"type\": \"审批节点\", \"id\": \"approve-b\", \"subType\": \"单签\", \"candidates\": [] },",
        "    { \"type\": \"JoinNode\", \"id\": \"join\", \"joinType\": \"or\" }",
        "  ],",
        "  \"edges\": [",
        "    { \"type\": \"SEQUENCE\", \"sourceId\": \"start\", \"targetId\": \"approve-a\" },",
        "    { \"type\": \"SEQUENCE\", \"sourceId\": \"start\", \"targetId\": \"approve-b\" },",
        "    { \"type\": \"SEQUENCE\", \"sourceId\": \"approve-a\", \"targetId\": \"join\" },",
        "    { \"type\": \"SEQUENCE\", \"sourceId\": \"approve-b\", \"targetId\": \"join\" }",
        "  ]",
        "}");
  }
}
