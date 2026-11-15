package com.ouroboros.uischema.schema.decorator;

import com.ouroboros.uischema.schema.Schema;

public interface WithPlaceholder extends Schema {
  default String getPlaceholder() {
    return getProp(String.class, "placeholder", null);
  }

  default void setPlaceholder(String placeholder) {
    setProp("placeholder", placeholder);
  }
}
