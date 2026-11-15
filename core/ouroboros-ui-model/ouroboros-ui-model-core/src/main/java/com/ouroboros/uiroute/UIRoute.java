package com.ouroboros.uiroute;

import java.io.Serializable;
import java.util.Map;

/**
 * @author liansz
 */
public class UIRoute implements Serializable {

  private static final long serialVersionUID = 6589954442233375234L;

  /**
   * 路由名称
   */
  private String name;

  /**
   * 路由路径
   */
  private String path;

  /**
   * 路由类型
   * 枚举：RouteTypeEnum
   */
  private String routeType;

  /**
   * 扩展属性
   */
  private Map<String, Object> config;

  public static UIRoute of(String name, String path) {
    UIRoute uiRoute = new UIRoute();
    uiRoute.setName(name);
    uiRoute.setPath(path);
    return uiRoute;
  }

  public static UIRoute of(String name, String path, String routeType) {
    UIRoute uiRoute = new UIRoute();
    uiRoute.setName(name);
    uiRoute.setPath(path);
    uiRoute.setRouteType(routeType);
    return uiRoute;
  }

  public static UIRoute of(String name, String path, String routeType, Map<String, Object> config) {
    UIRoute uiRoute = new UIRoute();
    uiRoute.setName(name);
    uiRoute.setPath(path);
    uiRoute.setRouteType(routeType);
    uiRoute.setConfig(config);
    return uiRoute;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getPath() {
    return path;
  }

  public void setPath(String path) {
    this.path = path;
  }

  public String getRouteType() {
    return routeType;
  }

  public void setRouteType(String routeType) {
    this.routeType = routeType;
  }

  public Map<String, Object> getConfig() {
    return config;
  }

  public void setConfig(Map<String, Object> config) {
    this.config = config;
  }
}
