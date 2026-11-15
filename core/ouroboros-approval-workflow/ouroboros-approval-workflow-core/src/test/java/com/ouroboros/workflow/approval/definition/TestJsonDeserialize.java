package com.ouroboros.workflow.approval.definition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.type.TypeReference;
import com.ouroboros.util.Json;
import io.vavr.control.Try;
import com.ouroboros.workflow.approval.definition.condition.Condition;
import com.ouroboros.workflow.approval.definition.FlowDefinition;
import com.ouroboros.workflow.approval.definition.FlowNode;
import com.ouroboros.workflow.approval.definition.nodes.*;

public class TestJsonDeserialize {
  @Test
  public void testSerializeNode() {
    StartNode startNode = new StartNode();
    SingleApprovalNode singleApproval = new SingleApprovalNode();
    MultipleApprovalNode multiApproval = new MultipleApprovalNode();
    ForkNode splitNode = new ForkNode();
    SubflowNode subFlowNode = new SubflowNode();
    List<FlowNode> list = Arrays.asList(startNode, singleApproval, multiApproval, splitNode, subFlowNode);
    String jsonString = Json.toJsonString(list);
    System.out.println(jsonString);
    assertNotNull(jsonString);
  }

  @Test
  public void testDeserializeNode() {
    String jsonString = String.join("\n",
        "[",
        "  { \"type\": \"发起节点\", \"name\": null, \"id\": null, \"candidates\": null, \"permission\": null, \"next\": null },",
        "  { \"type\": \"抄送节点\", \"name\": null, \"id\": null, \"candidates\": null, \"permission\": null, \"next\": null },",
        "  { \"type\": \"审批节点\", \"name\": \"单签节点\", \"id\": null, \"candidates\": null, \"permission\": null, \"next\": null, \"subType\": \"单签\", \"actions\": null },",
        "  { \"type\": \"审批节点\", \"name\": \"会签节点\", \"id\": null, \"candidates\": null, \"permission\": null, \"next\": null, \"subType\": \"会签\", \"sequence\": null, \"percentage\": 0, \"canAdd\": null, \"rejectAction\": null },",
        "  { \"type\": \"分流节点\", \"name\": null, \"id\": null, \"candidates\": null, \"permission\": null, \"next\": null, \"logic\": null, \"branches\": null },",
        "  { \"type\": \"子流程节点\", \"name\": null, \"id\": null, \"candidates\": null, \"permission\": null, \"next\": null, \"subFlowKey\": null, \"rejectAction\": null, \"inParameters\": null, \"outParameters\": null }",
        "]");
    Try<List<FlowNode>> tryList = Json.tryToList(new TypeReference<List<FlowNode>>() {
    }, jsonString);
    System.out.println(tryList);
    List<FlowNode> list = tryList.get();
    assertEquals(6, list.size());
    assertTrue(list.get(0) instanceof StartNode);
    assertTrue(list.get(1) instanceof CcNode);
    assertTrue(list.get(2) instanceof SingleApprovalNode);
    assertTrue(list.get(3) instanceof MultipleApprovalNode);
    assertTrue(list.get(4) instanceof ForkNode);
    assertTrue(list.get(5) instanceof SubflowNode);
  }

  @Test
  public void testDeserializeFlow() {
    String json = "{\"name\":\"新工作流\",\"id\":\"workflow49fb5bf0febc11ed8f3d153c27ff7110\",\"icon\":\"shenpi\",\"color\":\"rgba(255, 69, 0, 0.68)\",\"category\":null,\"description\":\"\",\"form\":null,\"nodes\":[{\"type\":\"发起节点\",\"name\":\"发起节点\",\"id\":\"workflow49fb5bf1febc11ed8f3d153c27ff7110\",\"permission\":[],\"next\":\"workflow49fb5bf2febc11ed8f3d153c27ff7110\"},{\"type\":\"审批节点\",\"subType\":\"单签\",\"name\":\"审批节点\",\"id\":\"workflow49fb5bf2febc11ed8f3d153c27ff7110\",\"candidates\":[],\"multipleMode\":\"并行\",\"percentage\":100,\"permission\":[],\"next\":\"\",\"rejectAction\":\"reject\"}]}";
    Try<FlowDefinition> flow = Json.tryToBean(FlowDefinition.class, json);
    System.out.println(flow);
    assertTrue(flow.isSuccess());
  }

  @Test
  public void testInheritanceClass() {
    ChildA objA = new ChildA();
    objA.setAge("10");
    objA.setName("A");
    String str = Json.toJsonString(objA);
    System.out.println(str);
    assertNotNull(str);
  }

  @Test
  public void testCondition() {
    String conditionString = String.join("\n",
        "{",
        "  \"id\": \"99cd705529f8\",",
        "  \"conjunction\": \"or\",",
        "  \"children\": [",
        "    { \"id\": \"0703aa8d9b43\", \"left\": { \"type\": \"field\", \"field\": \"text\" }, \"op\": \"equal\", \"right\": \"1\" },",
        "    { \"id\": \"cb6b441bc15e\", \"conjunction\": \"and\", \"children\": [{ \"id\": \"e297dbd7bc7f\", \"left\": { \"type\": \"field\", \"field\": \"text\" }, \"op\": \"equal\", \"right\": \"2\" }, { \"id\": \"97673583d918\", \"left\": { \"type\": \"field\", \"field\": \"number\" }, \"op\": \"equal\", \"right\": 1 }] }",
        "  ]",
        "}");
    Try<Condition> condition = Json.tryToBean(Condition.class, conditionString);
    assertTrue(condition.isSuccess());
  }

  @Test
  public void testFlow() {
    String modelString = String.join("\n",
        "{",
        "  \"id\": \"gaochu-1_\",",
        "  \"name\": \"新审批流\",",
        "  \"category\": \"\",",
        "  \"icon\": \"\",",
        "  \"description\": \"\",",
        "  \"formType\": \"\",",
        "  \"formKey\": \"\",",
        "  \"nodes\": [",
        "    {",
        "      \"type\": \"发起节点\",",
        "      \"name\": \"发起节点\",",
        "      \"id\": \"starta872bee06e2d11ee823851b8b382726b\",",
        "      \"candidates\": null,",
        "      \"permission\": null,",
        "      \"next\": \"appra872bee16e2d11ee823851b8b382726b\"",
        "    },",
        "    {",
        "      \"type\": \"审批节点\",",
        "      \"name\": \"作业负责人意见\",",
        "      \"id\": \"appra872bee16e2d11ee823851b8b382726b\",",
        "      \"candidates\": [{ \"type\": \"UserSelect\", \"mode\": \"multiple\" }],",
        "      \"permission\": [],",
        "      \"next\": \"nodebd44cb106e2d11ee823851b8b382726b\",",
        "      \"subType\": \"单签\",",
        "      \"actions\": [\"reject\"]",
        "    },",
        "    {",
        "      \"type\": \"审批节点\",",
        "      \"name\": \"所在单位意见\",",
        "      \"id\": \"nodebd44cb106e2d11ee823851b8b382726b\",",
        "      \"candidates\": [{ \"type\": \"UserSelect\", \"mode\": \"multiple\" }],",
        "      \"permission\": [],",
        "      \"next\": \"nodec92a72906e2d11ee823851b8b382726b\",",
        "      \"subType\": \"单签\",",
        "      \"actions\": [\"reject\"]",
        "    },",
        "    {",
        "      \"type\": \"审批节点\",",
        "      \"name\": \"生产部意见\",",
        "      \"id\": \"nodec92a72906e2d11ee823851b8b382726b\",",
        "      \"candidates\": [{ \"type\": \"User\", \"users\": [\"1959ec1b0a7a4030aee0ceabada71282\"] }],",
        "      \"permission\": [],",
        "      \"next\": \"noded32f25106e2d11ee823851b8b382726b\",",
        "      \"subType\": \"单签\",",
        "      \"actions\": [\"reject\"]",
        "    },",
        "    {",
        "      \"type\": \"审批节点\",",
        "      \"name\": \"安全部意见\",",
        "      \"id\": \"noded32f25106e2d11ee823851b8b382726b\",",
        "      \"candidates\": [{ \"type\": \"User\", \"users\": [\"38d4abee597a4d9dababc72f8cd46247\", \"76fe527759324a83a94b79473043d36b\", \"dd9edb1d4f744b92a78747d39c105aee\"] }],",
        "      \"permission\": [],",
        "      \"next\": \"nodee19b8a70994e11ee868a4f1ace1ce5fd\",",
        "      \"subType\": \"单签\",",
        "      \"actions\": [\"reject\"]",
        "    },",
        "    {",
        "      \"type\": \"分流节点\",",
        "      \"name\": \"分流\",",
        "      \"id\": \"nodef3a37ed0bf1911ee860f5d3efff6dd86\",",
        "      \"logic\": \"exclusive\",",
        "      \"branches\": [",
        "        {",
        "          \"name\": \"条件节点\",",
        "          \"id\": \"condf4a4c230bf1911ee860f5d3efff6dd86\",",
        "          \"condition\": { \"id\": \"b39183ec1ffd\", \"conjunction\": \"and\", \"children\": [{ \"id\": \"ab54e5de101e\", \"left\": { \"type\": \"field\", \"field\": \"ticketLevelStr\" }, \"op\": \"select_equals\", \"right\": \"特级动火作业\" }] },",
        "          \"first\": \"apprf4a4c231bf1911ee860f5d3efff6dd86\"",
        "        },",
        "        {",
        "          \"name\": \"条件节点\",",
        "          \"id\": \"condf4a4c232bf1911ee860f5d3efff6dd86\",",
        "          \"condition\": { \"id\": \"bc3549f3a2f4\", \"conjunction\": \"and\", \"children\": [{ \"id\": \"e2287279b0ff\", \"left\": { \"type\": \"field\", \"field\": \"firingPerson\" }, \"op\": \"equal\", \"right\": \"222\" }] },",
        "          \"first\": \"apprf4a4c233bf1911ee860f5d3efff6dd86\"",
        "        }",
        "      ],",
        "      \"next\": \"\"",
        "    }",
        "  ]",
        "}");
    Try<FlowDefinition> model = Json.tryToBean(FlowDefinition.class, modelString);
    String json = Json.toPrettyJsonString(model.get());
    assertTrue(model.isSuccess());
  }

  @Test
  public void singleApprovalNodeAllowRejectToSelfDefaultsToTrueWhenMissing() {
    String jsonString = String.join("\n",
        "[",
        "  { \"type\": \"审批节点\", \"name\": \"单签节点\", \"id\": \"approve-a\", \"subType\": \"单签\", \"actions\": [\"reject\"] }",
        "]");

    Try<List<FlowNode>> tryList = Json.tryToList(new TypeReference<List<FlowNode>>() {
    }, jsonString);

    assertTrue(tryList.isSuccess());
    ApprovalNode node = (ApprovalNode) tryList.get().get(0);
    assertTrue(node.isAllowRejectToSelf());
  }

  @Test
  public void multipleApprovalNodeShouldParseAllowRejectToSelfAndActions() {
    String jsonString = String.join("\n",
        "[",
        "  { \"type\": \"审批节点\", \"name\": \"会签节点\", \"id\": \"approve-b\", \"subType\": \"会签\", \"multipleMode\": \"并行\", \"percentage\": 80, \"actions\": [\"reject\", \"rollback\"], \"rejectAction\": \"rollback\", \"allowRejectToSelf\": false }",
        "]");

    Try<List<FlowNode>> tryList = Json.tryToList(new TypeReference<List<FlowNode>>() {
    }, jsonString);

    assertTrue(tryList.isSuccess());
    MultipleApprovalNode node = (MultipleApprovalNode) tryList.get().get(0);
    assertFalse(node.isAllowRejectToSelf());
    assertEquals(Arrays.asList("reject", "rollback"), node.getActions());
    assertEquals("rollback", node.getRejectAction());
  }

  @Test
  public void multipleApprovalNodeRoundTripShouldPreserveExplicitActions() {
    MultipleApprovalNode multipleApprovalNode = new MultipleApprovalNode();
    multipleApprovalNode.setId("approve-b");
    multipleApprovalNode.setMultipleMode("串行");
    multipleApprovalNode.setPercentage(100);
    multipleApprovalNode.setRejectAction("reject");
    multipleApprovalNode.setActions(Arrays.asList("reject", "addSign"));

    String json = Json.toJsonString(Collections.singletonList(multipleApprovalNode));
    Try<List<FlowNode>> roundTrip = Json.tryToList(new TypeReference<List<FlowNode>>() {
    }, json);

    assertTrue(roundTrip.isSuccess());
    MultipleApprovalNode roundTripNode = (MultipleApprovalNode) roundTrip.get().get(0);
    assertEquals(Arrays.asList("reject", "addSign"), roundTripNode.getActions());
  }

  @Test
  public void approvalNodeRoundTripShouldPreserveAllowRejectToSelf() {
    SingleApprovalNode singleApprovalNode = new SingleApprovalNode();
    singleApprovalNode.setId("approve-a");
    singleApprovalNode.setActions(Arrays.asList("reject", "freeReject"));
    singleApprovalNode.setAllowRejectToSelf(false);

    String json = Json.toJsonString(Collections.singletonList(singleApprovalNode));
    Try<List<FlowNode>> roundTrip = Json.tryToList(new TypeReference<List<FlowNode>>() {
    }, json);

    assertTrue(roundTrip.isSuccess());
    ApprovalNode roundTripNode = (ApprovalNode) roundTrip.get().get(0);
    assertFalse(roundTripNode.isAllowRejectToSelf());
    assertEquals(Arrays.asList("reject", "freeReject"), roundTripNode.getActions());
  }

  @Test
  public void explicitJsonNullFieldsFallBackToContractDefaults() {
    String jsonString = String.join("\n",
        "[",
        "  { \"type\": \"审批节点\", \"name\": \"会签节点\", \"id\": \"approve-null\", \"subType\": \"会签\",",
        "    \"rejectAction\": null, \"multipleMode\": null, \"notify\": null, \"allowRejectToSelf\": null, \"percentage\": null }",
        "]");

    Try<List<FlowNode>> tryList = Json.tryToList(new TypeReference<List<FlowNode>>() {
    }, jsonString);

    assertTrue(tryList.isSuccess());
    MultipleApprovalNode node = (MultipleApprovalNode) tryList.get().get(0);
    assertEquals("reject", node.getRejectAction());
    assertEquals("并行", node.getMultipleMode());
    assertEquals(100, node.getPercentage());
    assertTrue(node.isAllowRejectToSelf());
  }

  @Test
  public void explicitJsonNullNotifyDoesNotSilentlyDisableNotification() {
    String jsonString = String.join("\n",
        "[",
        "  { \"type\": \"审批节点\", \"name\": \"单签节点\", \"id\": \"approve-null\", \"subType\": \"单签\", \"notify\": null }",
        "]");

    Try<List<FlowNode>> tryList = Json.tryToList(new TypeReference<List<FlowNode>>() {
    }, jsonString);

    assertTrue(tryList.isSuccess());
    ApprovalNode node = (ApprovalNode) tryList.get().get(0);
    assertTrue(node.isNotifyEnabled());
    assertTrue(node.isAllowRejectToSelf());
  }

  @Test
  public void unknownApprovalSubTypeIsRejectedInsteadOfProducingNullNode() {
    String jsonString = String.join("\n",
        "[",
        "  { \"type\": \"审批节点\", \"name\": \"坏节点\", \"id\": \"approve-bad\", \"subType\": \"串签\" }",
        "]");

    Try<List<FlowNode>> tryList = Json.tryToList(new TypeReference<List<FlowNode>>() {
    }, jsonString);

    assertTrue(tryList.isFailure());
    assertTrue(String.valueOf(tryList.getCause()).contains("串签"));
  }

  @Test
  public void missingApprovalSubTypeIsRejected() {
    String jsonString = String.join("\n",
        "[",
        "  { \"type\": \"审批节点\", \"name\": \"无子类型\", \"id\": \"approve-missing\" }",
        "]");

    Try<List<FlowNode>> tryList = Json.tryToList(new TypeReference<List<FlowNode>>() {
    }, jsonString);

    assertTrue(tryList.isFailure());
  }


  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "name")
  @JsonSubTypes({
      @JsonSubTypes.Type(value = ChildA.class, name = "A"),
      @JsonSubTypes.Type(value = ChildB.class, name = "B")
  })
  public static class ParentA {

    String name;

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }
  }

  public static class ChildA extends ParentA {
    String age;

    public String getAge() {
      return age;
    }

    public void setAge(String age) {
      this.age = age;
    }
  }

  public static class ChildB extends ParentA {
    String aaa;

    public String getAaa() {
      return aaa;
    }

    public void setAaa(String aaa) {
      this.aaa = aaa;
    }
  }
}
