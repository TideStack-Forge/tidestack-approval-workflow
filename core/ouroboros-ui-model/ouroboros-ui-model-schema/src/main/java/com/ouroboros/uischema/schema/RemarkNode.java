package com.ouroboros.uischema.schema;

import java.util.Map;

@SuppressWarnings("unused")
public class RemarkNode extends SchemaNode {
  public RemarkNode() {
    super();
    setType("remark");
  }

  public RemarkNode(SchemaNode node) {
    super(node);
  }

  public RemarkNode(Map<String, ?> props) {
    super(props);
  }

  public String getContent() {
    return getProp(String.class, "content", null);
  }

  public void setContent(String content) {
    setProp("content", content);
  }

  public String getPlacement() {
    return getProp(String.class, "placement", null);
  }

  public void setPlacement(String placement) {
    setProp("placement", placement);
  }

  public String getTrigger() {
    return getProp(String.class, "trigger", null);
  }

  public void setTrigger(String trigger) {
    setProp("trigger", trigger);
  }

  public String getIcon() {
    return getProp(String.class, "icon", null);
  }

  public void setIcon(String icon) {
    setProp("icon", icon);
  }

  public String getShape() {
    return getProp(String.class, "shape", null);
  }

  public void setShape(String shape) {
    setProp("shape", shape);
  }
}
