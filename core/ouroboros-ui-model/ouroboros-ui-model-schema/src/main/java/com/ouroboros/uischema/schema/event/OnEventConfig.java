package com.ouroboros.uischema.schema.event;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;

@SuppressWarnings("unused")
public class OnEventConfig {
  Map<String, EventActionsConfig> onEventMap;

  public OnEventConfig() {
    onEventMap = new LinkedHashMap<>();
  }

  public OnEventConfig(Map<String, ?> onEventMap) {
    var eventActionMap = new LinkedHashMap<String, EventActionsConfig>();
    onEventMap.forEach((k, v) -> {
      if (v instanceof EventActionsConfig eventActionsConfig) {
        eventActionMap.put(k, eventActionsConfig);
      } else if (v instanceof Map eventActions) {
        eventActionMap.put(k, new EventActionsConfig((Map<String, ?>) eventActions));
      }
    });
    this.onEventMap = eventActionMap;
  }

  @JsonAnyGetter
  public Map<String, EventActionsConfig> getOnEventMap() {
    return onEventMap;
  }

  @JsonAnySetter
  public void addOnEvent(String name, EventActionsConfig eventActionsConfig) {
    var eventActions = onEventMap.get(name);
    if (eventActions == null) {
      onEventMap.put(name, eventActionsConfig);
      return;
    }
    eventActions.addActions(eventActionsConfig.getActions());
  }

  public void addOnEvent(String name, EventActionConfig... eventActionConfig) {
    addOnEvent(name, new EventActionsConfig(eventActionConfig));
  }

  public void addOnEvent(String name, Collection<EventActionConfig> eventActions) {
    addOnEvent(name, new EventActionsConfig(eventActions));
  }
}
