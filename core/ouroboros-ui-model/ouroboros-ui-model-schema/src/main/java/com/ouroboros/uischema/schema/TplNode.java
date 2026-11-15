package com.ouroboros.uischema.schema;

public class TplNode extends SchemaNode {
  public TplNode() {
    super();
    setType("tpl");
  }

  public TplNode(SchemaNode node) {
    super(node);
  }

  public TplNode(String tpl) {
    super();
    setType("tpl");
    setProp("tpl", tpl);
  }
}
