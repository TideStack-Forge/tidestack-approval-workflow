package com.ouroboros.fieldtype;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.ouroboros.util.Json;

class BundledFieldTypeDefinitionCompatibilityTest {

  @Test
  void bundledFieldDefinitionsStillLoadWithoutParamsJsonSchema() throws Exception {
    var reader = new FieldTypeDefinitionReader();
    var bundledFiles = getBundledFieldFiles();

    assertFalse(bundledFiles.isEmpty());

    FieldTypeDefinition singleLineText = null;
    FieldTypeDefinition snowflakePrimaryKey = null;
    for (Path bundledFile : bundledFiles) {
      var raw = Json.toMap(String.join("", Files.readAllLines(bundledFile)));
      assertFalse(raw.containsKey("paramsJsonSchema"), bundledFile.toString());

      var source = bundledFile.getFileName().toString().equals("编码主键.field-type.json")
          ? FieldTypeDefinitionSource.JAR
          : FieldTypeDefinitionSource.DEFAULT;
      var definition = reader.read(raw, source, bundledFile.toString());
      assertTrue(definition.isRight(), bundledFile.toString());

      if ("单行文本.field-type.json".equals(bundledFile.getFileName().toString())) {
        singleLineText = definition.get();
      }
      if ("雪花主键.field-type.json".equals(bundledFile.getFileName().toString())) {
        snowflakePrimaryKey = definition.get();
      }
    }

    assertNotNull(singleLineText);
    assertFalse(singleLineText.getDefaultParams().isEmpty());
    assertNotNull(snowflakePrimaryKey);
    assertEquals("Snowflake", snowflakePrimaryKey.getDefaultParams().get("valueType"));

    var compiler = new DefaultFieldTypeCompiler();
    var defaultSnowflake = compiler.compile(snowflakePrimaryKey, Collections.emptyMap(), FieldTypeCatalogContext.runtime());
    assertTrue(defaultSnowflake.isRight());
    assertEquals("Snowflake", defaultSnowflake.get().toDataFieldMeta().getType());

    var stringSnowflake = compiler.compile(snowflakePrimaryKey, Collections.singletonMap("valueType", "String"), FieldTypeCatalogContext.runtime());
    assertTrue(stringSnowflake.isRight());
    assertEquals("String", stringSnowflake.get().toDataFieldMeta().getType());
  }

  private List<Path> getBundledFieldFiles() throws Exception {
    var files = new ArrayList<Path>();

    var defaultFieldsDir = Paths.get("..", "ouroboros-field-type-default-types", "src", "main", "metadata")
        .normalize();
    assertTrue(Files.isDirectory(defaultFieldsDir), defaultFieldsDir.toString());

    try (var stream = Files.list(defaultFieldsDir)) {
      stream
          .filter(path -> path.getFileName().toString().endsWith(".field-type.json"))
          .sorted(Comparator.comparing(path -> path.getFileName().toString()))
          .forEach(files::add);
    }

    var codingPrimaryKeyFile = Paths.get("..", "..", "ouroboros-data", "ouroboros-data-pkgen-coding", "src", "main",
        "metadata", "编码主键.field-type.json")
        .normalize();
    assertTrue(Files.exists(codingPrimaryKeyFile), codingPrimaryKeyFile.toString());
    files.add(codingPrimaryKeyFile);

    return files;
  }
}
