package com.ouroboros.uischema.schema;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.ouroboros.uischema.schema.event.EventActionsConfig;
import com.ouroboros.util.Json;

public class TestSchemaNode {
  @Test
  public void testSchemaNodeToJson() throws JsonProcessingException {
    var schemaNode = new SchemaNode();
    schemaNode.setType("input-text");
    schemaNode.setClassName("abc");
    schemaNode.addOnEvent("click", new EventActionsConfig());
    var jsonString = Json.toPrettyJsonString(schemaNode);
    var schemaNode2 = Json.toBean(SchemaNode.class, jsonString);
    assertNotNull(schemaNode2);
    assertEquals(jsonString, Json.toPrettyJsonString(schemaNode2));
  }

  @Test
  public void testPageNodeToJson() {
    var pageNode = new PageNode();
    var body = new SchemaNode();
    body.setType("input-text");
    pageNode.setTitle("abc");
    pageNode.setClassName("abc");
    pageNode.addOnEvent("click", new EventActionsConfig());
    pageNode.setBody(body);
    var jsonString = Json.toPrettyJsonString(pageNode);
    var pageNode2 = Json.tryToBean(PageNode.class, jsonString);
    assertTrue(pageNode2.isSuccess());
    assertEquals(jsonString, Json.toPrettyJsonString(pageNode2.get()));

    var bodyList = Arrays.asList("abc", body);
    pageNode.setBody(bodyList);
    var jsonString2 = Json.toPrettyJsonString(pageNode);
    var pageNode22 = Json.tryToBean(PageNode.class, jsonString2);
    assertTrue(pageNode22.isSuccess());
    assertEquals(jsonString2, Json.toPrettyJsonString(pageNode22.get()));
  }

  @Test
  public void testStyle() {
    var styleConfig = new StyleConfig();
    styleConfig.setStyle("color", "red");
    var jsonString = Json.toPrettyJsonString(styleConfig);
    var styleConfig2 = Json.toBean(StyleConfig.class, jsonString);
    assertNotNull(styleConfig2);
    assertEquals(jsonString, Json.toPrettyJsonString(styleConfig2));

    var tableColumnNode = new TableColumnNode();
    tableColumnNode.setStyle(styleConfig);
    assertEquals("""
        {
          "style" : {
            "color" : "red"
          }
        }""", Json.toPrettyJsonString(tableColumnNode));
  }
}
