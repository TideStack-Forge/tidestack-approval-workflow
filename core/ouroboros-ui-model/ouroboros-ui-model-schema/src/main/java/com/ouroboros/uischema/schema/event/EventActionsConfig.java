package com.ouroboros.uischema.schema.event;

import java.util.*;

import org.apache.commons.lang3.ObjectUtils;

import com.ouroboros.uischema.schema.AbstractSchema;

public class EventActionsConfig extends AbstractSchema {
  private List<EventActionConfig> actions = new ArrayList<>();

  public EventActionsConfig() {
    super();
    setProp("actions", actions);
  }

  public EventActionsConfig(Map<String, ?> props) {
    this();
    if (ObjectUtils.isEmpty(props)) {
      return;
    }
    var actions = props.get("actions");
    if (actions instanceof Collection actionColl) {
      actionColl.forEach(action -> {
        if (action instanceof EventActionConfig) {
          this.actions.add((EventActionConfig) action);
        } else if (action instanceof Map) {
          this.actions.add(new EventActionConfig((Map<String, ?>) action));
        }
      });
    }
  }

  public EventActionsConfig(EventActionConfig... actions) {
    this();
    Collections.addAll(this.actions, actions);
  }

  public EventActionsConfig(Collection<EventActionConfig> actions) {
    this();
    this.actions.addAll(actions);
  }

  public List<EventActionConfig> getActions() {
    return actions;
  }

  public EventActionsConfig setActions(List<EventActionConfig> actions) {
    this.actions = actions;
    return this;
  }

  public EventActionsConfig addAction(EventActionConfig action) {
    this.actions.add(action);
    return this;
  }

  public EventActionsConfig addActions(Collection<EventActionConfig> actions) {
    this.actions.addAll(actions);
    return this;
  }
}
