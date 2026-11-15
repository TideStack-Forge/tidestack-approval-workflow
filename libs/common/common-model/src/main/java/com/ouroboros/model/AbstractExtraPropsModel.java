package com.ouroboros.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonAnySetter;

public abstract class AbstractExtraPropsModel<M extends AbstractExtraPropsModel<M, B>, B extends AbstractExtraPropsModel.Builder<M, B>> extends AbstractModel<M, B> implements WithExtraProps {
  protected Map<String, Object> extraProps = new LinkedHashMap<>();

  @Override
  final public Map<String, Object> getExtraProps() {
    return Collections.unmodifiableMap(extraProps);
  }

  @Override
  final public Optional<Object> getExtraProp(String key) {
    return Optional.ofNullable(extraProps.get(key));
  }

  @JsonAnySetter
  final protected void setExtraProp(String key, Object value) {
    extraProps.put(key, value);
  }

  @Override
  public M clone() {
    var cloned = super.clone();
    cloned.extraProps = new LinkedHashMap<>(extraProps);
    return cloned;
  }

  public abstract static class Builder<M extends AbstractExtraPropsModel<M, ? extends Builder<M, B>>, B extends Builder<M, B>> extends AbstractModel.Builder<M> {
    public final B extraProps(Map<String, Object> props) {
      model.extraProps = new LinkedHashMap<>(props);
      return (B) this;
    }

    public final B extraProp(String key, Object value) {
      model.setExtraProp(key, value);
      return (B) this;
    }
  }

}