package com.ouroboros.workflow.approval.definition.nodes;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.ouroboros.workflow.approval.definition.Candidate;
import com.ouroboros.workflow.approval.definition.NodeFormFieldPermission;
import com.ouroboros.workflow.approval.definition.codec.FlowDefinitionCodecException;

public class ApprovalNodeJsonDeserializer extends StdDeserializer<ApprovalNode> {
  public ApprovalNodeJsonDeserializer() {
    super(ApprovalNode.class);
  }

  @Override
  public ApprovalNode deserialize(
      JsonParser jsonParser,
      DeserializationContext deserializationContext) throws IOException {
    var mapper = jsonParser.getCodec();
    JsonNode node = mapper.readTree(jsonParser);

    var id = textOrNull(node, "id", "");
    var name = textOrNull(node, "name", "");
    var next = textOrNull(node, "next", null);
    var noCandidateMode = textOrNull(node, "noCandidateMode", null);
    var allowRejectToSelf = booleanOrNull(node, "allowRejectToSelf", true);
    var notify = booleanOrNull(node, "notify", null);
    var timeout = node.has("timeout") && node.get("timeout").isObject()
        ? mapper.treeToValue(node.get("timeout"), ApprovalNode.Timeout.class)
        : null;
    var reAssignUsers = node.has("reAssignUsers") && node.get("reAssignUsers").isArray()
        ? Arrays.asList(mapper.treeToValue(node.get("reAssignUsers"), String[].class))
        : Collections.<String>emptyList();
    var actions = node.has("actions") && node.get("actions").isArray()
        ? Arrays.asList(mapper.treeToValue(node.get("actions"), String[].class))
        : Collections.<String>emptyList();
    var candidates = node.has("candidates") && node.get("candidates").isArray()
        ? Arrays.asList(mapper.treeToValue(node.get("candidates"), Candidate[].class))
        : Collections.<Candidate>emptyList();
    var permission = node.has("permission") && node.get("permission").isArray()
        ? Arrays.asList(mapper.treeToValue(node.get("permission"), NodeFormFieldPermission[].class))
        : Collections.<NodeFormFieldPermission>emptyList();

    var subType = textOrNull(node, "subType", "");
    if ("单签".equals(subType)) {
      var singleApprovalNode = new SingleApprovalNode();
      singleApprovalNode.setId(id);
      singleApprovalNode.setName(name);
      singleApprovalNode.setNext(next);
      singleApprovalNode.setCandidates(candidates);
      singleApprovalNode.setPermission(permission);
      singleApprovalNode.setActions(actions);
      singleApprovalNode.setTimeout(timeout);
      singleApprovalNode.setNotify(notify);
      singleApprovalNode.setAllowRejectToSelf(allowRejectToSelf);
      singleApprovalNode.setNoCandidateMode(ApprovalNode.NoCandidateMode.fromValue(noCandidateMode));
      singleApprovalNode.setReAssignUsers(reAssignUsers);
      return singleApprovalNode;
    } else if ("会签".equals(subType)) {
      var rejectAction = textOrNull(node, "rejectAction", "reject");
      var multipleMode = textOrNull(node, "multipleMode", "并行");
      var percentage = Optional.ofNullable(node.get("percentage"))
          .filter(JsonNode::isNumber)
          .map(JsonNode::asInt)
          .orElse(100);

      var multipleApprovalNode = new MultipleApprovalNode();
      multipleApprovalNode.setId(id);
      multipleApprovalNode.setName(name);
      multipleApprovalNode.setNext(next);
      multipleApprovalNode.setCandidates(candidates);
      multipleApprovalNode.setPermission(permission);
      multipleApprovalNode.setActions(actions);
      multipleApprovalNode.setTimeout(timeout);
      multipleApprovalNode.setNotify(notify);
      multipleApprovalNode.setRejectAction(rejectAction);
      multipleApprovalNode.setMultipleMode(multipleMode);
      multipleApprovalNode.setPercentage(percentage);
      multipleApprovalNode.setAllowRejectToSelf(allowRejectToSelf);
      multipleApprovalNode.setNoCandidateMode(ApprovalNode.NoCandidateMode.fromValue(noCandidateMode));
      multipleApprovalNode.setReAssignUsers(reAssignUsers);
      return multipleApprovalNode;
    } else {
      throw new FlowDefinitionCodecException(
          "未知的审批子类型: " + subType + "（受支持的子类型: 单签/会签），节点 id=" + id);
    }

  }

  /**
   * 取文本字段：缺失或显式 JSON {@code null} 时返回缺省值。
   *
   * <p>不能用 {@code Optional.ofNullable(node.get(key)).map(JsonNode::asText)}：Jackson 的 {@code NullNode}
   * 不是 Java null，{@code asText()} 返回字面量 {@code "null"}，于是 {@code orElse} 永不触发，
   * 显式 null 的字段会变成字符串 {@code "null"} 并写入流程定义。
   */
  private static String textOrNull(JsonNode node, String key, String defaultValue) {
    return node.hasNonNull(key) ? node.get(key).asText() : defaultValue;
  }

  /**
   * 取布尔字段：缺失或显式 JSON {@code null} 时返回缺省值。
   *
   * <p>同理，{@code NullNode.asBoolean()} 返回 {@code false}，会把显式 null 静默当成"关闭"。
   */
  private static Boolean booleanOrNull(JsonNode node, String key, Boolean defaultValue) {
    if (!node.hasNonNull(key)) {
      return defaultValue;
    }
    return node.get(key).asBoolean();
  }
}
