package com.ouroboros.workflow.approval.definition.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;

import org.junit.jupiter.api.Test;

import com.ouroboros.workflow.approval.definition.Candidate;
import com.ouroboros.workflow.approval.definition.codec.FlowDefinitionCodecException;
import com.ouroboros.workflow.approval.definition.candidates.UserCandidate;
import com.ouroboros.workflow.approval.definition.nodes.CcNode;
import com.ouroboros.workflow.approval.definition.nodes.EndNode;
import com.ouroboros.workflow.approval.definition.nodes.HandleNode;
import com.ouroboros.workflow.approval.definition.nodes.StartNode;
import com.ouroboros.workflow.approval.definition.v2.FlowEdgeType;
import com.ouroboros.workflow.approval.definition.v2.FlowEdgeV2;

class StructuredFlowGraphValidatorCcNodeTest {
  @Test
  void validateRejectsCcNodeWithoutCandidates() {
    StructuredFlowDefinition definition = new StructuredFlowDefinition();
    definition.setFormatVersion(2);
    definition.setId("flow-cc");
    definition.setNodes(java.util.Arrays.asList(startNode(), ccNode(Collections.<Candidate>emptyList()), endNode()));
    definition.setEdges(java.util.Arrays.asList(edge("e1", "start-a", "cc-a"), edge("e2", "cc-a", "end-a")));

    try {
      new StructuredFlowGraphValidator().validate(definition);
    } catch (FlowDefinitionCodecException ex) {
      assertTrue(ex.getMessage().contains("CcNode的candidates不能为空"));
      return;
    }
    throw new AssertionError("Expected validation failure");
  }

  @Test
  void validateAcceptsCcNodeWithCandidates() {
    StructuredFlowDefinition definition = new StructuredFlowDefinition();
    definition.setFormatVersion(2);
    definition.setId("flow-cc");
    definition.setNodes(java.util.Arrays.asList(startNode(), ccNode(Collections.<Candidate>singletonList(userCandidate())), endNode()));
    definition.setEdges(java.util.Arrays.asList(edge("e1", "start-a", "cc-a"), edge("e2", "cc-a", "end-a")));

    StructuredFlowDefinition validated = new StructuredFlowGraphValidator().validate(definition);
    assertEquals("flow-cc", validated.getId());
  }

  @Test
  void validateRejectsHandleNodeWithoutCandidates() {
    StructuredFlowDefinition definition = new StructuredFlowDefinition();
    definition.setFormatVersion(2);
    definition.setId("flow-handle");
    definition.setNodes(java.util.Arrays.asList(startNode(), handleNode(Collections.<Candidate>emptyList()), endNode()));
    definition.setEdges(java.util.Arrays.asList(edge("e1", "start-a", "handle-a"), edge("e2", "handle-a", "end-a")));

    try {
      new StructuredFlowGraphValidator().validate(definition);
    } catch (FlowDefinitionCodecException ex) {
      assertTrue(ex.getMessage().contains("HandleNode的candidates不能为空"));
      return;
    }
    throw new AssertionError("Expected validation failure");
  }

  @Test
  void validateAcceptsHandleNodeWithCandidates() {
    StructuredFlowDefinition definition = new StructuredFlowDefinition();
    definition.setFormatVersion(2);
    definition.setId("flow-handle");
    definition.setNodes(
        java.util.Arrays.asList(startNode(), handleNode(Collections.<Candidate>singletonList(userCandidate())), endNode()));
    definition.setEdges(java.util.Arrays.asList(edge("e1", "start-a", "handle-a"), edge("e2", "handle-a", "end-a")));

    StructuredFlowDefinition validated = new StructuredFlowGraphValidator().validate(definition);
    assertEquals("flow-handle", validated.getId());
  }

  private StartNode startNode() {
    StartNode node = new StartNode();
    node.setId("start-a");
    node.setName("发起节点");
    return node;
  }

  private CcNode ccNode(java.util.List<Candidate> candidates) {
    CcNode node = new CcNode();
    node.setId("cc-a");
    node.setName("抄送节点");
    node.setCandidates(candidates);
    return node;
  }

  private EndNode endNode() {
    EndNode node = new EndNode();
    node.setId("end-a");
    node.setName("结束");
    return node;
  }

  private HandleNode handleNode(java.util.List<Candidate> candidates) {
    HandleNode node = new HandleNode();
    node.setId("handle-a");
    node.setName("办理节点");
    node.setCandidates(candidates);
    return node;
  }

  private FlowEdgeV2 edge(String id, String source, String target) {
    FlowEdgeV2 edge = new FlowEdgeV2();
    edge.setId(id);
    edge.setType(FlowEdgeType.SEQUENCE);
    edge.setSourceId(source);
    edge.setTargetId(target);
    return edge;
  }

  private UserCandidate userCandidate() {
    UserCandidate candidate = new UserCandidate();
    candidate.setUsers(Collections.singletonList("user-1"));
    return candidate;
  }
}
