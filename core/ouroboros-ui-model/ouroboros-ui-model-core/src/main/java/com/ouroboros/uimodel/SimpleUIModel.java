package com.ouroboros.uimodel;

import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import org.apache.commons.lang3.ObjectUtils;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;

public class SimpleUIModel implements UIModel {
  private final Map<String, Object> extraProps = new TreeMap<>(String::compareToIgnoreCase);
  private String icon;
  private String title;
  private String path;
  private String type;

  @Override
  public String getIcon() {
    return icon;
  }

  public void setIcon(String icon) {
    this.icon = icon;
  }

  @Override
  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  @Override
  public String getPath() {
    return path != null
        ? path.startsWith("/") ? path : "/" + path
        : null;
  }

  public void setPath(String path) {
    this.path = path;
  }

  @Override
  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  @JsonAnyGetter
  @Override
  public Map<String, Object> getExtraProps() {
    return extraProps;
  }

  @Override
  public Optional<Object> getExtraProp(String key) {
    return Optional.ofNullable(extraProps.get(key)).filter(ObjectUtils::isNotEmpty);
  }

  @Override
  public <T> Optional<T> getExtraProp(Class<T> clazz, String key) {
    return getExtraProp(key).flatMap(value -> {
      if (clazz.isInstance(value)) {
        return Optional.of(clazz.cast(value));
      }
      return Optional.empty();
    });
  }

  @JsonAnySetter
  public void setExtraProp(String key, Object value) {
    extraProps.put(key, value);
  }
}
