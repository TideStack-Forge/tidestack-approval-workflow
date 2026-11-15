package com.ouroboros.data.typed.runtime;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;

import org.junit.jupiter.api.Test;

import com.ouroboros.data.annotation.Model;
import com.ouroboros.data.exception.ModelMetadataException;

class TypedAnnotationDecoratorContextTest {

  @Test
  void resolvesModelNameUsingModelFullName() {
    var context = contextWith(FullNameTarget.class);

    String modelName = context.resolveModelName(FullNameTarget.class);

    assertEquals("demo.FullNameTarget", modelName);
  }

  @Test
  void resolvesModelNameUsingSimpleNameFallback() {
    var context = contextWith(SimpleNameTarget.class);

    String modelName = context.resolveModelName(SimpleNameTarget.class);

    assertEquals("SimpleNameTarget", modelName);
  }

  @Test
  void rejectsNullModelClass() {
    var context = contextWith();

    ModelMetadataException exception = assertThrows(
        ModelMetadataException.class,
        () -> context.resolveModelName(null)
    );

    assertTrue(exception.getMessage().contains("SourceModel"));
    assertTrue(exception.getMessage().contains("目标模型 class 不能为空"));
  }

  @Test
  void rejectsSentinelModelClass() {
    var context = contextWith();

    ModelMetadataException exception = assertThrows(
        ModelMetadataException.class,
        () -> context.resolveModelName(Void.class)
    );

    assertTrue(exception.getMessage().contains("SourceModel"));
    assertTrue(exception.getMessage().contains("目标模型 class 不能为空"));
  }

  @Test
  void rejectsModelClassWithoutModelAnnotation() {
    var context = contextWith(NotModelTarget.class);

    ModelMetadataException exception = assertThrows(
        ModelMetadataException.class,
        () -> context.resolveModelName(NotModelTarget.class)
    );

    assertTrue(exception.getMessage().contains("SourceModel"));
    assertTrue(exception.getMessage().contains("@Model"));
    assertTrue(exception.getMessage().contains(NotModelTarget.class.getName()));
  }

  @Test
  void rejectsModelClassOutsideCurrentScanSet() {
    var context = contextWith();

    ModelMetadataException exception = assertThrows(
        ModelMetadataException.class,
        () -> context.resolveModelName(FullNameTarget.class)
    );

    assertTrue(exception.getMessage().contains("SourceModel"));
    assertTrue(exception.getMessage().contains("扫描结果"));
    assertTrue(exception.getMessage().contains(FullNameTarget.class.getName()));
  }

  private static DefaultTypedAnnotationDecoratorContext contextWith(Class<?>... modelClasses) {
    return new DefaultTypedAnnotationDecoratorContext("SourceModel", Set.of(modelClasses));
  }

  @Model(fullName = "demo.FullNameTarget")
  static class FullNameTarget {
  }

  @Model
  static class SimpleNameTarget {
  }

  static class NotModelTarget {
  }
}
