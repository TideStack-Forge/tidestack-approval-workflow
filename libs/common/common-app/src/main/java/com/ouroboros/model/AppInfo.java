package com.ouroboros.model;

import org.apache.commons.lang3.ObjectUtils;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AppInfo implements Cloneable {
  private String name = "";
  private String description = "";
  private String title = "";
  private String icon = "";
  private String logo = null;
  private String homePage = "";
  private String defaultTheme = "dark";
  private Custom custom = new Custom();

  public String getName() {
    return name;
  }

  public AppInfo setName(String name) {
    this.name = name;
    return this;
  }

  public String getDescription() {
    return description;
  }

  public AppInfo setDescription(String description) {
    this.description = description;
    return this;
  }

  public String getTitle() {
    return title;
  }

  public AppInfo setTitle(String title) {
    this.title = title;
    return this;
  }

  public String getIcon() {
    return icon;
  }

  public AppInfo setIcon(String icon) {
    this.icon = icon;
    return this;
  }

  public String getLogo() {
    return logo;
  }

  public AppInfo setLogo(String logo) {
    this.logo = logo;
    return this;
  }

  public String getHomePage() {
    return homePage;
  }

  public AppInfo setHomePage(String homePage) {
    this.homePage = homePage;
    return this;
  }

  public String getDefaultTheme() {
    return defaultTheme;
  }

  public AppInfo setDefaultTheme(String defaultTheme) {
    this.defaultTheme = defaultTheme;
    return this;
  }

  public Custom getCustom() {
    return custom;
  }

  public AppInfo setCustom(Custom custom) {
    this.custom = custom;
    return this;
  }

  @Override
  public AppInfo clone() {
    try {
      AppInfo clone = (AppInfo) super.clone();
      return clone;
    } catch (CloneNotSupportedException e) {
      throw new AssertionError();
    }
  }

  public static class Custom {
    private String loginMicroWeb = null;
    private String topBarMicroWeb = null;
    private String sideBarMicroWeb = null;

    public String getLoginMicroWeb() {
      return ObjectUtils.isEmpty(loginMicroWeb) ? null : loginMicroWeb;
    }

    public void setLoginMicroWeb(String loginMicroWeb) {
      this.loginMicroWeb = loginMicroWeb;
    }

    public String getTopBarMicroWeb() {
      return ObjectUtils.isEmpty(topBarMicroWeb) ? null : topBarMicroWeb;
    }

    public void setTopBarMicroWeb(String topBarComponent) {
      this.topBarMicroWeb = topBarComponent;
    }

    public String getSideBarMicroWeb() {
      return ObjectUtils.isEmpty(sideBarMicroWeb) ? null : sideBarMicroWeb;
    }

    public void setSideBarMicroWeb(String sideBarComponent) {
      this.sideBarMicroWeb = sideBarComponent;
    }
  }
}
