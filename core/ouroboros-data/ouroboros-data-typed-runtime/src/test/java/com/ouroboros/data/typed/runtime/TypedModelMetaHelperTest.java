package com.ouroboros.data.typed.runtime;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ouroboros.data.model.DataModelFieldMeta;
import com.ouroboros.data.model.DataModelMeta;
import com.ouroboros.data.model.UniquenessScope;

class TypedModelMetaHelperTest {
  @Test
  void scanModelMetaPreservesAutoIncrementPrimaryKeyAnnotation() {
    Map<String, DataModelMeta> metas = TypedModelMetaHelper.scanModelMate("com.ouroboros.data");
    DataModelMeta meta = metas.get("AutoIncrementTestModel");

    assertNotNull(meta);
    DataModelFieldMeta idField = meta.getFields().stream()
        .filter(field -> "id".equals(field.getName()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("id field not found"));

    assertTrue(idField.getExtraProp(Boolean.class, "isPrimaryKey").orElse(false));
    assertTrue(idField.getExtraProp(Boolean.class, "isAutoIncrement").orElse(false));
    assertTrue(idField.getIsAutoIncrement());
  }

  @Test
  void scanModelMetaAppliesTypedModelAnnotationDecorator() {
    Map<String, DataModelMeta> metas = TypedModelMetaHelper.scanModelMate("com.ouroboros.data");
    DataModelMeta meta = metas.get("TestDecoratorModel");

    assertNotNull(meta);
    assertEquals("enabled", meta.getExtraProp(String.class, "testDecoratorPlugin").orElse(null));
  }

  @Test
  void scanModelMetaAppliesFieldAndRepeatableModelAnnotationDecorators() {
    Map<String, DataModelMeta> metas = TypedModelMetaHelper.scanModelMate("com.ouroboros.data");
    DataModelMeta meta = metas.get("UniqueDecoratorModel");

    assertNotNull(meta);
    assertEquals(2, meta.getUniqueConstraints().size());
    assertEquals("project_code", meta.getUniqueConstraints().get(0).getName());
    assertEquals("projectId", meta.getUniqueConstraints().get(0).getFields().get(0));
    assertEquals("code", meta.getUniqueConstraints().get(0).getFields().get(1));
    assertEquals(UniquenessScope.ACTIVE_RECORDS, meta.getUniqueConstraints().get(0).getScope());
    assertEquals("tenant_code", meta.getUniqueConstraints().get(1).getName());
    assertNull(meta.getUniqueConstraints().get(1).getScope());

    DataModelFieldMeta codeField = meta.getFields().stream()
        .filter(field -> "code".equals(field.getName()))
        .findFirst()
        .orElseThrow(AssertionError::new);
    DataModelFieldMeta nameField = meta.getFields().stream()
        .filter(field -> "name".equals(field.getName()))
        .findFirst()
        .orElseThrow(AssertionError::new);

    assertTrue(codeField.getIsUnique());
    assertEquals(UniquenessScope.ALL_RECORDS, codeField.getUniquenessScope());
    assertTrue(nameField.getIsUnique());
    assertNull(nameField.getUniquenessScope());
  }
}
