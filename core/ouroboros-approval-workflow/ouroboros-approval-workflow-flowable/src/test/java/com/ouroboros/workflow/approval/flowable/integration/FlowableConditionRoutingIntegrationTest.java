package com.ouroboros.workflow.approval.flowable.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.ouroboros.workflow.approval.flowable.testkit.FlowableEngineCapabilitySet;
import com.ouroboros.workflow.approval.flowable.testkit.FlowableEngineTestAdapter;
import com.ouroboros.workflow.approval.testkit.adapter.EngineCapability;
import com.ouroboros.workflow.approval.testkit.adapter.EngineTestAdapter;
import com.ouroboros.workflow.approval.testkit.contract.AbstractEngineContractTest;
import com.ouroboros.workflow.approval.testkit.snapshot.RuntimeInstance;
import com.ouroboros.workflow.approval.testkit.snapshot.StartRequest;
import com.ouroboros.workflow.approval.testkit.snapshot.TaskView;

/**
 * 条件分支的端到端路由验证。
 *
 * <p>2026-09-12 的审查发现条件表达式此前在引擎里根本算不对：字符串字面量没有引号
 * （`level == 特级`，`特级` 被 JUEL 当变量名），所有字符串相等条件恒假且不报错；
 * 集合判定用了 JUEL 不支持的 `{a,b}` 形态。当时的测试只断言表达式字符串长相，
 * 所以全绿。本测试走完整链路（定义 → 转译 → 部署 → 启动 → 按变量走分支），
 * 断言真实落到的审批节点，作为"条件真的生效"的证据。
 */
class FlowableConditionRoutingIntegrationTest extends AbstractEngineContractTest {

  private final FlowableEngineTestAdapter adapter = new FlowableEngineTestAdapter();

  @Override
  protected EngineTestAdapter adapter() {
    return adapter;
  }

  @Override
  protected EngineCapability capabilities() {
    return FlowableEngineCapabilitySet.DEFAULT;
  }

  @Test
  void customFormRedeploymentKeepsOldInstancesOnTheirOriginalConditions() throws Exception {
    ObjectNode original = (ObjectNode) new ObjectMapper().readTree(conditionalFlowJson("purchaseType", "select_equals", "equipment"));
    original.put("formType", "custom");
    original.put("formKey", "purchase");
    ArrayNode originalEdges = (ArrayNode) original.get("edges");
    for (int index = originalEdges.size() - 1; index >= 0; index--) {
      if (List.of("e1", "e4").contains(originalEdges.get(index).path("id").asText())) originalEdges.remove(index);
    }
    ArrayNode originalNodes = (ArrayNode) original.get("nodes");
    for (int index = originalNodes.size() - 1; index >= 0; index--) {
      if ("approve-special".equals(originalNodes.get(index).path("id").asText())) originalNodes.remove(index);
    }
    adapter.deploy("flow-condition-routing", original.toString());
    var oldInstance = adapter.start("flow-condition-routing", new StartRequest("starter", Collections.emptyMap(), Map.of("purchaseType", "equipment"), Collections.emptyMap()));
    ObjectNode revised = original.deepCopy();
    for (JsonNode edge : revised.path("edges")) {
      if ("e2".equals(edge.path("id").asText())) ((ObjectNode) edge.path("condition")).put("right", "service");
    }
    adapter.deploy("flow-condition-routing", revised.toString());
    var newInstance = adapter.start("flow-condition-routing", new StartRequest("starter", Collections.emptyMap(), Map.of("purchaseType", "equipment"), Collections.emptyMap()));
    adapter.completeStartTask(oldInstance.getInstanceId());
    adapter.completeStartTask(newInstance.getInstanceId());
    assertEquals(1, adapter.activeTasks(oldInstance.getInstanceId(), "approve-normal").size());
    assertEquals(1, adapter.activeTasks(newInstance.getInstanceId(), "approve-none").size());
  }

  @Test
  void stringValueInTheSetRoutesToTheCollectionBranch() {
    // 集合判定 level ∈ {特级, 一级}：这是修复前恒假、修复后能真正命中的路径
    assertEquals("approve-special", activeBranchNode("level", "特级"));
    assertEquals("approve-special", activeBranchNode("level", "一级"));
  }

  @Test
  void valueOutsideTheSetFallsToTheExplicitFallbackBranch() {
    // 不命中时必须由**显式兜底边**接住，否则引擎抛
    // "No outgoing sequence flow of the exclusive gateway could be selected"
    assertEquals("approve-none", activeBranchNode("level", "三级"));
  }

  /**
   * 数值字段与字符串集合比较时 JUEL 直接抛错，而不是静默判 false —— "错误必须外显"。
   *
   * <p>也说明"模型里字段类型与操作符右值必须匹配"是使用前提。
   */
  @Test
  void numericFieldWithNonNumericSetConditionFailsInsteadOfSilentlyFalse() {
    assertThatThrownBy(() -> activeBranchNode("amount", 500))
        .hasMessageContaining("amount == '特级'");
  }

  /**
   * 否定比较（{@code not_equal}）作为分支条件时能真正命中。
   *
   * <p>取值 三级 满足 {@code level != '三级'} 的反面——这里用"不等于另一个值"来构造命中：
   * 取值 一级 时 {@code level != '三级'} 为真，应落到比较分支。
   */
  @Test
  void stringInequalityRoutesToTheComparisonBranch() {
    // 三级 不在集合内（e1 不命中），且满足 level != '特级'（e2 命中）
    assertEquals("approve-normal", activeBranchNodeWithOp("level", "三级", "not_equal", "特级"));
  }

  /** 显式指定入口操作符与右值：便于构造"字段取值与比较值不同"的命中场景。 */
  private String activeBranchNodeWithOp(String field, Object fieldValue, String op, String right) {
    String flowJson = conditionalFlowJson(field, op, right);
    RuntimeInstance instance = deployAndStart(flowJson, startRequest(field, fieldValue));
    return activeNodeOf(instance);
  }

  private String activeNodeOf(RuntimeInstance instance) {
    for (String node : List.of("approve-special", "approve-normal", "approve-none")) {
      if (!adapter().activeTasks(instance.getInstanceId(), node).isEmpty()) {
        return node;
      }
    }
    throw new AssertionError("条件分流后没有任何激活的审批分支");
  }

  private String activeBranchNode(String field, Object value) {
    String right;
    if (value == null) {
      right = null;
    } else if (value instanceof Number number) {
      right = String.valueOf(number);
    } else {
      // right 传"值本身"，不要在这里补引号：引号由操作符的 literal 渲染负责。
      // 这里补引号会产生 level == '"三级"' 而恒假——与 CompareCondition 曾经的双引号缺陷同源。
      right = String.valueOf(value);
    }
    String op = value instanceof Number ? "greater" : "not_equal";
    String flowJson = conditionalFlowJson(field, op, right);
    RuntimeInstance instance = deployAndStart(flowJson, startRequest(field, value));
    return activeNodeOf(instance);
  }

  /** 条件字段必须以实例变量形式传入，否则 JUEL 求值报 Unknown property。 */
  private StartRequest startRequest(String field, Object value) {
    Map<String, Object> variables = new LinkedHashMap<>();
    variables.put(field, value);
    return new StartRequest("starter", Collections.emptyMap(), variables, Collections.emptyMap());
  }

  private String startWithLevel(String level) {
    return conditionalFlowJson("level", "equal", jsonString(level));
  }

  private String startWithAmount(int amount) {
    return conditionalFlowJson("amount", "greater", String.valueOf(amount));
  }

  /**
   * 分流结构：发起 → 两个带条件的分支 → 结束。
   *
   * <p>两条边都带条件，避免"无条件默认边"把结果掩盖掉。用 Jackson 构造 JSON 而不是手拼
   * 字符串——条件里的引号与集合字面量手拼极易写出非法 JSON（本测试第一版就踩了这个坑）。
   */
  private String conditionalFlowJson(String field, String op, String right) {
    ObjectMapper mapper = new ObjectMapper();
    try {
      ObjectNode root = mapper.createObjectNode();
      root.put("formatVersion", 2);
      root.put("id", "flow-condition-routing");
      root.put("name", "条件路由回归");
      root.put("category", "");
      root.put("icon", "shenpi");
      root.put("description", "");
      root.put("formType", "default");
      root.put("formKey", "runtime-form");

      ArrayNode nodes = root.putArray("nodes");
      nodes.add(node("发起节点", "发起节点", "startnode", null, null));
      nodes.add(node("审批节点", "集合命中审批", "approve-special", "单签", "approver-special"));
      nodes.add(node("审批节点", "比较命中审批", "approve-normal", "单签", "approver-normal"));
      nodes.add(node("审批节点", "兜底审批", "approve-none", "单签", "approver-none"));
      nodes.add(node("结束节点", "结束", "end-a", null, null));

      ArrayNode edges = root.putArray("edges");
      // e1：集合判定命中分支
      edges.add(edge("e1", "startnode", "approve-special", field, "select_any_in",
          "[" + jsonString("特级") + "," + jsonString("一级") + "]"));
      // e2：比较判定命中分支（字符串取值用 not_equal 构成"不在集合内"的反向条件）
      edges.add(edge("e2", "startnode", "approve-normal", field, op, right));
      // e3：无条件兜底边——集合判定不命中时必须有出边接住，否则引擎抛
      // "No outgoing sequence flow of the exclusive gateway could be selected"
      edges.add(edge("e3", "startnode", "approve-none", null, null, null));
      edges.add(edge("e4", "approve-special", "end-a", null, null, null));
      edges.add(edge("e5", "approve-normal", "end-a", null, null, null));
      edges.add(edge("e6", "approve-none", "end-a", null, null, null));
      return mapper.writeValueAsString(root);
    } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
      throw new IllegalStateException("构造测试流程定义失败", ex);
    }
  }

  private ObjectNode node(String type, String name, String id, String subType, String assignee) {
    ObjectNode node = new ObjectMapper().createObjectNode();
    node.put("type", type);
    node.put("name", name);
    node.put("id", id);
    if (subType != null) {
      node.put("subType", subType);
    }
    ArrayNode candidates = node.putArray("candidates");
    if (assignee != null) {
      ObjectNode candidate = candidates.addObject();
      candidate.put("type", "User");
      candidate.putArray("users").add(assignee);
    }
    node.putArray("permission");
    return node;
  }

  private ObjectNode edge(String id, String source, String target, String field, String op, String right) {
    ObjectNode edge = new ObjectMapper().createObjectNode();
    edge.put("id", id);
    edge.put("type", "SEQUENCE");
    edge.put("sourceId", source);
    edge.put("targetId", target);
    if (op != null) {
      ObjectNode condition = edge.putObject("condition");
      ObjectNode left = condition.putObject("left");
      left.put("type", "field");
      left.put("field", field);
      condition.put("op", op);
      // right 有两种形态：已是 JSON 片段（集合判定用 [..]）或裸字符串值
      condition.set("right", right != null && right.startsWith("[") ? rawJson(right)
          : condition.textNode(right));
    }
    return edge;
  }

  /** 允许把已是 JSON 的片段（集合字面量）原样放进 right。 */
  private JsonNode rawJson(String value) {
    try {
      return new ObjectMapper().readTree(value);
    } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
      throw new IllegalStateException("right 不是合法 JSON 片段: " + value, ex);
    }
  }

  private String jsonString(String value) {
    return "\"" + value + "\"";
  }
}
