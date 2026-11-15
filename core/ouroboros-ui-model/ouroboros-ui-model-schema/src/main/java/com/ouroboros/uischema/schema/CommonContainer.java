package com.ouroboros.uischema.schema;

import java.util.Map;

import com.ouroboros.uischema.schema.decorator.WithBody;

public class CommonContainer extends SchemaNode implements WithBody {
  public CommonContainer() {
    super();
  }

  public CommonContainer(SchemaNode node) {
    super(node);
    setBody(node.getProp("body"));
  }

  public CommonContainer(Map<String, ?> props) {
    super(props);
    setBody(props.get("body"));
  }
}