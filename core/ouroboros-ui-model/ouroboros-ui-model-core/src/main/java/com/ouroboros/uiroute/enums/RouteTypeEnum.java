package com.ouroboros.uiroute.enums;

import java.util.Arrays;

import com.ouroboros.exception.PlatformException;

/**
 * @author liansz
 */
public enum RouteTypeEnum {

  /**
   * 主应用内部路由
   */
  INTERNAL("internal"),

  /**
   * 微应用路由
   */
  MICRO("micro"),

  /**
   * amis应用路由
   */
  AMIS("amis"),

  /**
   * quickBuilder应用路由
   */
  QUICK("quick");

  private final String value;

  RouteTypeEnum(String value) {
    this.value = value;
  }

  public static RouteTypeEnum fromValue(String value) {
    return Arrays.stream(RouteTypeEnum.values())
        .filter(v -> v.value().equals(value))
        .findFirst()
        .orElseThrow(() -> new PlatformException("Invalid value: " + value));
  }

  public String value() {
    return value;
  }
}
