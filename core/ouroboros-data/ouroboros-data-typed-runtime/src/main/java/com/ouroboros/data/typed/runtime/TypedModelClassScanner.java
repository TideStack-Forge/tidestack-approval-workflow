package com.ouroboros.data.typed.runtime;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import com.ouroboros.data.annotation.Model;
import com.ouroboros.data.exception.ModelMetadataException;

public final class TypedModelClassScanner {

  private TypedModelClassScanner() {
  }

  public static Set<Class<?>> scanModelClasses(String packageName) {
    ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new AnnotationTypeFilter(Model.class));
    return scanner.findCandidateComponents(packageName).stream()
        .map(BeanDefinition::getBeanClassName)
        .filter(Objects::nonNull)
        .map(TypedModelClassScanner::loadClass)
        .collect(Collectors.toSet());
  }

  private static Class<?> loadClass(String className) {
    try {
      return Class.forName(className);
    } catch (ClassNotFoundException e) {
      throw new ModelMetadataException("模型类加载失败: " + className, className, e);
    }
  }
}
