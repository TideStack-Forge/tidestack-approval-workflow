package com.ouroboros.uischema.schema.decorator;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.ouroboros.uischema.schema.PopOverConfig;
import com.ouroboros.uischema.schema.Schema;

public interface WithPopOver extends Schema {
  default PopOverConfig getPopOver() {
    return getProp(PopOverConfig.class, "popOver", null);
  }

  @JsonSetter
  default void setPopOver(PopOverConfig popOver) {
    setProp("popOver", popOver);
  }
}
