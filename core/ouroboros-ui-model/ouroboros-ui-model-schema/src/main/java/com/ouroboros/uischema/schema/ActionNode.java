package com.ouroboros.uischema.schema;

import java.util.List;
import java.util.Map;

import com.ouroboros.uischema.schema.decorator.WithLabel;

public class ActionNode extends SchemaNode implements WithLabel {
  public ActionNode() {
    super();
    setType("button");
  }

  public ActionNode(SchemaNode node) {
    super(node);
  }

  public ActionNode(Map<String, ?> props) {
    super(props);
  }

  public String getActionType() {
    return getProp(String.class, "actionType", null);
  }

  public void setActionType(String actionType) {
    setProp("actionType", actionType);
  }

  public String getLevel() {
    return getProp(String.class, "level", null);
  }

  public void setLevel(String level) {
    setProp("level", level);
  }

  public String getSize() {
    return getProp(String.class, "size", null);
  }

  public void setSize(String size) {
    setProp("size", size);
  }

  public String getIcon() {
    return getProp(String.class, "icon", null);
  }

  public void setIcon(String icon) {
    setProp("icon", icon);
  }

  public Object getIconClassName() {
    return getProp("iconClassName");
  }

  public void setIconClassName(String iconClassName) {
    setProp("iconClassName", iconClassName);
  }

  public void setIconClassName(Map<String, Object> iconClassName) {
    setProp("iconClassName", iconClassName);
  }

  public String getRightIcon() {
    return getProp(String.class, "rightIcon", null);
  }

  public void setRightIcon(String rightIcon) {
    setProp("rightIcon", rightIcon);
  }

  public Object getRightIconClassName() {
    return getProp("rightIconClassName");
  }

  public void setRightIconClassName(String rightIconClassName) {
    setProp("rightIconClassName", rightIconClassName);
  }

  public void setRightIconClassName(Map<String, Object> rightIconClassName) {
    setProp("rightIconClassName", rightIconClassName);
  }

  public Boolean getActive() {
    return getProp(Boolean.class, "active", null);
  }

  public void setActive(Boolean active) {
    setProp("active", active);
  }

  public String getActiveLevel() {
    return getProp(String.class, "activeLevel", null);
  }

  public void setActiveLevel(String activeLevel) {
    setProp("activeLevel", activeLevel);
  }

  public Object getActiveClassName() {
    return getProp("activeClassName");
  }

  public void setActiveClassName(String activeClassName) {
    setProp("activeClassName", activeClassName);
  }

  public void setActiveClassName(Map<String, Object> activeClassName) {
    setProp("activeClassName", activeClassName);
  }

  public Boolean getBlock() {
    return getProp(Boolean.class, "block", null);
  }

  public void setBlock(Boolean block) {
    setProp("block", block);
  }

  public String getConfirmText() {
    return getProp(String.class, "confirmText", null);
  }

  public void setConfirmText(String confirmText) {
    setProp("confirmText", confirmText);
  }

  public String getConfirmTitle() {
    return getProp(String.class, "confirmTitle", null);
  }

  public void setConfirmTitle(String confirmTitle) {
    setProp("confirmTitle", confirmTitle);
  }

  public String getReload() {
    return getProp(String.class, "reload", null);
  }

  public void setReload(String reload) {
    setProp("reload", reload);
  }

  public String getTooltip() {
    return getProp(String.class, "tooltip", null);
  }

  public void setTooltip(String tooltip) {
    setProp("tooltip", tooltip);
  }

  public String getDisabledTip() {
    return getProp(String.class, "disabledTip", null);
  }

  public void setDisabledTip(String disabledTip) {
    setProp("disabledTip", disabledTip);
  }

  public String getTooltipPlacement() {
    return getProp(String.class, "tooltipPlacement", null);
  }

  public void setTooltipPlacement(String tooltipPlacement) {
    setProp("tooltipPlacement", tooltipPlacement);
  }

  public Object getClose() {
    return getProp("close");
  }

  public void setClose(Boolean close) {
    setProp("close", close);
  }

  public void setClose(String target) {
    setProp("close", target);
  }

  public List<String> getRequired() {
    return getProp(List.class, "required", null);
  }

  public void setRequired(List<String> required) {
    setProp("required", required);
  }
}
