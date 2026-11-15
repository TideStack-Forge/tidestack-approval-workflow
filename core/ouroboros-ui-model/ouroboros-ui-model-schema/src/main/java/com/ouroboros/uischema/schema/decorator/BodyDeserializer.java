package com.ouroboros.uischema.schema.decorator;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.ouroboros.uischema.schema.SchemaNode;
import com.ouroboros.uischema.schema.TplNode;

public class BodyDeserializer extends JsonDeserializer<Object> {
  @Override
  public Object deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException, JacksonException {
    var currentToken = jsonParser.currentToken();
    switch (currentToken) {
      case START_OBJECT:
        var schemaNode = jsonParser.readValueAs(SchemaNode.class);
        var body = new ArrayList<>();
        body.add(schemaNode);
        return body;
      case VALUE_STRING:
        var tpl = new TplNode(jsonParser.getValueAsString());
        var body2 = new ArrayList<>();
        body2.add(tpl);
        return body2;
      case START_ARRAY:
        var list = jsonParser.readValueAs(List.class);
        return list.stream()
            .map(item -> item instanceof Map map ? new SchemaNode(map) : item)
            .collect(Collectors.toList());
      default:
        throw null;
    }
  }
}
