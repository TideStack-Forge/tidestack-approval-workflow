package com.ouroboros.uischema;

import java.util.Map;
import java.util.Optional;

@SuppressWarnings("unused")
public interface UISchemaProvider {
  Optional<Object> getUISchema(String path);

  default Optional<Object> getUISchema(String path, Map<String, Object> context) {
    return getUISchema(path);
  }

  Map<String, Object> getUISchemaMap();
}
