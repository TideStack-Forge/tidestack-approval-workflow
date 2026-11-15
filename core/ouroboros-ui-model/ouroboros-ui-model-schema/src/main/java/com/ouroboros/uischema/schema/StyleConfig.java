package com.ouroboros.uischema.schema;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;

@SuppressWarnings("unused")
public class StyleConfig {
  Map<String, Object> style;

  @JsonAnyGetter
  public Map<String, Object> getStyle() {
    return style;
  }

  @JsonAnySetter
  public void setStyle(String name, Object value) {
    if (style == null) {
      style = new java.util.LinkedHashMap<>();
    }
    style.put(name, value);
  }
}
