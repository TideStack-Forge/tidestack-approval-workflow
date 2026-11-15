package com.ouroboros.uischema.schema.decorator;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.ouroboros.uischema.schema.ApiConfig;
import com.ouroboros.uischema.schema.Schema;

public interface WithInitAPI extends Schema {
  default Object getInitApi() {
    return getProp("initApi");
  }

  default void setInitApi(String initApi) {
    setProp("initApi", initApi);
  }

  @JsonSetter
  @JsonDeserialize(using = APIDeserializer.class)
  default void setInitApi(ApiConfig initApi) {
    setProp("initApi", initApi);
  }
}
