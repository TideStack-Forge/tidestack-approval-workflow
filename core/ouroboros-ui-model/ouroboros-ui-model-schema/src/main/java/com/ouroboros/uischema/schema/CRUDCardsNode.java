package com.ouroboros.uischema.schema;

import java.util.Map;

import com.ouroboros.uischema.schema.decorator.*;

public class CRUDCardsNode extends CardsNode implements WithTitle, WithAPI, WithAutoRefresh,
    WithMessages, WithCRUD {

  public CRUDCardsNode() {
    super();
    setType("crud");
    setMode("cards");
  }

  public CRUDCardsNode(SchemaNode node) {
    super(node);
  }

  public CRUDCardsNode(Map<String, ?> props) {
    super(props);
  }
}
