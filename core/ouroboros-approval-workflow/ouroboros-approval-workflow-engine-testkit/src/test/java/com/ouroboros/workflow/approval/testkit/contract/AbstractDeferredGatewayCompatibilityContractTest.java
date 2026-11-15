package com.ouroboros.workflow.approval.testkit.contract;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.ouroboros.workflow.approval.semantics.SplitType;

/**
 * Deferred compatibility regression for engine-specific gateway inference details.
 *
 * <p>These assertions intentionally sit outside the stable V2 product contract: they pin how the
 * built-in engines currently transpile legacy fork shapes and internal split/join gateway types so
 * drift remains visible without promoting those details to product semantics.
 */
public abstract class AbstractDeferredGatewayCompatibilityContractTest {
  @Test
  public void mixedConditionalAndDefaultBranchesTranspileAsInclusiveSplitAndJoin() {
    assertEquals(
        SplitType.OR,
        inferCompatibilitySplitType(v2MixedConditionalAndDefaultBranchesFlow(), "start-a"),
        "兼容 split helper 应继续把部分有条件边推导为 OR");

    Object model = transpile(v2MixedConditionalAndDefaultBranchesFlow());
    Object process = getMainProcess(model);

    assertInclusiveGateway(process, "join-a", "OR join应该是InclusiveGateway");
  }

  @Test
  public void legacyInclusiveForkTranspilesAsInclusiveSplitAndJoin() {
    assertEquals(
        SplitType.OR,
        inferCompatibilitySplitType(v1InclusiveForkFlow(), "start-a"),
        "V1 inclusive fork 兼容 split helper 应继续推导为 OR");

    Object model = transpile(v1InclusiveForkFlow());
    Object process = getMainProcess(model);

    assertInclusiveGateway(process, "fork-a__join", "V1 inclusive fork的join应该是InclusiveGateway");
  }

  @Test
  @Disabled("JoinNode(AND) remains deferred engine compatibility and is rejected by the Stable V2 definition boundary.")
  public void lowercaseAndJoinTypeStillTranspilesAsParallelGateway() {
    assertEquals(
        SplitType.AND,
        inferCompatibilitySplitType(flowWithLowercaseAndJoinType(), "start"),
        "兼容 split helper 应继续把全无条件边推导为 AND");

    Object model = transpile(flowWithLowercaseAndJoinType());
    Object process = getMainProcess(model);

    assertParallelGateway(process, "join", "joinType='and'（小写）应该是ParallelGateway");
  }

  @Test
  public void allConditionalBranchesTranspileAsExclusiveGateway() {
    assertEquals(
        SplitType.XOR,
        inferCompatibilitySplitType(v2AllConditionalBranchesFlow(), "start-a"),
        "兼容 split helper 应继续把全有条件边推导为 XOR");
  }

  @Test
  public void allDefaultBranchesTranspileAsParallelGateway() {
    assertEquals(
        SplitType.AND,
        inferCompatibilitySplitType(v2AllDefaultBranchesFlow(), "start"),
        "兼容 split helper 应继续把全无条件边推导为 AND");
  }

  protected abstract Object transpile(String json);

  protected abstract SplitType inferCompatibilitySplitType(String json, String sourceNodeId);

  protected abstract Object getMainProcess(Object model);

  protected abstract Object findGatewayAfterNode(Object process, String nodeId);

  protected abstract Object getFlowElement(Object process, String elementId);

  protected abstract void assertIsInclusiveGateway(Object gateway, String message);

  protected abstract void assertIsExclusiveGateway(Object gateway, String message);

  protected abstract void assertIsParallelGateway(Object gateway, String message);

  private void assertInclusiveGateway(Object process, String elementId, String message) {
    Object gateway = requireFlowElement(process, elementId, "应该有join网关");
    assertIsInclusiveGateway(gateway, message);
  }

  private void assertParallelGateway(Object process, String elementId, String message) {
    Object gateway = requireFlowElement(process, elementId, "应该有join网关");
    assertIsParallelGateway(gateway, message);
  }

  private Object requireFlowElement(Object process, String elementId, String notFoundMessage) {
    Object element = getFlowElement(process, elementId);
    assertNotNull(element, notFoundMessage);
    return element;
  }

  private String v2MixedConditionalAndDefaultBranchesFlow() {
    return String.join("\n",
        "{",
        "  \"formatVersion\": 2,",
        "  \"id\": \"flow-mixed\",",
        "  \"name\": \"混合分支\",",
        "  \"nodes\": [",
        "    { \"type\": \"发起节点\", \"id\": \"start-a\" },",
        "    { \"type\": \"审批节点\", \"id\": \"approve-a\", \"subType\": \"单签\", \"candidates\": [] },",
        "    { \"type\": \"审批节点\", \"id\": \"approve-b\", \"subType\": \"单签\", \"candidates\": [] },",
        "    { \"type\": \"审批节点\", \"id\": \"approve-c\", \"subType\": \"单签\", \"candidates\": [] },",
        "    { \"type\": \"JoinNode\", \"id\": \"join-a\", \"joinType\": \"OR\" },",
        "    { \"type\": \"结束节点\", \"id\": \"end-a\" }",
        "  ],",
        "  \"edges\": [",
        "    { \"type\": \"SEQUENCE\", \"sourceId\": \"start-a\", \"targetId\": \"approve-a\", \"condition\": { \"left\": { \"type\": \"field\", \"field\": \"amount\" }, \"op\": \"greater\", \"right\": 5000 } },",
        "    { \"type\": \"SEQUENCE\", \"sourceId\": \"start-a\", \"targetId\": \"approve-b\", \"condition\": { \"left\": { \"type\": \"field\", \"field\": \"amount\" }, \"op\": \"greater\", \"right\": 1000 } },",
        "    { \"type\": \"SEQUENCE\", \"sourceId\": \"start-a\", \"targetId\": \"approve-c\" },",
        "    { \"type\": \"SEQUENCE\", \"sourceId\": \"approve-a\", \"targetId\": \"join-a\" },",
        "    { \"type\": \"SEQUENCE\", \"sourceId\": \"approve-b\", \"targetId\": \"join-a\" },",
        "    { \"type\": \"SEQUENCE\", \"sourceId\": \"approve-c\", \"targetId\": \"join-a\" },",
        "    { \"type\": \"SEQUENCE\", \"sourceId\": \"join-a\", \"targetId\": \"end-a\" }",
        "  ]",
        "}");
  }

  private String v1InclusiveForkFlow() {
    return String.join("\n",
        "{",
        "  \"id\": \"flow-inclusive\",",
        "  \"name\": \"并发审批\",",
        "  \"nodes\": [",
        "    { \"type\": \"发起节点\", \"id\": \"start-a\", \"next\": \"fork-a\" },",
        "    { \"type\": \"分流节点\", \"id\": \"fork-a\", \"logic\": \"inclusive\", \"branches\": [",
        "      { \"id\": \"branch-1\", \"condition\": { \"left\": { \"type\": \"field\", \"field\": \"amount\" }, \"op\": \"greater\", \"right\": 5000 }, \"first\": \"approve-a\" },",
        "      { \"id\": \"branch-2\", \"condition\": { \"left\": { \"type\": \"field\", \"field\": \"amount\" }, \"op\": \"greater\", \"right\": 1000 }, \"first\": \"approve-b\" },",
        "      { \"id\": \"branch-3\", \"first\": \"approve-c\" }",
        "    ], \"next\": \"end-a\" },",
        "    { \"type\": \"审批节点\", \"id\": \"approve-a\", \"subType\": \"单签\", \"candidates\": [] },",
        "    { \"type\": \"审批节点\", \"id\": \"approve-b\", \"subType\": \"单签\", \"candidates\": [] },",
        "    { \"type\": \"审批节点\", \"id\": \"approve-c\", \"subType\": \"单签\", \"candidates\": [] },",
        "    { \"type\": \"结束节点\", \"id\": \"end-a\" }",
        "  ]",
        "}");
  }

  private String flowWithLowercaseAndJoinType() {
    return String.join("\n",
        "{",
        "  \"formatVersion\": 2,",
        "  \"id\": \"flow-test\",",
        "  \"nodes\": [",
        "    { \"type\": \"发起节点\", \"id\": \"start\" },",
        "    { \"type\": \"审批节点\", \"id\": \"approve-a\", \"subType\": \"单签\", \"candidates\": [] },",
        "    { \"type\": \"审批节点\", \"id\": \"approve-b\", \"subType\": \"单签\", \"candidates\": [] },",
        "    { \"type\": \"JoinNode\", \"id\": \"join\", \"joinType\": \"and\" }",
        "  ],",
        "  \"edges\": [",
        "    { \"type\": \"SEQUENCE\", \"sourceId\": \"start\", \"targetId\": \"approve-a\" },",
        "    { \"type\": \"SEQUENCE\", \"sourceId\": \"start\", \"targetId\": \"approve-b\" },",
        "    { \"type\": \"SEQUENCE\", \"sourceId\": \"approve-a\", \"targetId\": \"join\" },",
        "    { \"type\": \"SEQUENCE\", \"sourceId\": \"approve-b\", \"targetId\": \"join\" }",
        "  ]",
        "}");
  }

  private String v2AllConditionalBranchesFlow() {
    return String.join("\n",
        "{",
        "  \"formatVersion\": 2,",
        "  \"id\": \"flow-xor\",",
        "  \"name\": \"互斥分支\",",
        "  \"nodes\": [",
        "    { \"type\": \"发起节点\", \"id\": \"start-a\" },",
        "    { \"type\": \"审批节点\", \"id\": \"approve-a\", \"subType\": \"单签\", \"candidates\": [] },",
        "    { \"type\": \"审批节点\", \"id\": \"approve-b\", \"subType\": \"单签\", \"candidates\": [] },",
        "    { \"type\": \"结束节点\", \"id\": \"end-a\" }",
        "  ],",
        "  \"edges\": [",
        "    { \"type\": \"SEQUENCE\", \"sourceId\": \"start-a\", \"targetId\": \"approve-a\", \"condition\": { \"left\": { \"type\": \"field\", \"field\": \"amount\" }, \"op\": \"greater\", \"right\": 1000 } },",
        "    { \"type\": \"SEQUENCE\", \"sourceId\": \"start-a\", \"targetId\": \"approve-b\", \"condition\": { \"left\": { \"type\": \"field\", \"field\": \"amount\" }, \"op\": \"less_or_equal\", \"right\": 1000 } },",
        "    { \"type\": \"SEQUENCE\", \"sourceId\": \"approve-a\", \"targetId\": \"end-a\" },",
        "    { \"type\": \"SEQUENCE\", \"sourceId\": \"approve-b\", \"targetId\": \"end-a\" }",
        "  ]",
        "}");
  }

  private String v2AllDefaultBranchesFlow() {
    return String.join("\n",
        "{",
        "  \"formatVersion\": 2,",
        "  \"id\": \"flow-parallel\",",
        "  \"name\": \"并行分支\",",
        "  \"nodes\": [",
        "    { \"type\": \"发起节点\", \"id\": \"start\" },",
        "    { \"type\": \"审批节点\", \"id\": \"approve-a\", \"subType\": \"单签\", \"candidates\": [] },",
        "    { \"type\": \"审批节点\", \"id\": \"approve-b\", \"subType\": \"单签\", \"candidates\": [] },",
        "    { \"type\": \"JoinNode\", \"id\": \"join\", \"joinType\": \"OR\" },",
        "    { \"type\": \"结束节点\", \"id\": \"end\" }",
        "  ],",
        "  \"edges\": [",
        "    { \"type\": \"SEQUENCE\", \"sourceId\": \"start\", \"targetId\": \"approve-a\" },",
        "    { \"type\": \"SEQUENCE\", \"sourceId\": \"start\", \"targetId\": \"approve-b\" },",
        "    { \"type\": \"SEQUENCE\", \"sourceId\": \"approve-a\", \"targetId\": \"join\" },",
        "    { \"type\": \"SEQUENCE\", \"sourceId\": \"approve-b\", \"targetId\": \"join\" },",
        "    { \"type\": \"SEQUENCE\", \"sourceId\": \"join\", \"targetId\": \"end\" }",
        "  ]",
        "}");
  }
}
