package com.ouroboros.workflow.approval.model;

import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;

public class FlowFormField {
  String name;
  String label;
  String type;
  Map<String, Object> extraProps = new LinkedHashMap<>();

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getLabel() {
    return label;
  }

  public void setLabel(String label) {
    this.label = label;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  @JsonAnyGetter
  public Map<String, Object> getExtraProps() {
    return extraProps;
  }

  @JsonAnySetter
  public void setExtraProp(String key, Object value) {
    this.extraProps.put(key, value);
  }
}
