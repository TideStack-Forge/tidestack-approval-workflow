package com.ouroboros.web;

import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.NONE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SelectOption {
  private String value;
  private String label;
  private Map<String, Object> extraProps = new LinkedHashMap<>();

  public SelectOption() {
  }

  public SelectOption(String value, String label) {
    this.value = value;
    this.label = label;
  }

  public String getValue() {
    return value;
  }

  public SelectOption setValue(String value) {
    this.value = value;
    return this;
  }

  public String getLabel() {
    return label;
  }

  public SelectOption setLabel(String label) {
    this.label = label;
    return this;
  }

  @JsonAnyGetter
  public Map<String, Object> getExtraProps() {
    return extraProps;
  }

  public SelectOption setExtraProps(Map<String, Object> extraProps) {
    this.extraProps = extraProps;
    return this;
  }

  @JsonAnySetter
  public SelectOption addExtraProp(String key, Object value) {
    this.extraProps.put(key, value);
    return this;
  }

  public SelectOption removeExtraProp(String key) {
    this.extraProps.remove(key);
    return this;
  }
}
