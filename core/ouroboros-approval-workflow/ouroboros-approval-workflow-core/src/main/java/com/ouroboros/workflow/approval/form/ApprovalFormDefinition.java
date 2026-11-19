package com.ouroboros.workflow.approval.form;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.util.Json;

/** A design-time field description, independent of runtime form data and permissions. */
public record ApprovalFormDefinition(String name, String title, String description, List<Field> fields) {
  public record Field(String name, String label, String description, String type, Map<String, Object> params) {}

  public static ApprovalFormDefinition read(String content) {
    JsonNode node = Json.tryToBean(JsonNode.class, content)
        .getOrElseThrow(cause -> new BusinessException("自定义表单 JSON 不合法", cause));
    object(node, Set.of("formatVersion", "name", "title", "description", "fields"), "表单");
    if (!node.path("formatVersion").isIntegralNumber() || !node.path("formatVersion").canConvertToInt()
        || node.path("formatVersion").intValue() != 1) {
      throw new BusinessException("自定义表单 formatVersion 必须为整数 1");
    }
    String name = text(node, "name");
    if (name.contains("..") || name.chars().anyMatch(c -> Character.isWhitespace(c) || c == '/' || c == '\\')) {
      throw new BusinessException("自定义表单 name 不能包含路径分隔符、空白或上级目录片段");
    }
    if (!node.path("fields").isArray()) {
      throw new BusinessException("自定义表单 fields 必须为数组");
    }
    List<Field> fields = new ArrayList<>();
    Set<String> names = new HashSet<>();
    for (JsonNode field : node.path("fields")) {
      object(field, Set.of("name", "label", "description", "type", "params"), "字段");
      String fieldName = text(field, "name");
      if (!fieldName.matches("[A-Za-z_][A-Za-z0-9_]*") || fieldName.startsWith("__")) {
        throw new BusinessException("自定义表单字段 name 必须为顶层字段标识，且不能以 __ 开头: " + fieldName);
      }
      if (!names.add(fieldName)) {
        throw new BusinessException("自定义表单字段重名: " + fieldName);
      }
      if (field.has("params") && !field.get("params").isObject()) {
        throw new BusinessException("自定义表单字段 params 必须为对象: " + fieldName);
      }
      fields.add(new Field(fieldName, text(field, "label"), description(field), text(field, "type"),
          field.has("params") ? Json.toMap(field.get("params").toString()) : Map.of()));
    }
    return new ApprovalFormDefinition(name, text(node, "title"), description(node), List.copyOf(fields));
  }

  private static String text(JsonNode node, String key) {
    if (!node.path(key).isTextual() || node.path(key).asText().isBlank()) {
      throw new BusinessException("自定义表单 " + key + " 必须为非空字符串");
    }
    return node.path(key).asText();
  }

  private static String description(JsonNode node) {
    if (node.has("description") && !node.get("description").isTextual()) {
      throw new BusinessException("自定义表单 description 必须为字符串");
    }
    return node.path("description").asText("");
  }

  private static void object(JsonNode node, Set<String> keys, String location) {
    if (node == null || !node.isObject()) {
      throw new BusinessException("自定义表单" + location + "必须为对象");
    }
    node.fieldNames().forEachRemaining(key -> {
      if (!keys.contains(key)) {
        throw new BusinessException("自定义表单" + location + "包含未知属性: " + key);
      }
    });
  }
}
