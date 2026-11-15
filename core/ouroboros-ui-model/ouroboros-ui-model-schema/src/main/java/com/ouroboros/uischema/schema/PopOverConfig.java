package com.ouroboros.uischema.schema;

import com.fasterxml.jackson.annotation.JsonSetter;

public class PopOverConfig extends AbstractSchema {
  public String getMode() {
    return getProp(String.class, "mode", null);
  }

  public void setMode(String mode) {
    setProp("mode", mode);
  }

  public String getTitle() {
    return getProp(String.class, "title", null);
  }

  public void setTitle(String title) {
    setProp("title", title);
  }

  public String getSize() {
    return getProp(String.class, "size", null);
  }

  public void setSize(String size) {
    setProp("size", size);
  }

  public String getPosition() {
    return getProp(String.class, "position", null);
  }

  public void setPosition(String position) {
    setProp("position", position);
  }

  public Offset getOffset() {
    return getProp(Offset.class, "offset", null);
  }

  @JsonSetter
  public void setOffset(Offset offset) {
    setProp("offset", offset);
  }

  public String getTrigger() {
    return getProp(String.class, "trigger", null);
  }

  public void setTrigger(String trigger) {
    setProp("trigger", trigger);
  }

  public Boolean getShowIcon() {
    return getProp(Boolean.class, "showIcon", null);
  }

  public void setShowIcon(Boolean showIcon) {
    setProp("showIcon", showIcon);
  }

  public SchemaNode getBody() {
    return getProp(SchemaNode.class, "body", null);
  }

  @JsonSetter
  public void setBody(SchemaNode body) {
    setProp("body", body);
  }

  public static class Offset {
    private Integer top;
    private Integer left;
    private Integer right;
    private Integer bottom;

    public Integer getTop() {
      return top;
    }

    public void setTop(Integer top) {
      this.top = top;
    }

    public Integer getLeft() {
      return left;
    }

    public void setLeft(Integer left) {
      this.left = left;
    }

    public Integer getRight() {
      return right;
    }

    public void setRight(Integer right) {
      this.right = right;
    }

    public Integer getBottom() {
      return bottom;
    }

    public void setBottom(Integer bottom) {
      this.bottom = bottom;
    }
  }
}
