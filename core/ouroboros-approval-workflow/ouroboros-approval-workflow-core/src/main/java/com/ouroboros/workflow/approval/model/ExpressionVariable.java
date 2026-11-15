package com.ouroboros.workflow.approval.model;

import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAnyGetter;

// TODO: 定义表达式相关属性，比如可用的操作符等
public class ExpressionVariable {
  String name;
  String label;
  String type;
  Map<String, Object> extraProps = new LinkedHashMap<>();

  public String getName() {
    return name;
  }

  public ExpressionVariable setName(String name) {
    this.name = name;
    return this;
  }

  public String getLabel() {
    return label;
  }

  public ExpressionVariable setLabel(String label) {
    this.label = label;
    return this;
  }

  public String getType() {
    return type;
  }

  public ExpressionVariable setType(String type) {
    this.type = type;
    return this;
  }

  @JsonAnyGetter
  public Map<String, Object> getExtraProps() {
    return extraProps;
  }

  public ExpressionVariable setExtraProps(Map<String, Object> extraProps) {
    this.extraProps = extraProps;
    return this;
  }
}
