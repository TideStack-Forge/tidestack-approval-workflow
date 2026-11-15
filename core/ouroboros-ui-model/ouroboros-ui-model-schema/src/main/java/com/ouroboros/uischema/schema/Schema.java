package com.ouroboros.uischema.schema;

import java.util.Map;
import java.util.Optional;

public interface Schema {
  Map<String, Object> getProps();

  void setProp(String name, Object value);

  Object getProp(String name);

  <T> T getProp(Class<T> clazz, String name, T defaultValue);

  <T> Optional<T> getProp(Class<T> clazz, String name);

  void putProps(Map<String, Object> props);
}
