package com.ouroboros.uischema.schema.decorator;

import java.util.Map;

import com.ouroboros.uischema.schema.Schema;

public interface WithFooterClassName extends Schema {
  default Object getFooterClassName() {
    return getProp("footerClassName");
  }

  default void setFooterClassName(String footerClassName) {
    setProp("footerClassName", footerClassName);
  }

  default void setFooterClassName(Map<String, Object> footerClassName) {
    setProp("footerClassName", footerClassName);
  }
}
