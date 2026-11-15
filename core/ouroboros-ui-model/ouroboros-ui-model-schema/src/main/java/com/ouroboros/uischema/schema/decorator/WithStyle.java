package com.ouroboros.uischema.schema.decorator;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.ouroboros.uischema.schema.Schema;
import com.ouroboros.uischema.schema.StyleConfig;

public interface WithStyle extends Schema {

  default StyleConfig getStyle() {
    return getProp(StyleConfig.class, "style", null);
  }

  @JsonSetter
  default void setStyle(StyleConfig style) {
    setProp("style", style);
  }
}
