package com.ouroboros.uimodel;

import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonIgnore;

@SuppressWarnings("unused")
public interface UIModel {
  /**
   * UI标题（用于菜单、面包屑、标签页等展示场景）
   */
  String getTitle();

  /**
   * UI的唯一路径（用于路由和分层覆盖机制）
   */
  String getPath();

  /**
   * UI的渲染类型（如 "amis", "micro", "qb", "internal" 等）
   */
  String getType();

  /**
   * UI图标（用于菜单、卡片等展示场景）
   */
  String getIcon();

  /**
   * UI的详细描述
   */
  @JsonIgnore
  default Optional<String> getDescription() {
    return getExtraProp(String.class, "description");
  }

  /**
   * 扩展属性集合
   */
  Map<String, Object> getExtraProps();

  /**
   * 获取指定扩展属性
   */
  Optional<Object> getExtraProp(String key);

  /**
   * 获取指定类型的扩展属性（类型安全）
   */
  <T> Optional<T> getExtraProp(Class<T> clazz, String key);
}
