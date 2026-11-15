package com.ouroboros.workflow.approval.definition.codec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ouroboros.util.Json;
import org.junit.jupiter.api.Test;

import com.ouroboros.workflow.approval.definition.FlowDefinition;
import com.ouroboros.workflow.approval.definition.FlowNode;
import com.ouroboros.workflow.approval.definition.graph.StructuredFlowDefinition;
import com.ouroboros.workflow.approval.definition.nodes.BuiltInFlowNodeTypes;
import com.ouroboros.workflow.approval.definition.nodes.CcNode;
import com.ouroboros.workflow.approval.definition.nodes.EndNode;
import com.ouroboros.workflow.approval.definition.nodes.HandleNode;
import com.ouroboros.workflow.approval.definition.nodes.SingleApprovalNode;
import com.ouroboros.workflow.approval.definition.nodes.StartNode;
import com.ouroboros.workflow.approval.definition.candidates.FormFieldCandidate;
import com.ouroboros.workflow.approval.definition.candidates.UserCandidate;
import com.ouroboros.workflow.approval.definition.v2.FlowDefinitionV2;
import io.vavr.control.Try;

public class FlowDefinitionCodecTest {
  @Test
  public void decodeV2BuildsStructuredEdgesWithSequenceOnly() {
    CompositeFlowDefinitionCodec codec = new CompositeFlowDefinitionCodec();
    Try<StructuredFlowDefinition> definitionTry = codec.decode(v2XorFlow());

    assertTrue(definitionTry.isSuccess());
    StructuredFlowDefinition definition = definitionTry.get();
    assertEquals(Integer.valueOf(2), definition.getFormatVersion());
    assertEquals("flow-a", definition.getId());
    assertEquals(4, definition.getNodes().size());
    assertEquals(4, definition.getEdges().size());
    assertNotNull(definition.getOutgoingEdges("start-a").get(0).getCondition());
  }

  @Test
  public void decodeV2TechnicalNodeTypesUsesStrongTypesAndEncodesCanonicalTypes() {
    CompositeFlowDefinitionCodec codec = new CompositeFlowDefinitionCodec();
    Try<StructuredFlowDefinition> definitionTry = codec.decode(v2TechnicalTypeFlow());

    assertTrue(definitionTry.isSuccess());
    StructuredFlowDefinition definition = definitionTry.get();
    assertInstanceOf(StartNode.class, definition.getNodes().get(0));
    assertInstanceOf(SingleApprovalNode.class, definition.getNodes().get(1));
    assertInstanceOf(EndNode.class, definition.getNodes().get(2));

    Try<FlowDefinitionV2> encoded = codec.encodeV2(definition);

    assertTrue(encoded.isSuccess());
    assertEquals(BuiltInFlowNodeTypes.START, encoded.get().getNodes().get(0).getType());
    assertEquals(BuiltInFlowNodeTypes.APPROVAL, encoded.get().getNodes().get(1).getType());
    assertEquals(BuiltInFlowNodeTypes.END, encoded.get().getNodes().get(2).getType());
  }

  @Test
  public void decodeV2EncodeV2PreservesEditorLayout() {
    CompositeFlowDefinitionCodec codec = new CompositeFlowDefinitionCodec();
    Try<StructuredFlowDefinition> definitionTry = codec.decode(String.join("\n",
        "{",
        "  \"formatVersion\": 2,",
        "  \"id\": \"flow-layout\",",
        "  \"name\": \"布局流程\",",
        "  \"editorLayout\": {",
        "    \"nodePositions\": {",
        "      \"start-a\": { \"x\": 120, \"y\": 80 },",
        "      \"end-a\": { \"x\": 420, \"y\": 80 }",
        "    }",
        "  },",
        "  \"nodes\": [",
        "    { \"type\": \"StartNode\", \"id\": \"start-a\", \"name\": \"发起节点\", \"permission\": [] },",
        "    { \"type\": \"EndNode\", \"id\": \"end-a\", \"name\": \"结束\" }",
        "  ],",
        "  \"edges\": [",
        "    { \"id\": \"e1\", \"type\": \"SEQUENCE\", \"sourceId\": \"start-a\", \"targetId\": \"end-a\" }",
        "  ]",
        "}"));

    assertTrue(definitionTry.isSuccess());
    assertEquals(120, definitionTry.get().getEditorLayout().path("nodePositions").path("start-a").path("x").asInt());

    Try<FlowDefinitionV2> encoded = codec.encodeV2(definitionTry.get());

    assertTrue(encoded.isSuccess());
    assertEquals(420, encoded.get().getEditorLayout().path("nodePositions").path("end-a").path("x").asInt());
    assertEquals(
        encoded.get().getEditorLayout(),
        Json.toBean(com.fasterxml.jackson.databind.JsonNode.class, Json.toJsonString(encoded.get())).path("editorLayout"));
  }

  @Test
  public void decodeV2CcNodeUsesStrongTypeAndPreservesCandidates() {
    CompositeFlowDefinitionCodec codec = new CompositeFlowDefinitionCodec();
    Try<StructuredFlowDefinition> definitionTry = codec.decode(String.join("\n",
        "{",
        "  \"formatVersion\": 2,",
        "  \"id\": \"flow-cc\",",
        "  \"name\": \"抄送流程\",",
        "  \"nodes\": [",
        "    { \"type\": \"StartNode\", \"id\": \"start-a\", \"name\": \"发起节点\", \"permission\": [] },",
        "    { \"type\": \"CcNode\", \"id\": \"cc-a\", \"name\": \"抄送节点\", \"candidates\": [{ \"type\": \"User\", \"users\": [\"u-cc-1\"] }] },",
        "    { \"type\": \"EndNode\", \"id\": \"end-a\", \"name\": \"结束\" }",
        "  ],",
        "  \"edges\": [",
        "    { \"id\": \"e1\", \"type\": \"SEQUENCE\", \"sourceId\": \"start-a\", \"targetId\": \"cc-a\" },",
        "    { \"id\": \"e2\", \"type\": \"SEQUENCE\", \"sourceId\": \"cc-a\", \"targetId\": \"end-a\" }",
        "  ]",
        "}"));

    assertTrue(definitionTry.isSuccess());
    StructuredFlowDefinition definition = definitionTry.get();
    assertInstanceOf(CcNode.class, definition.getNodes().get(1));
    CcNode ccNode = (CcNode) definition.getNodes().get(1);
    assertEquals(BuiltInFlowNodeTypes.CC, ccNode.getType());
    assertEquals(1, ccNode.getCandidates().size());
    assertInstanceOf(UserCandidate.class, ccNode.getCandidates().get(0));
    UserCandidate candidate = (UserCandidate) ccNode.getCandidates().get(0);
    assertEquals("u-cc-1", candidate.getUsers().get(0));

    Try<FlowDefinitionV2> encoded = codec.encodeV2(definition);
    assertTrue(encoded.isSuccess());
    assertEquals(BuiltInFlowNodeTypes.CC, encoded.get().getNodes().get(1).getType());
    assertInstanceOf(UserCandidate.class, encoded.get().getNodes().get(1).getCandidates().get(0));
    UserCandidate encodedCandidate =
        (UserCandidate) encoded.get().getNodes().get(1).getCandidates().get(0);
    assertEquals("u-cc-1", encodedCandidate.getUsers().get(0));
  }

  @Test
  public void decodeV2HandleNodeUsesStrongTypeAndPreservesCandidatesAndPermission() {
    CompositeFlowDefinitionCodec codec = new CompositeFlowDefinitionCodec();
    Try<StructuredFlowDefinition> definitionTry = codec.decode(String.join("\n",
        "{",
        "  \"formatVersion\": 2,",
        "  \"id\": \"flow-handle\",",
        "  \"name\": \"办理流程\",",
        "  \"nodes\": [",
        "    { \"type\": \"StartNode\", \"id\": \"start-a\", \"name\": \"发起节点\", \"permission\": [] },",
        "    { \"type\": \"HandleNode\", \"id\": \"handle-a\", \"name\": \"办理节点\", \"candidates\": [{ \"type\": \"User\", \"users\": [\"u-handle-1\"] }], \"permission\": [{ \"field\": \"amount\", \"permission\": \"EDITABLE\" }] },",
        "    { \"type\": \"EndNode\", \"id\": \"end-a\", \"name\": \"结束\" }",
        "  ],",
        "  \"edges\": [",
        "    { \"id\": \"e1\", \"type\": \"SEQUENCE\", \"sourceId\": \"start-a\", \"targetId\": \"handle-a\" },",
        "    { \"id\": \"e2\", \"type\": \"SEQUENCE\", \"sourceId\": \"handle-a\", \"targetId\": \"end-a\" }",
        "  ]",
        "}"));

    assertTrue(definitionTry.isSuccess());
    StructuredFlowDefinition definition = definitionTry.get();
    assertInstanceOf(HandleNode.class, definition.getNodes().get(1));
    HandleNode handleNode = (HandleNode) definition.getNodes().get(1);
    assertEquals(BuiltInFlowNodeTypes.HANDLE, handleNode.getType());
    assertEquals(1, handleNode.getCandidates().size());
    assertEquals(1, handleNode.getPermission().size());
    assertInstanceOf(UserCandidate.class, handleNode.getCandidates().get(0));
    UserCandidate candidate = (UserCandidate) handleNode.getCandidates().get(0);
    assertEquals("u-handle-1", candidate.getUsers().get(0));

    Try<FlowDefinitionV2> encoded = codec.encodeV2(definition);
    assertTrue(encoded.isSuccess());
    assertEquals(BuiltInFlowNodeTypes.HANDLE, encoded.get().getNodes().get(1).getType());
    assertInstanceOf(UserCandidate.class, encoded.get().getNodes().get(1).getCandidates().get(0));
    UserCandidate encodedCandidate =
        (UserCandidate) encoded.get().getNodes().get(1).getCandidates().get(0);
    assertEquals("u-handle-1", encodedCandidate.getUsers().get(0));
    assertEquals(1, encoded.get().getNodes().get(1).getPermission().size());
  }

  @Test
  public void decodeV2PreservesFormFieldCandidate() {
    CompositeFlowDefinitionCodec codec = new CompositeFlowDefinitionCodec();
    Try<StructuredFlowDefinition> definitionTry = codec.decode(String.join("\n",
        "{",
        "  \"formatVersion\": 2,",
        "  \"id\": \"flow-form-field\",",
        "  \"name\": \"表单字段审批\",",
        "  \"nodes\": [",
        "    { \"type\": \"StartNode\", \"id\": \"start-a\", \"name\": \"发起节点\", \"permission\": [] },",
        "    { \"type\": \"ApprovalNode\", \"id\": \"approve-a\", \"name\": \"经理审批\", \"subType\": \"单签\", \"candidates\": [{ \"type\": \"FormField\", \"field\": \"managerId\" }] },",
        "    { \"type\": \"EndNode\", \"id\": \"end-a\", \"name\": \"结束\" }",
        "  ],",
        "  \"edges\": [",
        "    { \"id\": \"e1\", \"type\": \"SEQUENCE\", \"sourceId\": \"start-a\", \"targetId\": \"approve-a\" },",
        "    { \"id\": \"e2\", \"type\": \"SEQUENCE\", \"sourceId\": \"approve-a\", \"targetId\": \"end-a\" }",
        "  ]",
        "}"));

    assertTrue(definitionTry.isSuccess());
    StructuredFlowDefinition definition = definitionTry.get();
    assertInstanceOf(SingleApprovalNode.class, definition.getNodes().get(1));
    SingleApprovalNode approvalNode = (SingleApprovalNode) definition.getNodes().get(1);
    assertEquals(1, approvalNode.getCandidates().size());
    assertInstanceOf(FormFieldCandidate.class, approvalNode.getCandidates().get(0));
    FormFieldCandidate candidate = (FormFieldCandidate) approvalNode.getCandidates().get(0);
    assertEquals("FormField", candidate.getType());
    assertEquals("managerId", candidate.getField());

    Try<FlowDefinitionV2> encoded = codec.encodeV2(definition);
    assertTrue(encoded.isSuccess());
    assertInstanceOf(FormFieldCandidate.class, encoded.get().getNodes().get(1).getCandidates().get(0));
    FormFieldCandidate encodedCandidate =
        (FormFieldCandidate) encoded.get().getNodes().get(1).getCandidates().get(0);
    assertEquals("managerId", encodedCandidate.getField());
  }

  @Test
  public void decodeV2PreservesNotificationAndNodeNotifyContract() {
    CompositeFlowDefinitionCodec codec = new CompositeFlowDefinitionCodec();
    Try<StructuredFlowDefinition> definitionTry = codec.decode(String.join("\n",
        "{",
        "  \"formatVersion\": 2,",
        "  \"id\": \"flow-notify\",",
        "  \"name\": \"通知流程\",",
        "  \"notification\": {",
        "    \"taskCreatedTemplate\": \"tpl-task-created\",",
        "    \"taskTimeoutRemindTemplate\": \"tpl-task-timeout\",",
        "    \"instanceCompletedTemplate\": \"tpl-instance-completed\",",
        "    \"instanceRejectedTemplate\": \"tpl-instance-rejected\"",
        "  },",
        "  \"nodes\": [",
        "    { \"type\": \"StartNode\", \"id\": \"start-a\", \"name\": \"发起节点\", \"permission\": [] },",
        "    { \"type\": \"ApprovalNode\", \"id\": \"approve-a\", \"name\": \"经理审批\", \"subType\": \"单签\", \"candidates\": [], \"notify\": false },",
        "    { \"type\": \"EndNode\", \"id\": \"end-a\", \"name\": \"结束\" }",
        "  ],",
        "  \"edges\": [",
        "    { \"id\": \"e1\", \"type\": \"SEQUENCE\", \"sourceId\": \"start-a\", \"targetId\": \"approve-a\" },",
        "    { \"id\": \"e2\", \"type\": \"SEQUENCE\", \"sourceId\": \"approve-a\", \"targetId\": \"end-a\" }",
        "  ]",
        "}"));

    assertTrue(definitionTry.isSuccess());
    StructuredFlowDefinition definition = definitionTry.get();
    assertEquals("tpl-task-created", definition.getNotification().getTaskCreatedTemplate());
    assertEquals("tpl-task-timeout", definition.getNotification().getTaskTimeoutRemindTemplate());
    assertEquals("tpl-instance-completed", definition.getNotification().getInstanceCompletedTemplate());
    assertEquals("tpl-instance-rejected", definition.getNotification().getInstanceRejectedTemplate());
    assertEquals(Boolean.FALSE, definition.getNodes().get(1).getNotify());
    assertFalse(definition.getNodes().get(1).isNotifyEnabled());

    Try<FlowDefinitionV2> encoded = codec.encodeV2(definition);

    assertTrue(encoded.isSuccess());
    assertEquals("tpl-task-created", encoded.get().getNotification().getTaskCreatedTemplate());
    assertEquals(Boolean.FALSE, encoded.get().getNodes().get(1).getNotify());
    String encodedJson = Json.toJsonString(encoded.get());
    assertTrue(encodedJson.contains("\"notify\":false"));
    assertFalse(encodedJson.contains("notifyEnabled"));
  }

  @Test
  public void decodeV2RejectsHandleNodeWithoutCandidates() {
    CompositeFlowDefinitionCodec codec = new CompositeFlowDefinitionCodec();
    Try<StructuredFlowDefinition> definitionTry = codec.decode(String.join("\n",
        "{",
        "  \"formatVersion\": 2,",
        "  \"id\": \"flow-handle-invalid\",",
        "  \"name\": \"办理流程\",",
        "  \"nodes\": [",
        "    { \"type\": \"StartNode\", \"id\": \"start-a\", \"name\": \"发起节点\" },",
        "    { \"type\": \"HandleNode\", \"id\": \"handle-a\", \"name\": \"办理节点\", \"candidates\": [] },",
        "    { \"type\": \"EndNode\", \"id\": \"end-a\", \"name\": \"结束\" }",
        "  ],",
        "  \"edges\": [",
        "    { \"id\": \"e1\", \"type\": \"SEQUENCE\", \"sourceId\": \"start-a\", \"targetId\": \"handle-a\" },",
        "    { \"id\": \"e2\", \"type\": \"SEQUENCE\", \"sourceId\": \"handle-a\", \"targetId\": \"end-a\" }",
        "  ]",
        "}"));

    assertTrue(definitionTry.isFailure());
  }

  @Test
  public void decodeRejectsOutgoingEdgeFromEndNodeInV2() {
    CompositeFlowDefinitionCodec codec = new CompositeFlowDefinitionCodec();
    Try<StructuredFlowDefinition> definitionTry = codec.decode(String.join("\n",
        "{",
        "  \"formatVersion\": 2,",
        "  \"id\": \"flow-end-outgoing\",",
        "  \"name\": \"结束节点出边测试\",",
        "  \"nodes\": [",
        "    { \"type\": \"StartNode\", \"id\": \"start-a\" },",
        "    { \"type\": \"EndNode\", \"id\": \"end-a\" },",
        "    { \"type\": \"ApprovalNode\", \"id\": \"approve-b\", \"subType\": \"单签\", \"candidates\": [] }",
        "  ],",
        "  \"edges\": [",
        "    { \"id\": \"e1\", \"type\": \"SEQUENCE\", \"sourceId\": \"start-a\", \"targetId\": \"end-a\" },",
        "    { \"id\": \"e2\", \"type\": \"SEQUENCE\", \"sourceId\": \"end-a\", \"targetId\": \"approve-b\" }",
        "  ]",
        "}"));

    assertTrue(definitionTry.isFailure());
  }

  @Test
  public void decodeRejectsForkNodeInV2() {
    CompositeFlowDefinitionCodec codec = new CompositeFlowDefinitionCodec();
    Try<StructuredFlowDefinition> definitionTry = codec.decode(String.join("\n",
        "{",
        "  \"formatVersion\": 2,",
        "  \"id\": \"flow-a\",",
        "  \"name\": \"采购审批\",",
        "  \"nodes\": [",
        "    { \"id\": \"start-a\", \"type\": \"发起节点\", \"name\": \"发起节点\" },",
        "    { \"id\": \"fork-a\", \"type\": \"分流节点\", \"name\": \"金额判断\", \"logic\": \"exclusive\" },",
        "    { \"id\": \"approve-a\", \"type\": \"审批节点\", \"name\": \"经理审批\", \"subType\": \"单签\", \"candidates\": [] }",
        "  ],",
        "  \"edges\": [",
        "    { \"id\": \"e1\", \"type\": \"SEQUENCE\", \"sourceId\": \"start-a\", \"targetId\": \"fork-a\" },",
        "    { \"id\": \"e2\", \"type\": \"SEQUENCE\", \"sourceId\": \"fork-a\", \"targetId\": \"approve-a\" }",
        "  ]",
        "}"));

    assertTrue(definitionTry.isFailure());
  }

  @Test
  public void decodeRejectsBranchEdgeInStableV2() {
    CompositeFlowDefinitionCodec codec = new CompositeFlowDefinitionCodec();
    Try<StructuredFlowDefinition> definitionTry = codec.decode(v2FlowWithEdgeType("BRANCH"));

    assertTrue(definitionTry.isFailure());
  }

  @Test
  public void decodeRejectsContinueEdgeInStableV2() {
    CompositeFlowDefinitionCodec codec = new CompositeFlowDefinitionCodec();
    Try<StructuredFlowDefinition> definitionTry = codec.decode(v2FlowWithEdgeType("CONTINUE"));

    assertTrue(definitionTry.isFailure());
  }

  @Test
  public void decodeRejectsAndJoinInStableV2() {
    CompositeFlowDefinitionCodec codec = new CompositeFlowDefinitionCodec();
    Try<StructuredFlowDefinition> definitionTry = codec.decode(v2JoinFlow("AND"));

    assertTrue(definitionTry.isFailure());
  }

  @Test
  public void decodeAcceptsOrJoinInStableV2() {
    CompositeFlowDefinitionCodec codec = new CompositeFlowDefinitionCodec();
    Try<StructuredFlowDefinition> definitionTry = codec.decode(v2JoinFlow("OR"));

    assertTrue(definitionTry.isSuccess());
  }

  @Test
  public void decodeAcceptsNormalNodeWithMultipleOutgoingEdges() {
    CompositeFlowDefinitionCodec codec = new CompositeFlowDefinitionCodec();
    Try<StructuredFlowDefinition> definitionTry = codec.decode(v2XorFlow());

    assertTrue(definitionTry.isSuccess());
    assertEquals(2, definitionTry.get().getOutgoingEdges("start-a").size());
  }

  @Test
  public void decodeV1AndDecodeV2ProduceEquivalentStructuredGraphs() {
    CompositeFlowDefinitionCodec codec = new CompositeFlowDefinitionCodec();
    StructuredFlowDefinition fromV1 = codec.decode(v1ForkFlow()).get();
    StructuredFlowDefinition fromV2 = codec.decode(v2XorFlow()).get();

    assertEquivalent(fromV1, fromV2);
  }

  @Test
  public void missingFormatVersionFallsBackToV1() {
    CompositeFlowDefinitionCodec codec = new CompositeFlowDefinitionCodec();
    Try<StructuredFlowDefinition> definitionTry = codec.decode(v1ForkFlow());

    assertTrue(definitionTry.isSuccess());
    assertEquals(Integer.valueOf(2), definitionTry.get().getFormatVersion());
  }

  @Test
  public void encodeV1ConvertsV2XorFlowToLegacyDefinition() {
    CompositeFlowDefinitionCodec codec = new CompositeFlowDefinitionCodec();
    StructuredFlowDefinition structured = codec.decode(v2XorFlow()).get();

    Try<FlowDefinition> encoded = codec.encodeV1(structured);

    assertTrue(encoded.isSuccess());
    FlowDefinition flowDefinition = encoded.get();
    assertEquals(4, flowDefinition.getNodes().size());
    FlowNode startNode = flowDefinition.getNodes().stream()
        .filter(node -> "start-a".equals(node.getId()))
        .findFirst()
        .get();
    assertTrue("approve-a".equals(startNode.getNext()) || "approve-b".equals(startNode.getNext()));
  }

  private void assertEquivalent(StructuredFlowDefinition left, StructuredFlowDefinition right) {
    assertEquals(left.getId(), right.getId());
    assertEquals(left.getNodes().size(), right.getNodes().size());
    assertEquals(left.getEdges().size(), right.getEdges().size());
    assertEquals(left.getOutgoingEdges("start-a").size(), right.getOutgoingEdges("start-a").size());
  }

  private String v1ForkFlow() {
    return String.join("\n",
        "{",
        "  \"id\": \"flow-a\",",
        "  \"name\": \"采购审批\",",
        "  \"category\": \"\",",
        "  \"icon\": \"shenpi\",",
        "  \"description\": \"\",",
        "  \"formType\": \"default\",",
        "  \"formKey\": \"purchase-form\",",
        "  \"nodes\": [",
        "    { \"type\": \"发起节点\", \"name\": \"发起节点\", \"id\": \"start-a\", \"permission\": [], \"next\": \"fork-a\" },",
        "    { \"type\": \"分流节点\", \"name\": \"金额判断\", \"id\": \"fork-a\", \"logic\": \"exclusive\", \"branches\": [{ \"id\": \"branch-1\", \"name\": \"金额大于1000\", \"condition\": { \"left\": { \"type\": \"field\", \"field\": \"amount\" }, \"op\": \"greater\", \"right\": 1000 }, \"first\": \"approve-a\" }, { \"id\": \"branch-2\", \"name\": \"默认\", \"condition\": { \"left\": { \"type\": \"field\", \"field\": \"amount\" }, \"op\": \"less_or_equal\", \"right\": 1000 }, \"first\": \"approve-b\" }], \"next\": \"end-a\" },",
        "    { \"type\": \"审批节点\", \"name\": \"经理审批\", \"id\": \"approve-a\", \"subType\": \"单签\", \"candidates\": [], \"permission\": [], \"actions\": [\"reject\"], \"next\": \"\" },",
        "    { \"type\": \"审批节点\", \"name\": \"直属审批\", \"id\": \"approve-b\", \"subType\": \"单签\", \"candidates\": [], \"permission\": [], \"actions\": [\"reject\"], \"next\": \"\" },",
        "    { \"type\": \"结束节点\", \"name\": \"结束\", \"id\": \"end-a\", \"next\": \"\" }",
        "  ]",
        "}");
  }

  private String v2TechnicalTypeFlow() {
    return String.join("\n",
        "{",
        "  \"formatVersion\": 2,",
        "  \"id\": \"flow-technical-types\",",
        "  \"name\": \"技术类型测试\",",
        "  \"nodes\": [",
        "    { \"type\": \"StartNode\", \"id\": \"start-a\", \"name\": \"发起节点\", \"permission\": [] },",
        "    { \"type\": \"ApprovalNode\", \"id\": \"approve-a\", \"name\": \"经理审批\", \"subType\": \"单签\", \"candidates\": [] },",
        "    { \"type\": \"EndNode\", \"id\": \"end-a\", \"name\": \"结束\" }",
        "  ],",
        "  \"edges\": [",
        "    { \"id\": \"e1\", \"type\": \"SEQUENCE\", \"sourceId\": \"start-a\", \"targetId\": \"approve-a\" },",
        "    { \"id\": \"e2\", \"type\": \"SEQUENCE\", \"sourceId\": \"approve-a\", \"targetId\": \"end-a\" }",
        "  ]",
        "}");
  }

  @Test
  public void decodeV2MixedSplitWithPartialConditions() {
    CompositeFlowDefinitionCodec codec = new CompositeFlowDefinitionCodec();
    Try<StructuredFlowDefinition> definitionTry = codec.decode(v2MixedSplit());

    assertTrue(definitionTry.isSuccess());
    StructuredFlowDefinition definition = definitionTry.get();
    assertEquals(Integer.valueOf(2), definition.getFormatVersion());
    assertEquals(3, definition.getOutgoingEdges("start-a").size());

    long conditionalCount = definition.getOutgoingEdges("start-a").stream()
        .filter(edge -> edge.getCondition() != null)
        .count();
    assertEquals(2, conditionalCount);
  }

  @Test
  public void decodeV1InclusiveForkConvertsToV2Structure() {
    CompositeFlowDefinitionCodec codec = new CompositeFlowDefinitionCodec();
    Try<StructuredFlowDefinition> definitionTry = codec.decode(v1InclusiveFork());

    assertTrue(definitionTry.isSuccess());
    StructuredFlowDefinition definition = definitionTry.get();
    assertEquals(Integer.valueOf(2), definition.getFormatVersion());

    boolean hasJoinNode = definition.getNodes().stream()
        .anyMatch(node -> node.getId().contains("join"));
    assertTrue(hasJoinNode);

    assertEquals(3, definition.getOutgoingEdges("start-a").size());
  }

  private String v2XorFlow() {
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
        "    { \"type\": \"审批节点\", \"name\": \"经理审批\", \"id\": \"approve-a\", \"subType\": \"单签\", \"candidates\": [], \"permission\": [], \"actions\": [\"reject\"] },",
        "    { \"type\": \"审批节点\", \"name\": \"直属审批\", \"id\": \"approve-b\", \"subType\": \"单签\", \"candidates\": [], \"permission\": [], \"actions\": [\"reject\"] },",
        "    { \"type\": \"结束节点\", \"name\": \"结束\", \"id\": \"end-a\" }",
        "  ],",
        "  \"edges\": [",
        "    { \"id\": \"edge-seq-1\", \"type\": \"SEQUENCE\", \"sourceId\": \"start-a\", \"targetId\": \"approve-a\", \"condition\": { \"left\": { \"type\": \"field\", \"field\": \"amount\" }, \"op\": \"greater\", \"right\": 1000 }, \"order\": 1 },",
        "    { \"id\": \"branch-2\", \"type\": \"SEQUENCE\", \"sourceId\": \"start-a\", \"targetId\": \"approve-b\", \"condition\": { \"left\": { \"type\": \"field\", \"field\": \"amount\" }, \"op\": \"less_or_equal\", \"right\": 1000 }, \"order\": 2 },",
        "    { \"id\": \"edge-seq-2\", \"type\": \"SEQUENCE\", \"sourceId\": \"approve-a\", \"targetId\": \"end-a\" },",
        "    { \"id\": \"edge-seq-3\", \"type\": \"SEQUENCE\", \"sourceId\": \"approve-b\", \"targetId\": \"end-a\" }",
        "  ]",
        "}");
  }

  private String v2FlowWithEdgeType(String edgeType) {
    return String.join("\n",
        "{",
        "  \"formatVersion\": 2,",
        "  \"id\": \"flow-edge-type\",",
        "  \"name\": \"边类型测试\",",
        "  \"nodes\": [",
        "    { \"type\": \"发起节点\", \"id\": \"start-a\" },",
        "    { \"type\": \"审批节点\", \"id\": \"approve-a\", \"subType\": \"单签\", \"candidates\": [] }",
        "  ],",
        "  \"edges\": [",
        "    { \"id\": \"edge-a\", \"type\": \"" + edgeType + "\", \"sourceId\": \"start-a\", \"targetId\": \"approve-a\" }",
        "  ]",
        "}");
  }

  private String v2JoinFlow(String joinType) {
    return String.join("\n",
        "{",
        "  \"formatVersion\": 2,",
        "  \"id\": \"flow-join\",",
        "  \"name\": \"汇聚测试\",",
        "  \"nodes\": [",
        "    { \"type\": \"发起节点\", \"id\": \"start-a\" },",
        "    { \"type\": \"审批节点\", \"id\": \"approve-a\", \"subType\": \"单签\", \"candidates\": [] },",
        "    { \"type\": \"审批节点\", \"id\": \"approve-b\", \"subType\": \"单签\", \"candidates\": [] },",
        "    { \"type\": \"JoinNode\", \"id\": \"join-a\", \"joinType\": \"" + joinType + "\" },",
        "    { \"type\": \"结束节点\", \"id\": \"end-a\" }",
        "  ],",
        "  \"edges\": [",
        "    { \"type\": \"SEQUENCE\", \"sourceId\": \"start-a\", \"targetId\": \"approve-a\" },",
        "    { \"type\": \"SEQUENCE\", \"sourceId\": \"start-a\", \"targetId\": \"approve-b\" },",
        "    { \"type\": \"SEQUENCE\", \"sourceId\": \"approve-a\", \"targetId\": \"join-a\" },",
        "    { \"type\": \"SEQUENCE\", \"sourceId\": \"approve-b\", \"targetId\": \"join-a\" },",
        "    { \"type\": \"SEQUENCE\", \"sourceId\": \"join-a\", \"targetId\": \"end-a\" }",
        "  ]",
        "}");
  }

  private String v2MixedSplit() {
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

  private String v1InclusiveFork() {
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
}
