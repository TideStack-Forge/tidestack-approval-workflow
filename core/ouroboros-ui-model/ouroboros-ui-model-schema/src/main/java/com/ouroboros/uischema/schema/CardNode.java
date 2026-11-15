package com.ouroboros.uischema.schema;

import java.util.Map;

import com.ouroboros.uischema.schema.decorator.WithBody;
import com.ouroboros.uischema.schema.decorator.WithBodyClassName;

/**
 * CardNode -- 卡片（未完成）
 */
public class CardNode extends SchemaNode implements WithBody, WithBodyClassName {

  public CardNode() {
    super();
    setType("card");
  }

  public CardNode(Map<String, ?> props) {
    super(props);
    setBody(props.get("body"));
  }

  public CardNode(SchemaNode node) {
    super(node);
    setBody(node.getProp("body"));
  }

  @Override
  public String getType() {
    return "card";
  }

  @Override
  public void setType(String type) {
  }
}
