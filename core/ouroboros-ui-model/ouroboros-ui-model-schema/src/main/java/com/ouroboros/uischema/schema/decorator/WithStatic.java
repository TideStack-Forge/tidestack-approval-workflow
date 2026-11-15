package com.ouroboros.uischema.schema.decorator;

import java.util.Map;

import com.ouroboros.uischema.schema.Schema;

public interface WithStatic extends Schema {
  default Boolean getStatic() {
    return getProp(Boolean.class, "static", null);
  }

  default void setStatic(Boolean isStatic) {
    setProp("static", isStatic);
  }

  default Object getStaticClassName() {
    return getProp("staticClassName");
  }

  default void setStaticClassName(String staticClassName) {
    setProp("staticClassName", staticClassName);
  }

  default void setStaticClassName(Map<String, Object> staticClassName) {
    setProp("staticClassName", staticClassName);
  }
}
