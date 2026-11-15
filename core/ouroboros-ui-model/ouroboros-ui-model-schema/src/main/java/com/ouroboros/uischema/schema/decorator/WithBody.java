package com.ouroboros.uischema.schema.decorator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.ouroboros.uischema.schema.Schema;
import com.ouroboros.uischema.schema.SchemaNode;
import com.ouroboros.uischema.schema.TplNode;

public interface WithBody extends Schema {
  default List<SchemaNode> getBody() {
    return getProp(List.class, "body", null);
  }

  default void setBody(Collection<?> body) {
    var bodyList = new ArrayList<SchemaNode>();
    for (var item : body) {
      if (item == null) {
        continue;
      }
      if (item instanceof SchemaNode) {
        bodyList.add((SchemaNode) item);
      } else if (item instanceof Map mapItem) {
        var node = new SchemaNode(mapItem);
        bodyList.add(node);
      } else {
        bodyList.add(new TplNode(item.toString()));
      }
    }
    setProp("body", body);
  }

  default void setBody(SchemaNode body) {
    var list = new ArrayList<SchemaNode>();
    list.add(body);
    setProp("body", list);
  }

  default void setBody(String body) {
    var tplNode = new TplNode(body);
    setBody(tplNode);
  }

  @JsonDeserialize(using = BodyDeserializer.class)
  @JsonSetter
  default void setBody(Object body) {
    if (body instanceof String tplString) {
      setBody(tplString);
    } else if (body instanceof SchemaNode schemaNode) {
      setBody(schemaNode);
    } else if (body instanceof Map mapBody) {
      var node = new SchemaNode(mapBody);
      setBody(node);
    } else if (body instanceof Collection listBody) {
      setBody(listBody);
    } else {
      setProp("body", null);
    }
  }

  default void addBody(SchemaNode node) {
    var body = getBody();
    if (body == null) {
      body = new ArrayList<SchemaNode>();
      setBody(body);
    }
    body.add(node);
  }
}
