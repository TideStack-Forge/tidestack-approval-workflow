package com.ouroboros.workflow.approval.semantics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import com.ouroboros.workflow.approval.definition.condition.Condition;
import com.ouroboros.workflow.approval.definition.v2.FlowEdgeType;
import com.ouroboros.workflow.approval.definition.v2.FlowEdgeV2;

class GatewayAndJoinSemanticsTest {

  @Test
  void splitTypeSupportsCaseInsensitiveParsingAndUppercaseRendering() {
    assertEquals(SplitType.AND, SplitType.fromString("and"));
    assertEquals(SplitType.XOR, SplitType.fromString("XoR"));
    assertEquals("OR", SplitType.OR.toString());
    assertNull(SplitType.fromString("unsupported"));
  }

  @Test
  void joinTypeKeepsDeferredAndCompatibilityParsing() {
    assertEquals(JoinType.AND, JoinType.fromString("and"));
    assertEquals(JoinType.OR, JoinType.fromString("Or"));
    assertEquals("AND", JoinType.AND.toString());
    assertNull(JoinType.fromString("xor"));
  }

  @Test
  void inferSplitTypeReturnsAndWhenAllOutgoingEdgesAreUnconditional() {
    assertEquals(SplitType.AND, GatewaySemantics.inferSplitType(Arrays.asList(
        buildEdge("edge-a", null),
        buildEdge("edge-b", null))));
  }

  @Test
  void inferSplitTypeReturnsXorWhenAllOutgoingEdgesAreConditional() {
    assertEquals(SplitType.XOR, GatewaySemantics.inferSplitType(Arrays.asList(
        buildEdge("edge-a", "${a}"),
        buildEdge("edge-b", "${b}"))));
  }

  @Test
  void inferSplitTypeReturnsOrWhenOutgoingEdgesMixConditionalAndUnconditionalPaths() {
    assertEquals(SplitType.OR, GatewaySemantics.inferSplitType(Arrays.asList(
        buildEdge("edge-a", null),
        buildEdge("edge-b", "${b}"))));
  }

  @Test
  void inferSplitTypeTreatsNullExpressionAsUnconditional() {
    assertEquals(SplitType.AND, GatewaySemantics.inferSplitType(Arrays.asList(
        buildEdgeWithNullExpression("edge-a"),
        buildEdgeWithNullExpression("edge-b"))));
  }

  @Test
  void inferSplitTypeTreatsEmptyExpressionAsUnconditional() {
    assertEquals(SplitType.AND, GatewaySemantics.inferSplitType(Arrays.asList(
        buildEdge("edge-a", ""),
        buildEdge("edge-b", ""))));
  }

  @Test
  void inferSplitTypeTreatsBlankExpressionAsUnconditional() {
    assertEquals(SplitType.AND, GatewaySemantics.inferSplitType(Arrays.asList(
        buildEdge("edge-a", "   "),
        buildEdge("edge-b", "\t\n"))));
  }

  @Test
  void inferSplitTypeHandlesMixedValidAndNullExpressions() {
    assertEquals(SplitType.OR, GatewaySemantics.inferSplitType(Arrays.asList(
        buildEdge("edge-a", "${a}"),
        buildEdgeWithNullExpression("edge-b"))));
  }

  @Test
  void normalizeJoinTypeKeepsDeferredAndCompatibilityAndDefaultsInvalidValuesToOr() {
    assertEquals(JoinType.AND, JoinSemantics.normalizeJoinType("and"));
    assertEquals(JoinType.OR, JoinSemantics.normalizeJoinType("or"));
    assertEquals(JoinType.OR, JoinSemantics.normalizeJoinType("xor"));
    assertEquals(JoinType.OR, JoinSemantics.normalizeJoinType(null));
  }

  private FlowEdgeV2 buildEdge(String id, String expression) {
    FlowEdgeV2 edge = new FlowEdgeV2();
    edge.setId(id);
    edge.setType(FlowEdgeType.SEQUENCE);
    edge.setSourceId("source");
    edge.setTargetId("target-" + id);
    if (expression != null) {
      edge.setCondition(new Condition() {
        @Override
        public String toExpression() {
          return expression;
        }
      });
    }
    return edge;
  }

  private FlowEdgeV2 buildEdgeWithNullExpression(String id) {
    FlowEdgeV2 edge = new FlowEdgeV2();
    edge.setId(id);
    edge.setType(FlowEdgeType.SEQUENCE);
    edge.setSourceId("source");
    edge.setTargetId("target-" + id);
    edge.setCondition(new Condition() {
      @Override
      public String toExpression() {
        return null;
      }
    });
    return edge;
  }
}
