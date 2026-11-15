package com.ouroboros.uischema.schema.decorator;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.ouroboros.uischema.schema.ActionNode;
import com.ouroboros.uischema.schema.Schema;

public interface WithActions extends Schema {
  default List<ActionNode> getActions() {
    return getProp(List.class, "actions", null);
  }

  @JsonSetter
  default void setActions(List<ActionNode> actions) {
    setProp("actions", actions);
  }
}
