package com.ouroboros.uischema.schema;

import java.util.Collection;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.ouroboros.uischema.schema.event.EventActionConfig;
import com.ouroboros.uischema.schema.event.EventActionsConfig;
import com.ouroboros.uischema.schema.event.OnEventConfig;

public class SchemaNode extends AbstractSchema {
  public SchemaNode() {
    super();
  }

  public SchemaNode(SchemaNode node) {
    props = node.props;
  }

  public SchemaNode(Map<String, ?> props) {
    super(props);
  }

  public String getType() {
    return getProp(String.class, "type", null);
  }

  public void setType(String type) {
    setProp("type", type);
  }

  public String getName() {
    return getProp(String.class, "name", null);
  }

  public void setName(String name) {
    setProp("name", name);
  }

  public String getId() {
    return getProp(String.class, "id", null);
  }

  public void setId(String id) {
    setProp("id", id);
  }

  public Object getClassName() {
    return getProp("className");
  }

  public void setClassName(Map<String, String> className) {
    setProp("className", className);
  }

  public void setClassName(String className) {
    setProp("className", className);
  }

  public Boolean getVisible() {
    return getProp(Boolean.class, "visible", true);
  }

  public void setVisible(Boolean visible) {
    setProp("visible", visible);
  }

  public Boolean getHidden() {
    return getProp(Boolean.class, "hidden", false);
  }

  public void setHidden(Boolean hidden) {
    setProp("hidden", hidden);
  }

  public String getVisibleOn() {
    return getProp(String.class, "visibleOn", null);
  }

  public void setVisibleOn(String visibleOn) {
    setProp("visibleOn", visibleOn);
  }

  public String getHiddenOn() {
    return getProp(String.class, "hiddenOn", null);
  }

  public void setHiddenOn(String hiddenOn) {
    setProp("hiddenOn", hiddenOn);
  }

  public OnEventConfig getOnEvent() {
    var onEvent = getProp("onEvent");
    if (onEvent instanceof OnEventConfig onEventConfig) {
      return onEventConfig;
    } else if (onEvent instanceof Map onEventMap) {
      var onEventConfig = new OnEventConfig(onEventMap);
      setOnEvent(onEventConfig);
      return onEventConfig;
    }
    return null;
  }

  @JsonSetter
  public void setOnEvent(Object onEvent) {
    if (onEvent instanceof OnEventConfig onEventConfig) {
      setProp("onEvent", onEventConfig);
    } else if (onEvent instanceof Map onEventMap) {
      setProp("onEvent", new OnEventConfig(onEventMap));
    } else {
      setProp("onEvent", null);
    }
  }

  public void addOnEvent(String name, EventActionsConfig config) {
    var onEvent = getOnEvent();
    if (onEvent == null) {
      onEvent = new OnEventConfig();
      setOnEvent(onEvent);
    }
    onEvent.addOnEvent(name, config);
  }

  public void addOnEvent(String name, EventActionConfig... eventActions) {
    var onEvent = getOnEvent();
    if (onEvent == null) {
      onEvent = new OnEventConfig();
      setOnEvent(onEvent);
    }
    onEvent.addOnEvent(name, eventActions);
  }

  public void addOnEvent(String name, Collection<EventActionConfig> eventActions) {
    var onEvent = getOnEvent();
    if (onEvent == null) {
      onEvent = new OnEventConfig();
      setOnEvent(onEvent);
    }
    onEvent.addOnEvent(name, eventActions);
  }
}
