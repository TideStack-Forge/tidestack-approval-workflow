package com.ouroboros.uischema.schema.decorator;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.ouroboros.uischema.schema.ActionNode;
import com.ouroboros.uischema.schema.Schema;

public interface WithItemAction extends Schema {
  default ActionNode getItemAction() {
    return getProp(ActionNode.class, "itemAction", null);
  }

  @JsonSetter
  default void setItemAction(ActionNode itemAction) {
    setProp("itemAction", itemAction);
  }
}
