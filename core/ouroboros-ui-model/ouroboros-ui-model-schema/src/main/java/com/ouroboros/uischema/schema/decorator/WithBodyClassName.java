package com.ouroboros.uischema.schema.decorator;

import java.util.Map;

import com.ouroboros.uischema.schema.Schema;

public interface WithBodyClassName extends Schema {
  default Object getBodyClassName() {
    return getProp("bodyClassName");
  }

  default void setBodyClassName(String bodyClassName) {
    setProp("bodyClassName", bodyClassName);
  }

  default void setBodyClassName(Map<String, Object> bodyClassName) {
    setProp("bodyClassName", bodyClassName);
  }
}
