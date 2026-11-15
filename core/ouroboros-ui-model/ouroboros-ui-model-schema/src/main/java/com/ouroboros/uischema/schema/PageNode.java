package com.ouroboros.uischema.schema;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.ouroboros.uischema.schema.decorator.*;

@SuppressWarnings("unused")
public class PageNode extends SchemaNode implements WithBody, WithRemark, WithTitle,
    WithHeaderClassName, WithBodyClassName, WithInitAPI, WithAutoRefresh {
  public PageNode() {
    super();
    setType("page");
  }

  public PageNode(SchemaNode node) {
    super(node);
    setBody(node.getProp("body"));
  }

  public PageNode(Map<String, ?> props) {
    super(props);
    setBody(props.get("body"));
  }

  public String getSubTitle() {
    return getProp(String.class, "subTitle", null);
  }

  public void setSubTitle(String subTitle) {
    setProp("subTitle", subTitle);
  }

  public SchemaNode getAside() {
    return getProp(SchemaNode.class, "aside", null);
  }

  @JsonSetter
  public void setAside(SchemaNode aside) {
    setProp("aside", aside);
  }

  public Boolean getAsideResizor() {
    return getProp(Boolean.class, "asideResizor", null);
  }

  public void setAsideResizor(Boolean asideResizor) {
    setProp("asideResizor", asideResizor);
  }

  public Integer getAsideMinWidth() {
    return getProp(Integer.class, "asideMinWidth", null);
  }

  public void setAsideMinWidth(Integer asideMinWidth) {
    setProp("asideMinWidth", asideMinWidth);
  }

  public Integer getAsideMaxWidth() {
    return getProp(Integer.class, "asideMaxWidth", null);
  }

  public void setAsideMaxWidth(Integer asideMaxWidth) {
    setProp("asideMaxWidth", asideMaxWidth);
  }

  public Boolean getAsideSticky() {
    return getProp(Boolean.class, "asideSticky", null);
  }

  public void setAsideSticky(Boolean asideSticky) {
    setProp("asideSticky", asideSticky);
  }

  public List<SchemaNode> getToolbar() {
    return getProp(List.class, "toolbar", null);
  }

  @JsonSetter
  public void setToolbar(List<SchemaNode> toolbar) {
    setProp("toolbar", toolbar);
  }

  public CssVars getCssVars() {
    return getProp(CssVars.class, "cssVars", null);
  }

  public void setCssVars(Map<String, Object> cssVars) {
    setProp("cssVars", new CssVars(cssVars));
  }

  @JsonSetter
  public void setCssVars(CssVars cssVars) {
    setProp("cssVars", cssVars);
  }

  public CssConfig getCss() {
    return getProp(CssConfig.class, "css", null);
  }

  @JsonSetter
  public void setCss(CssConfig css) {
    setProp("css", css);
  }

  public Object getToolbarClassName() {
    return getProp("toolbarClassName");
  }

  public void setToolbarClassName(String toolbarClassName) {
    setProp("toolbarClassName", toolbarClassName);
  }

  public void setToolbarClassName(Map<String, Object> toolbarClassName) {
    setProp("toolbarClassName", toolbarClassName);
  }

  public Object getAsideClassName() {
    return getProp("asideClassName");
  }

  public void setAsideClassName(String asideClassName) {
    setProp("asideClassName", asideClassName);
  }

  public void setAsideClassName(Map<String, Object> asideClassName) {
    setProp("asideClassName", asideClassName);
  }

  public PullRefreshConfig getPullRefresh() {
    return getProp(PullRefreshConfig.class, "pullRefresh", null);
  }

  @JsonSetter
  public void setPullRefresh(PullRefreshConfig pullRefresh) {
    setProp("pullRefresh", pullRefresh);
  }

  public static class PullRefreshConfig {
    private Boolean disabled;
    private String pullingText;
    private String loosingText;

    public Boolean getDisabled() {
      return disabled;
    }

    public void setDisabled(Boolean disabled) {
      this.disabled = disabled;
    }

    public String getPullingText() {
      return pullingText;
    }

    public void setPullingText(String pullingText) {
      this.pullingText = pullingText;
    }

    public String getLoosingText() {
      return loosingText;
    }

    public void setLoosingText(String loosingText) {
      this.loosingText = loosingText;
    }
  }

  public static class CssVars {
    final private Map<String, Object> cssVars;

    public CssVars() {
      cssVars = new LinkedHashMap<>();
    }

    public CssVars(Map<String, Object> cssVars) {
      this.cssVars = cssVars;
    }

    @JsonAnyGetter
    public Map<String, Object> getCssVars() {
      return cssVars;
    }

    @JsonAnySetter
    public void setCssVar(String varName, Object value) {
      cssVars.put(varName, value);
    }
  }

  public static class CssConfig {
    final Map<String, Map<String, String>> css;

    public CssConfig() {
      css = new LinkedHashMap<>();
    }

    public CssConfig(Map<String, Map<String, String>> css) {
      this.css = css;
    }

    @JsonAnyGetter
    public Map<String, Map<String, String>> getCss() {
      return css;
    }

    @JsonAnySetter
    public void setCss(String key, Map<String, String> value) {
      css.put(key, value);
    }
  }
}
