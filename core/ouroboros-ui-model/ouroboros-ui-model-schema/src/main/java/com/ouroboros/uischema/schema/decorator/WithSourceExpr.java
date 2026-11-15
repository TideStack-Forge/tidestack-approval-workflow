package com.ouroboros.uischema.schema.decorator;

import com.ouroboros.uischema.schema.Schema;

public interface WithSourceExpr extends Schema {
  default String getSource() {
    return getProp(String.class, "source", null);
  }

  default void setSource(String source) {
    setProp("source", source);
  }
}
