package com.ouroboros.uimodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

import com.ouroboros.util.Json;

class SimpleUIModelIconTest {

  @Test
  void shouldReadTopLevelIconAndKeepExtraPropsSeparate() {
    SimpleUIModel model = Json.toBean(SimpleUIModel.class,
        "{\"title\":\"字段类型管理\",\"path\":\"/dev/ui\",\"type\":\"amis\",\"icon\":\"fa fa-database\",\"schemaId\":\"/dev/ui\"}");

    assertEquals("fa fa-database", model.getIcon());
    assertFalse(model.getExtraProps().containsKey("icon"));
    assertEquals("/dev/ui", model.getPath());
    assertEquals("/dev/ui", model.getExtraProp(String.class, "schemaId").orElse(null));

    String json = Json.toJsonString(model);
    SimpleUIModel roundTrip = Json.toBean(SimpleUIModel.class, json);
    assertEquals("fa fa-database", roundTrip.getIcon());

    SimpleUIModel explicitModel = new SimpleUIModel();
    explicitModel.setIcon("fa fa-explicit");
    assertEquals("fa fa-explicit", explicitModel.getIcon());
  }
}
