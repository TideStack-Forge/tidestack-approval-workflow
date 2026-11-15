package com.ouroboros.uischema.schema;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.NONE, getterVisibility = JsonAutoDetect.Visibility.NONE, setterVisibility = JsonAutoDetect.Visibility.NONE)
public abstract class AbstractSchema implements Schema, java.io.Serializable {
  private static final long serialVersionUID = 1L;
  protected Map<String, Object> props;

  public AbstractSchema() {
    props = new LinkedHashMap<>();
  }

  @SuppressWarnings("unchecked")
  public AbstractSchema(Map<String, ?> props) {
    this.props = (Map<String, Object>) props;
  }

  @Override
  @JsonAnySetter
  public void setProp(String name, Object value) {
    props.put(name, value);
  }

  @Override
  @JsonAnyGetter
  public Map<String, Object> getProps() {
    return props;
  }

  @Override
  public Object getProp(String name) {
    return props.get(name);
  }

  @Override
  public <T> T getProp(Class<T> clazz, String name, T defaultValue) {
    return Optional.ofNullable(props.get(name))
        .map(v -> clazz.isInstance(v) ? clazz.cast(v) : null)
        .orElse(defaultValue);
  }

  @Override
  public <T> Optional<T> getProp(Class<T> clazz, String name) {
    return Optional.ofNullable(props.get(name))
        .map(v -> clazz.isInstance(v) ? clazz.cast(v) : null);
  }

  @Override
  public void putProps(Map<String, Object> props) {
    this.props.putAll(props);
  }
}
