package com.ouroboros.fieldtype;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.ouroboros.util.Json;

class FieldTypeDefinitionReaderTest {

  @Test
  void read_extractsDefaultParamsFromParamsUiSchemaValue() {
    var result = new FieldTypeDefinitionReader().read(
        Json.toMap("{\"name\":\"单行文本\",\"label\":\"单行文本\",\"paramsUiSchema\":[{\"name\":\"size\",\"value\":100},{\"name\":\"regexp\",\"value\":\".*\"}],\"dataType\":{\"type\":\"String\",\"size\":\"${size}\"}}"),
        FieldTypeDefinitionSource.DEFAULT,
        "classpath:META-INF/ouroboros/metadata/单行文本.field-type.json");

    assertTrue(result.isRight());
    var definition = result.get();
    assertEquals(100, definition.getDefaultParams().get("size"));
    assertEquals(".*", definition.getDefaultParams().get("regexp"));
  }

  @Test
  void read_preservesExplicitFalsyDefaultValuesFromParamsUiSchemaValue() {
    var result = new FieldTypeDefinitionReader().read(
        Json.toMap("{\"name\":\"开关字段\",\"label\":\"开关字段\",\"paramsUiSchema\":[{\"name\":\"precision\",\"value\":0},{\"name\":\"trim\",\"value\":false},{\"name\":\"placeholder\",\"value\":\"\"}]}"),
        FieldTypeDefinitionSource.DEFAULT,
        "classpath:META-INF/ouroboros/metadata/开关字段.field-type.json");

    assertTrue(result.isRight());
    var definition = result.get();
    assertEquals(0, definition.getDefaultParams().get("precision"));
    assertEquals(false, definition.getDefaultParams().get("trim"));
    assertEquals("", definition.getDefaultParams().get("placeholder"));
  }

  @Test
  void read_failsWhenNameIsMissing() {
    var result = new FieldTypeDefinitionReader().read(
        Json.toMap("{\"label\":\"单行文本\"}"),
        FieldTypeDefinitionSource.DEFAULT,
        "classpath:META-INF/ouroboros/metadata/单行文本.field-type.json");

    assertTrue(result.isLeft());
    assertTrue(result.getLeft() instanceof FieldTypeDefinitionException);
  }

  @Test
  void read_failsWhenLabelIsMissing() {
    var result = new FieldTypeDefinitionReader().read(
        Json.toMap("{\"name\":\"单行文本\"}"),
        FieldTypeDefinitionSource.DEFAULT,
        "classpath:META-INF/ouroboros/metadata/单行文本.field-type.json");

    assertTrue(result.isLeft());
    assertTrue(result.getLeft() instanceof FieldTypeDefinitionException);
  }

  @Test
  void read_ignoresParamsUiSchemaEntriesWithoutValueAndDropsParamsJsonSchema() {
    var result = new FieldTypeDefinitionReader().read(
        Json.toMap("{\"name\":\"单行文本\",\"label\":\"单行文本\",\"paramsJsonSchema\":{\"type\":\"object\"},\"paramsUiSchema\":[{\"name\":\"size\"},{\"name\":\"regexp\",\"value\":\".*\"}]}"),
        FieldTypeDefinitionSource.DEFAULT,
        "classpath:META-INF/ouroboros/metadata/单行文本.field-type.json");

    assertTrue(result.isRight());
    var definition = result.get();
    assertFalse(definition.getDefaultParams().containsKey("size"));
    assertEquals(".*", definition.getDefaultParams().get("regexp"));
  }
}
