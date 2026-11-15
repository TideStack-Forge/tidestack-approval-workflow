package com.ouroboros.uischema.schema.decorator;

import java.util.Map;

import com.ouroboros.uischema.schema.Schema;

public interface WithHeaderClassName extends Schema {
  default Object getHeaderClassName() {
    return getProp("headerClassName");
  }

  default void setHeaderClassName(String headerClassName) {
    setProp("headerClassName", headerClassName);
  }

  default void setHeaderClassName(Map<String, Object> headerClassName) {
    setProp("headerClassName", headerClassName);
  }
}
