package com.ouroboros.uischema.schema;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.ouroboros.uischema.schema.decorator.*;

public class CardsNode extends SchemaNode implements WithTitle, WithSourceExpr, WithPlaceholder,
    WithHeaderClassName, WithFooterClassName {

  public CardsNode() {
    super();
    setType("cards");
  }

  public CardsNode(SchemaNode node) {
    super(node);
  }

  public CardsNode(Map<String, ?> props) {
    super(props);
  }

  public Object getItemClassName() {
    return getProp("itemClassName");
  }

  public void setItemClassName(String itemClassName) {
    setProp("itemClassName", itemClassName);
  }

  public void setItemClassName(Map<String, Object> itemClassName) {
    setProp("itemClassName", itemClassName);
  }

  public CardNode getCard() {
    return getProp(CardNode.class, "card", null);
  }

  @JsonSetter
  public void setCard(CardNode card) {
    setProp("card", card);
  }

  public Boolean getSelectable() {
    return getProp(Boolean.class, "selectable", null);
  }

  public void setSelectable(Boolean selectable) {
    setProp("selectable", selectable);
  }

  public Boolean getMultiple() {
    return getProp(Boolean.class, "multiple", null);
  }

  public void setMultiple(Boolean multiple) {
    setProp("multiple", multiple);
  }

  public Boolean getCheckOnItemClick() {
    return getProp(Boolean.class, "checkOnItemClick", null);
  }

  public void setCheckOnItemClick(Boolean checkOnItemClick) {
    setProp("checkOnItemClick", checkOnItemClick);
  }
}
