package com.ouroboros.uischema.schema.decorator;

import com.ouroboros.uischema.schema.Schema;

public interface WithLabel extends Schema {
  default String getLabel() {
    return getProp(String.class, "label", null);
  }

  default void setLabel(String label) {
    setProp("label", label);
  }
}
