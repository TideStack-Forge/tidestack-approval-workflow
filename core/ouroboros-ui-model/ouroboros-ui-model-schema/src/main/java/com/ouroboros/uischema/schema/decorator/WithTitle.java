package com.ouroboros.uischema.schema.decorator;

import com.ouroboros.uischema.schema.Schema;

public interface WithTitle extends Schema {
  default String getTitle() {
    return getProp(String.class, "title", null);
  }

  default void setTitle(String title) {
    setProp("title", title);
  }
}
