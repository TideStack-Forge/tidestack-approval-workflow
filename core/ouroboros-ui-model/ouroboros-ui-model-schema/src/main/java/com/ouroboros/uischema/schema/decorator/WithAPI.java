package com.ouroboros.uischema.schema.decorator;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.ouroboros.uischema.schema.ApiConfig;
import com.ouroboros.uischema.schema.Schema;

public interface WithAPI extends Schema {
  default Object getApi() {
    return getProp("api");
  }

  @JsonSetter
  @JsonDeserialize(using = APIDeserializer.class)
  default void setApi(Object api) {
    setProp("api", api);
  }

  default void setApi(String api) {
    setProp("api", api);
  }

  default void setApi(ApiConfig api) {
    setProp("api", api);
  }
}
