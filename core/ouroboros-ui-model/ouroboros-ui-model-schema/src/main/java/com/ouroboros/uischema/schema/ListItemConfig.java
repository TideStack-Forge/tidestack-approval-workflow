package com.ouroboros.uischema.schema;

import java.util.List;
import java.util.Map;

import com.ouroboros.uischema.schema.decorator.WithActions;
import com.ouroboros.uischema.schema.decorator.WithTitle;

@SuppressWarnings("unused")
public class ListItemConfig extends AbstractSchema implements WithActions, WithTitle {

  public ListItemConfig() {
    super();
  }

  public ListItemConfig(Map<String, ?> props) {
    super(props);
  }

  public Object getTitleClassName() {
    return getProp("titleClassName");
  }

  public void setTitleClassName(String titleClassName) {
    setProp("titleClassName", titleClassName);
  }

  public void setTitleClassName(Map<String, Object> titleClassName) {
    setProp("titleClassName", titleClassName);
  }

  public String getSubTitle() {
    return getProp(String.class, "subTitle", null);
  }

  public void setSubTitle(String subTitle) {
    setProp("subTitle", subTitle);
  }

  public String getAvatar() {
    return getProp(String.class, "avatar", null);
  }

  public void setAvatar(String avatar) {
    setProp("avatar", avatar);
  }

  public Object getAvatarClassName() {
    return getProp("avatarClassName");
  }

  public void setAvatarClassName(String avatarClassName) {
    setProp("avatarClassName", avatarClassName);
  }

  public void setAvatarClassName(Map<String, Object> avatarClassName) {
    setProp("avatarClassName", avatarClassName);
  }

  public String getDesc() {
    return getProp(String.class, "desc", null);
  }

  public void setDesc(String desc) {
    setProp("desc", desc);
  }

  public List<Object> getBody() {
    return getProp(List.class, "body", null);
  }

  public void setBody(List<Object> body) {
    setProp("body", body);
  }

  public String getActionsPosition() {
    return getProp(String.class, "actionsPosition", null);
  }

  public void setActionsPosition(String actionsPosition) {
    setProp("actionsPosition", actionsPosition);
  }
}
