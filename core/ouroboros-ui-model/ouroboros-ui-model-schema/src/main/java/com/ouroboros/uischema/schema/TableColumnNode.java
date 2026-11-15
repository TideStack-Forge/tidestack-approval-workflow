package com.ouroboros.uischema.schema;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.ouroboros.uischema.schema.decorator.WithLabel;
import com.ouroboros.uischema.schema.decorator.WithPopOver;
import com.ouroboros.uischema.schema.decorator.WithRemark;
import com.ouroboros.uischema.schema.decorator.WithStyle;

@SuppressWarnings("unused")
public class TableColumnNode extends SchemaNode implements WithStyle, WithPopOver, WithRemark, WithLabel {
  public TableColumnNode() {
    super();
  }

  public TableColumnNode(SchemaNode node) {
    super(node);
  }

  public TableColumnNode(Map<String, ?> props) {
    super(props);
  }

  public Object getWidth() {
    return getProp("width");
  }

  public void setWidth(Object width) {
    setProp("width", width);
  }

  public void setWidth(String width) {
    setProp("width", width);
  }

  public void setWidth(Integer width) {
    setProp("width", width);
  }

  public String getFixed() {
    return getProp(String.class, "fixed", null);
  }

  public void setFixed(String fixed) {
    setProp("fixed", fixed);
  }

  public Object getCopyable() {
    var copyable = getProp("copyable");
    if (copyable instanceof Boolean) {
      return copyable;
    } else if (copyable instanceof Map<?, ?> copyableMap) {
      var copyableObj = new Copyable() {
        {
          setIcon((String) copyableMap.get("icon"));
          setContent((String) copyableMap.get("content"));
        }
      };
      setProp("copyable", copyableObj);
      return copyableObj;
    }
    return null;
  }

  public void setCopyable(Boolean copyable) {
    setProp("copyable", copyable);
  }

  public void setCopyable(Copyable copyable) {
    setProp("copyable", copyable);
  }

  public StyleConfig getInnerStyle() {
    return getProp(StyleConfig.class, "innerStyle", null);
  }

  @JsonSetter
  public void setInnerStyle(StyleConfig innerStyle) {
    setProp("innerStyle", innerStyle);
  }

  public static class Copyable {
    private String icon;
    private String content;

    public String getIcon() {
      return icon;
    }

    public void setIcon(String icon) {
      this.icon = icon;
    }

    public String getContent() {
      return content;
    }

    public void setContent(String content) {
      this.content = content;
    }
  }
}
