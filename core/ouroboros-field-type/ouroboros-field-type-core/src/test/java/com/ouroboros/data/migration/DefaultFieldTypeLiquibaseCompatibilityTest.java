package com.ouroboros.data.migration;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

import com.ouroboros.data.adapter.DataAdapter;
import com.ouroboros.data.dsl.statement.QueryStatement;
import com.ouroboros.data.model.*;
import com.ouroboros.data.record.Record;
import com.ouroboros.data.record.RecordList;
import com.ouroboros.data.station.DataStation;
import com.ouroboros.fieldtype.*;

import io.vavr.control.Try;
import liquibase.CatalogAndSchema;
import liquibase.database.core.H2Database;
import liquibase.structure.core.DataType;

class DefaultFieldTypeLiquibaseCompatibilityTest {

  @Test
  void bundledFieldTypesShouldCompileToKnownLiquibaseTypes() {
    var catalog = new DefaultFieldTypeCatalog(
        Collections.singletonList(new JarFieldTypeDefinitionProvider(new FieldTypeDefinitionReader())),
        new DefaultFieldTypeCompiler());
    var definitions = catalog.listMetadata(FieldTypeCatalogContext.runtime());

    assertFalse(definitions.isEmpty());
    for (FieldTypeDefinition definition : definitions) {
      if (definition.getDataType() == null) {
        continue;
      }
      assertFieldTypeBuildsLiquibaseType(catalog, definition.getName(), defaultParams(definition));
    }
  }

  @Test
  void bundledSingleSelectShouldCompileAllConfiguredDataTypeBranches() {
    var catalog = new DefaultFieldTypeCatalog(
        Collections.singletonList(new JarFieldTypeDefinitionProvider(new FieldTypeDefinitionReader())),
        new DefaultFieldTypeCompiler());

    assertFieldTypeBuildsLiquibaseType(catalog, "单选", params("dataType", "String", "size", 100));
    assertFieldTypeBuildsLiquibaseType(catalog, "单选", params("dataType", "Short"));
    assertFieldTypeBuildsLiquibaseType(catalog, "单选", params("dataType", "Integer"));
    assertFieldTypeBuildsLiquibaseType(catalog, "单选", params("dataType", "Long"));
  }

  private static void assertFieldTypeBuildsLiquibaseType(DefaultFieldTypeCatalog catalog,
                                                         String fieldTypeName,
                                                         Map<String, Object> params) {
    var compileResult = catalog.compile(FieldTypeCatalogContext.runtime(), fieldTypeName, params);
    assertTrue(compileResult.isRight(), () -> fieldTypeName + " compile failed: " + compileResult.getLeft().getMessage());
    var fieldMeta = compileResult.get().toDataFieldMeta();
    assertNotNull(fieldMeta, () -> fieldTypeName + " dataType missing");
    fieldMeta.setName("field");
    fieldMeta.setLabel(fieldTypeName);
    fieldMeta.setRawName("field");

    var model = new StubModel();
    var field = new DefaultDataModelField(model, fieldMeta);
    model.fields.add(field);
    var table = TableBuilder.build(new H2Database(), model, new CatalogAndSchema(null, null));
    DataType dataType = table.getColumn("field").getType();

    assertNotEquals("Unknown", field.getValueType().getName(), () -> fieldTypeName + " resolved unknown value type");
    assertEquals(expectedLiquibaseType(fieldMeta.getType(), fieldMeta.getSize()), dataType.getTypeName(), fieldTypeName);
    if ("String".equals(fieldMeta.getType()) && fieldMeta.getSize() != null && fieldMeta.getSize() < 1024) {
      assertEquals(fieldMeta.getSize(), dataType.getColumnSize(), fieldTypeName);
    }
    if ("Decimal".equals(fieldMeta.getType())) {
      assertEquals(Optional.ofNullable(fieldMeta.getSize()).orElse(10), dataType.getColumnSize(), fieldTypeName);
      assertEquals(Optional.ofNullable(fieldMeta.getDecimalDigits()).orElse(2), dataType.getDecimalDigits(), fieldTypeName);
    }
  }

  private static Map<String, Object> defaultParams(FieldTypeDefinition definition) {
    var params = new LinkedHashMap<String, Object>(definition.getDefaultParams());
    if ("单选".equals(definition.getName()) || "多选".equals(definition.getName())) {
      params.put("options", Collections.singletonList(option("A")));
    }
    return params;
  }

  private static String expectedLiquibaseType(String fieldType, Integer size) {
    switch (fieldType) {
      case "String":
        return size == null || size < 1024 ? "VARCHAR" : "TEXT";
      case "Short":
        return "TINYINT";
      case "Integer":
        return "INTEGER";
      case "Long":
      case "Snowflake":
        return "BIGINT";
      case "Double":
        return "DOUBLE";
      case "Decimal":
        return "DECIMAL";
      case "Date":
        return "DATE";
      case "DateTime":
        return "DATETIME";
      default:
        fail("Unexpected bundled field data type: " + fieldType);
        return null;
    }
  }

  private static Map<String, Object> params(Object... keyValues) {
    var map = new LinkedHashMap<String, Object>();
    for (int i = 0; i < keyValues.length; i += 2) {
      map.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
    }
    if (!map.containsKey("options")) {
      map.put("options", Collections.singletonList(option("A")));
    }
    return map;
  }

  private static Map<String, Object> option(String value) {
    var option = new LinkedHashMap<String, Object>();
    option.put("value", value);
    option.put("label", "选项" + value);
    return option;
  }

  private static class StubModel implements DataModel {
    private final List<DataModelField> fields = new ArrayList<>();

    @Override public String getFormatVersion() { return "1"; }
    @Override public String getSource() { return null; }
    @Override public String getNamespace() { return "demo"; }
    @Override public String getName() { return "field_type_check"; }
    @Override public String getFullName() { return "demo.field_type_check"; }
    @Override public String getLabel() { return "Field Type Check"; }
    @Override public String getDescription() { return null; }
    @Override public String getRawName() { return "field_type_check"; }
    @Override public MigrationStrategy getMigrationStrategy() { return MigrationStrategy.AUTO; }
    @Override public Map<String, Object> getExtraProps() { return Collections.emptyMap(); }
    @Override public Optional<Object> getExtraProp(String name) { return Optional.empty(); }
    @Override public List<DataModelField> getFields() { return fields; }
    @Override public Optional<DataModelField> getField(String name) { return fields.stream().filter(field -> field.getName().equals(name)).findFirst(); }
    @Override public List<DataModelField> getPrimaryKeys() { return Collections.emptyList(); }
    @Override public PrimaryKeyGenerator<?> getPrimaryKeyGenerator() { return null; }
    @Override public DataAdapter getAdapter() { return null; }
    @Override public DataStation<?> getDataStation() { return null; }
    @Override public Try<Record> insert(Map<String, Object> data) { return null; }
    @Override public Try<Record> insertOrUpdate(Map<String, Object> data) { return null; }
    @Override public Try<RecordList> batchInsert(List<Map<String, Object>> dataList) { return null; }
    @Override public Try<RecordList> batchInsertOrUpdate(List<Map<String, Object>> dataList) { return null; }
    @Override public Try<Long> update(Object id, Map<String, Object> data) { return null; }
    @Override public Try<Long> update(List<?> ids, Map<String, Object> data) { return null; }
    @Override public Try<Long> update(Map<String, Object> where, Map<String, Object> data) { return null; }
    @Override public Try<Long> delete(Object id) { return null; }
    @Override public Try<Long> delete(List<?> ids) { return null; }
    @Override public Try<Long> delete(Map<String, Object> where) { return null; }
    @Override public Try<Long> count(Map<String, Object> where) { return null; }
    @Override public Try<Record> get(Object id) { return null; }
    @Override public Try<Record> get(Object id, Map<String, Object> statement) { return null; }
    @Override public Try<RecordList> query(List<?> ids) { return null; }
    @Override public Try<RecordList> query(QueryStatement statement) { return null; }
    @Override public Try<RecordList> query(Map<String, Object> statement) { return null; }
    @Override public Try<RecordList> query(List<String> select, Map<String, Object> where) { return null; }
    @Override public Try<RecordList> query(List<String> select, Map<String, Object> where, Map<String, Object> orderBy) { return null; }
    @Override public Try<RecordList> query(List<String> select, Map<String, Object> where, Map<String, Object> orderBy, Integer offset, Integer limit) { return null; }
    @Override public DataModel withPlugins(Collection<PluginDescriptor> pluginDescriptors) { return this; }
    @Override public DataModel withoutPlugins(Collection<String> pluginNames) { return this; }
    @Override public DataModel withoutPlugins() { return this; }
    @Override public boolean hasPlugin(String name) { return false; }
  }
}
