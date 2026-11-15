package com.ouroboros.model;

import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonAnyGetter;

public interface WithExtraProps {
  @JsonAnyGetter
  Map<String, Object> getExtraProps();

  Optional<Object> getExtraProp(String key);

  default <T> Optional<T> getExtraProp(Class<T> clazz, String key) {
    var value = getExtraProp(key);
    return value.map(v -> {
      if (clazz.isInstance(v)) {
        return clazz.cast(v);
      }
      return null;
    });
  }
}
