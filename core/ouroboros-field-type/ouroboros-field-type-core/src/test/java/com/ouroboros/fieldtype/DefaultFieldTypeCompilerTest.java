package com.ouroboros.fieldtype;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

import org.junit.jupiter.api.Test;

class DefaultFieldTypeCompilerTest {

  @Test
  void compile_buildsDataFieldAndInputDisplayFormItemsFromMetadata() {
    var definition = buildSingleLineTextDefinition();

    var result = new DefaultFieldTypeCompiler().compile(
        definition,
        Collections.singletonMap("size", 64),
        FieldTypeCatalogContext.runtime());

    assertTrue(result.isRight());
    var compiled = result.get();
    assertEquals("String", compiled.toDataFieldMeta().getType());
    assertEquals(Integer.valueOf(64), compiled.toDataFieldMeta().getSize());
    assertEquals("input-text", compiled.toInputFormItem().getType());
    assertEquals("static-text", compiled.toDisplayFormItem().getType());
  }

  @Test
  void compile_fallsBackToInputUiWhenDisplayUiIsMissing() {
    var definition = buildSingleLineTextDefinition();
    definition.setUi(buildInputOnlyUi());

    var result = new DefaultFieldTypeCompiler().compile(
        definition,
        Collections.singletonMap("size", 32),
        FieldTypeCatalogContext.runtime());

    assertTrue(result.isRight());
    var display = result.get().toDisplayFormItem();
    assertEquals("input-text", display.getType());
    assertTrue(Boolean.TRUE.equals(display.getStatic()));
  }

  @Test
  void compile_resolvesPrimaryKeyGeneratorExpressionsUsingMergedParams() {
    var definition = buildPrimaryKeyDefinition();

    var result = new DefaultFieldTypeCompiler().compile(
        definition,
        Collections.singletonMap("prefix", "ORDER"),
        FieldTypeCatalogContext.runtime());

    assertTrue(result.isRight());
    assertEquals("coding:ORDER:8", result.get().getPrimaryKeyGenerator());
  }

  @Test
  void compile_resolvesNestedFormItemExpressionsUsingMergedParams() {
    var definition = buildSingleLineTextDefinition();
    definition.setValidations(mapOf(
        "matchRegexp", "${regexp}",
        "nested", mapOf(
            "items", Arrays.asList(mapOf("pattern", "${regexp}"))
        )
    ));
    definition.setValidationErrors(mapOf("matchRegexp", "${regexpMsg}"));
    definition.setUi(mapOf(
        "input", mapOf(
            "type", "input-text",
            "maxLength", "${size}",
            "source", Arrays.asList(
                mapOf("label", "固定项", "value", "${optionValue}")
            )
        )
    ));

    var result = new DefaultFieldTypeCompiler().compile(
        definition,
        mapOf(
            "size", 32,
            "regexp", "^[A-Z]+$",
            "regexpMsg", "只能输入大写字母",
            "optionValue", "fixed"
        ),
        FieldTypeCatalogContext.runtime());

    assertTrue(result.isRight());
    var input = result.get().toInputFormItem();
    assertEquals(Integer.valueOf(32), input.getProp(Integer.class, "maxLength").orElse(null));
    assertEquals("^[A-Z]+$", input.getValidations().get("matchRegexp"));
    assertEquals("只能输入大写字母", input.getValidationErrors().get("matchRegexp"));
    var source = input.getProp(List.class, "source").orElseThrow(AssertionError::new);
    assertEquals("fixed", ((Map<?, ?>) source.get(0)).get("value"));
    var nestedValidation = (Map<?, ?>) input.getValidations().get("nested");
    var nestedItems = (List<?>) nestedValidation.get("items");
    assertEquals("^[A-Z]+$", ((Map<?, ?>) nestedItems.get(0)).get("pattern"));
  }

  @Test
  void compile_skipsMissingOptionalFormItemProperties() {
    var definition = new FieldTypeDefinition();
    definition.setName("定点数字");
    definition.setLabel("定点数字");
    definition.setDataType(mapOf(
        "type", "Decimal",
        "decimalDigits", "${precision}",
        "size", "${size}"
    ));
    definition.setUi(mapOf(
        "input", mapOf(
            "type", "input-number",
            "min", "${min}",
            "max", "${max}",
            "precision", "${precision}"
        )
    ));
    definition.setDefaultParams(mapOf("size", 10, "precision", 2));

    var result = new DefaultFieldTypeCompiler().compile(
        definition,
        Collections.<String, Object>emptyMap(),
        FieldTypeCatalogContext.runtime());

    assertTrue(result.isRight());
    assertEquals("Decimal", result.get().toDataFieldMeta().getType());
    assertEquals(Integer.valueOf(10), result.get().toDataFieldMeta().getSize());
    assertEquals(Integer.valueOf(2), result.get().toDataFieldMeta().getDecimalDigits());
    assertFalse(result.get().toInputFormItem().getProps().containsKey("min"));
    assertFalse(result.get().toInputFormItem().getProps().containsKey("max"));
    assertEquals(Integer.valueOf(2), result.get().toInputFormItem().getProp(Integer.class, "precision").orElse(null));
  }

  @Test
  void compile_failsWhenExpressionVariableIsMissing() {
    var definition = buildSingleLineTextDefinition();
    definition.setDefaultParams(Collections.<String, Object>emptyMap());

    var result = new DefaultFieldTypeCompiler().compile(
        definition,
        Collections.<String, Object>emptyMap(),
        FieldTypeCatalogContext.runtime());

    assertTrue(result.isLeft());
    assertTrue(result.getLeft() instanceof FieldTypeCompileException);
  }

  private FieldTypeDefinition buildSingleLineTextDefinition() {
    var definition = new FieldTypeDefinition();
    definition.setName("单行文本");
    definition.setLabel("单行文本");
    definition.setDataType(mapOf(
        "type", "String",
        "size", "${size}"
    ));
    definition.setUi(buildInputAndDisplayUi());
    definition.setDefaultParams(mapOf("size", 100));
    definition.setSource(FieldTypeDefinitionSource.DEFAULT);
    definition.setSourceLocation("classpath:META-INF/ouroboros/metadata/单行文本.field-type.json");
    return definition;
  }

  private FieldTypeDefinition buildPrimaryKeyDefinition() {
    var definition = new FieldTypeDefinition();
    definition.setName("编码主键");
    definition.setLabel("编码主键");
    definition.setPrimaryKeyGenerator("coding:${prefix}:${size}");
    definition.setDefaultParams(mapOf("size", 8));
    definition.setSource(FieldTypeDefinitionSource.DEFAULT);
    definition.setSourceLocation("classpath:META-INF/ouroboros/metadata/编码主键.field-type.json");
    return definition;
  }

  private Map<String, Object> buildInputAndDisplayUi() {
    var input = mapOf(
        "type", "input-text",
        "maxLength", "${size}"
    );
    var display = mapOf("type", "static-text");
    return mapOf(
        "input", input,
        "display", display
    );
  }

  private Map<String, Object> buildInputOnlyUi() {
    var input = mapOf(
        "type", "input-text",
        "maxLength", "${size}"
    );
    return mapOf("input", input);
  }

  private Map<String, Object> mapOf(Object... keyValues) {
    var map = new LinkedHashMap<String, Object>();
    for (int i = 0; i < keyValues.length; i += 2) {
      map.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
    }
    return map;
  }
}
