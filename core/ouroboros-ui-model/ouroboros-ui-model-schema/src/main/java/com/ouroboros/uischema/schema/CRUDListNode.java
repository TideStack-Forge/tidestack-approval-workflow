package com.ouroboros.uischema.schema;

import java.util.Map;

import com.ouroboros.uischema.schema.decorator.*;

public class CRUDListNode extends ListNode implements WithTitle, WithAPI, WithAutoRefresh,
    WithMessages, WithCRUD {
  public CRUDListNode() {
    super();
    setType("crud");
    setMode("list");
  }

  public CRUDListNode(SchemaNode node) {
    super(node);
  }

  public CRUDListNode(Map<String, ?> props) {
    super(props);
  }
}
