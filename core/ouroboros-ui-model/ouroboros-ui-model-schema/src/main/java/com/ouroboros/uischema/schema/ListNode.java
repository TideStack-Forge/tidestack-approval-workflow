package com.ouroboros.uischema.schema;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.ouroboros.uischema.schema.decorator.*;

@SuppressWarnings("unused")
public class ListNode extends SchemaNode implements WithTitle, WithHeaderClassName, WithFooterClassName,
    WithSourceExpr, WithPlaceholder {

  public ListNode() {
    super();
    setType("list");
  }

  public ListNode(SchemaNode node) {
    super(node);
    setType("list");
  }

  public ListNode(Map<String, ?> props) {
    super(props);
    setType("list");
  }

  public ListItemConfig getListItem() {
    return getProp(ListItemConfig.class, "listItem", null);
  }

  @JsonSetter
  public void setListItem(ListItemConfig listItem) {
    setProp("listItem", listItem);
  }
}
