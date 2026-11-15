package com.ouroboros.web;

import java.util.*;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.NONE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TreeSelectOption {
  private String value;
  private String label;
  private Map<String, Object> extraProps = new LinkedHashMap<>();
  private List<TreeSelectOption> children;

  public TreeSelectOption() {
  }

  public TreeSelectOption(String value, String label) {
    this.value = value;
    this.label = label;
  }

  public String getValue() {
    return value;
  }

  public TreeSelectOption setValue(String value) {
    this.value = value;
    return this;
  }

  public String getLabel() {
    return label;
  }

  public TreeSelectOption setLabel(String label) {
    this.label = label;
    return this;
  }

  public List<TreeSelectOption> getChildren() {
    return children;
  }

  public TreeSelectOption setChildren(List<TreeSelectOption> children) {
    this.children = children;
    return this;
  }

  public TreeSelectOption addChild(TreeSelectOption child) {
    if (this.children == null) {
      this.children = new ArrayList<>();
    }
    this.children.add(child);
    return this;
  }

  @JsonAnyGetter
  public Map<String, Object> getExtraProps() {
    return extraProps;
  }

  public TreeSelectOption setExtraProps(Map<String, Object> extraProps) {
    this.extraProps = extraProps;
    return this;
  }

  @JsonAnySetter
  public TreeSelectOption addExtraProp(String key, Object value) {
    this.extraProps.put(key, value);
    return this;
  }

  public TreeSelectOption removeExtraProp(String key) {
    this.extraProps.remove(key);
    return this;
  }
}
