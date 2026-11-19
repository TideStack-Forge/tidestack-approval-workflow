package com.ouroboros.fieldtype;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

import org.junit.jupiter.api.Test;

import io.vavr.control.Either;

class DefaultFieldTypeCatalogTest {

  @Test
  void getMetadata_usesDevAppPrecedenceRules() {
    var catalog = new DefaultFieldTypeCatalog(Arrays.asList(
        provider(FieldTypeDefinitionSource.DEFAULT, "single", "默认"),
        provider(FieldTypeDefinitionSource.JAR, "single", "JAR"),
        provider(FieldTypeDefinitionSource.RUNTIME_EXTEND, "single", "运行时"),
        provider(FieldTypeDefinitionSource.DEV_BUILTIN, "single", "开发内建"),
        provider(FieldTypeDefinitionSource.APP, "single", "应用")
    ), new DefaultFieldTypeCompiler());

    var definition = catalog.getMetadata(FieldTypeCatalogContext.devApp("crm"), "single");
    assertTrue(definition.isPresent());
    assertEquals("应用", definition.get().getLabel());
  }

  @Test
  void getMetadata_usesRuntimePrecedenceRules() {
    var catalog = new DefaultFieldTypeCatalog(Arrays.asList(
        provider(FieldTypeDefinitionSource.DEFAULT, "single", "默认"),
        provider(FieldTypeDefinitionSource.JAR, "single", "JAR"),
        provider(FieldTypeDefinitionSource.RUNTIME_EXTEND, "single", "运行时"),
        provider(FieldTypeDefinitionSource.APP, "single", "应用")
    ), new DefaultFieldTypeCompiler());

    var definition = catalog.getMetadata(FieldTypeCatalogContext.runtime(), "single");
    assertTrue(definition.isPresent());
    assertEquals("运行时", definition.get().getLabel());
  }

  @Test
  void getMetadata_looksUpCaseInsensitively() {
    // 契约是大小写不敏感查找（原实现用 equalsIgnoreCase 线性扫描）。名称索引上线后
    // 索引以小写名为键，查询必须同样归一化，否则大小写不同的调用方会全部查不到。
    var catalog = new DefaultFieldTypeCatalog(List.of(
        provider(FieldTypeDefinitionSource.DEFAULT, "spiProvided", "SPI 提供")),
        new DefaultFieldTypeCompiler());

    assertTrue(catalog.getMetadata(FieldTypeCatalogContext.runtime(), "spiProvided").isPresent());
    assertTrue(catalog.getMetadata(FieldTypeCatalogContext.runtime(), "SPIPROVIDED").isPresent());
    assertTrue(catalog.getMetadata(FieldTypeCatalogContext.runtime(), "SpiProvided").isPresent());
    assertEquals("SPI 提供",
        catalog.getMetadata(FieldTypeCatalogContext.runtime(), "spiprovided").orElseThrow().getLabel());
  }

  @Test
  void listMetadata_skipsUnsupportedProviders() {
    var catalog = new DefaultFieldTypeCatalog(Arrays.asList(
        provider(FieldTypeDefinitionSource.DEFAULT, "visible", "可见"),
        unsupportedProvider(FieldTypeDefinitionSource.APP, "hidden", "隐藏")
    ), new DefaultFieldTypeCompiler());

    var definitions = catalog.listMetadata(FieldTypeCatalogContext.runtime());
    assertEquals(1, definitions.size());
    assertEquals("visible", definitions.get(0).getName());
  }

  @Test
  void compile_delegatesToCompilerAfterLookup() {
    var catalog = new DefaultFieldTypeCatalog(
        Collections.singletonList(provider(FieldTypeDefinitionSource.DEFAULT, "single", "默认")),
        (definition, params, context) -> Either.right(new StubCompiledFieldType("pk:" + definition.getName()))
    );

    var result = catalog.compile(FieldTypeCatalogContext.runtime(), "single", Collections.singletonMap("size", 10));
    assertTrue(result.isRight());
    assertEquals("pk:single", result.get().getPrimaryKeyGenerator());
  }

  @Test
  void getMetadata_returnsEmptyWhenFieldNameIsMissing() {
    var catalog = new DefaultFieldTypeCatalog(Collections.singletonList(
        provider(FieldTypeDefinitionSource.DEFAULT, "single", "默认")
    ), new DefaultFieldTypeCompiler());

    assertFalse(catalog.getMetadata(FieldTypeCatalogContext.runtime(), "missing").isPresent());
  }

  private FieldTypeDefinitionProvider provider(FieldTypeDefinitionSource source, String name, String label) {
    return new FieldTypeDefinitionProvider() {
      @Override
      public boolean supports(FieldTypeCatalogContext context) {
        return true;
      }

      @Override
      public List<FieldTypeDefinition> load(FieldTypeCatalogContext context) {
        var definition = new FieldTypeDefinition();
        definition.setName(name);
        definition.setLabel(label);
        definition.setSource(source);
        definition.setSourceLocation(source.name().toLowerCase() + ":" + name);
        return Collections.singletonList(definition);
      }
    };
  }

  private FieldTypeDefinitionProvider unsupportedProvider(FieldTypeDefinitionSource source, String name, String label) {
    return new FieldTypeDefinitionProvider() {
      @Override
      public boolean supports(FieldTypeCatalogContext context) {
        return false;
      }

      @Override
      public List<FieldTypeDefinition> load(FieldTypeCatalogContext context) {
        var definition = new FieldTypeDefinition();
        definition.setName(name);
        definition.setLabel(label);
        definition.setSource(source);
        definition.setSourceLocation(source.name().toLowerCase() + ":" + name);
        return Collections.singletonList(definition);
      }
    };
  }

  private static class StubCompiledFieldType implements CompiledFieldType {
    private final String primaryKeyGenerator;

    private StubCompiledFieldType(String primaryKeyGenerator) {
      this.primaryKeyGenerator = primaryKeyGenerator;
    }

    @Override
    public com.ouroboros.data.model.DataModelFieldMeta toDataFieldMeta() {
      return null;
    }

    @Override
    public com.ouroboros.uischema.schema.FormItemNode toInputFormItem() {
      return null;
    }

    @Override
    public com.ouroboros.uischema.schema.FormItemNode toDisplayFormItem() {
      return null;
    }

    @Override
    public String getPrimaryKeyGenerator() {
      return primaryKeyGenerator;
    }
  }
}
