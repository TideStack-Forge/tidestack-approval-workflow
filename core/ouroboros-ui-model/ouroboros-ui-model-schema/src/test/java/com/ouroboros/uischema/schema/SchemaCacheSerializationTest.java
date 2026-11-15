package com.ouroboros.uischema.schema;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SchemaCacheSerializationTest {
  @Test
  void sharedCacheRoundTripPreservesEditorFieldsAndEvents() throws Exception {
    var original = new SchemaNode(new java.util.LinkedHashMap<>(Map.of(
        "type", "form", "body", List.of(Map.of("type", "input-password", "label", "客户端密钥")),
        "onEvent", Map.of("submit", Map.of("actions", List.of(Map.of("actionType", "ajax")))))));
    var bytes = new ByteArrayOutputStream();
    try (var output = new ObjectOutputStream(bytes)) { output.writeObject(original); }
    try (var input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      var restored = assertInstanceOf(SchemaNode.class, input.readObject());
      assertEquals(original.getProps(), restored.getProps());
      assertEquals("form", restored.getType());
      restored.setName("restored-editor"); assertNull(original.getName());
    }
  }
}
