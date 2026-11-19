package com.ouroboros.fieldtype;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class JarFieldTypeDefinitionProviderTest {

  @Test
  void load_readsTypedMetadataFieldTypeResources() {
    JarFieldTypeDefinitionProvider provider = new JarFieldTypeDefinitionProvider(new FieldTypeDefinitionReader());

    FieldTypeDefinition definition = provider.load(FieldTypeCatalogContext.runtime()).stream()
        .filter(item -> "typedJarField".equals(item.getName()))
        .findFirst()
        .orElse(null);

    assertNotNull(definition);
    assertEquals(FieldTypeDefinitionSource.JAR, definition.getSource());
    assertEquals(24, definition.getDefaultParams().get("size"));
  }
}
