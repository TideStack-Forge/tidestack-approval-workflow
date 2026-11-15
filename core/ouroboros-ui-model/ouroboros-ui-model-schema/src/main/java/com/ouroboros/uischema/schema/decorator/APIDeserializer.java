package com.ouroboros.uischema.schema.decorator;

import java.io.IOException;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.ouroboros.uischema.schema.ApiConfig;

public class APIDeserializer extends JsonDeserializer<Object> {
  @Override
  public Object deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException, JacksonException {
    var currentToken = jsonParser.currentToken();
    switch (currentToken) {
      case START_OBJECT:
        return jsonParser.readValueAs(ApiConfig.class);
      case VALUE_STRING:
        var apiStr = jsonParser.getValueAsString();
        return new ApiConfig(apiStr);
      default:
        throw null;
    }
  }
}
