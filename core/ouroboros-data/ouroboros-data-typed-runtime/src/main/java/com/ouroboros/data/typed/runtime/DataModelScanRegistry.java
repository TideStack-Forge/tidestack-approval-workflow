package com.ouroboros.data.typed.runtime;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.core.ResolvableType;
import org.springframework.util.ClassUtils;

import com.ouroboros.data.annotation.DataModelScan;
import com.ouroboros.data.annotation.Model;
import com.ouroboros.data.annotation.PrimaryKey;
import com.ouroboros.data.model.TypedDataModel;

/**
 * @author liansz
 */
public class DataModelScanRegistry implements BeanDefinitionRegistryPostProcessor {
  private static final Logger logger = LoggerFactory.getLogger(DataModelScanRegistry.class);
  private static final String DEFERRED_MODEL_REQUEST_REGISTRY_BEAN_NAME = "deferredModelRequestRegistry";

  private static final Set<Class<?>> DATA_MODEL_SCAN_ANNOTATION_CLASSES =
      Collections.synchronizedSet(new LinkedHashSet<>());

  public static Collection<Class<?>> getDataModelScanAnnotationClasses() {
    return DATA_MODEL_SCAN_ANNOTATION_CLASSES;
  }

  static Set<Class<?>> getDataModelScanAnnotationClassSnapshot() {
    synchronized (DATA_MODEL_SCAN_ANNOTATION_CLASSES) {
      return Set.copyOf(DATA_MODEL_SCAN_ANNOTATION_CLASSES);
    }
  }

  @Override
  public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
    Set<Class<?>> scanAnnotations = Arrays.stream(registry.getBeanDefinitionNames())
        .map(registry::getBeanDefinition)
        .map(this::resolveBeanClass)
        .flatMap(Optional::stream)
        .filter(beanClass -> beanClass.isAnnotationPresent(DataModelScan.class))
        .distinct()
        .sorted(Comparator.comparing(Class::getName))
        .collect(Collectors.toCollection(LinkedHashSet::new));

    replaceDataModelScanAnnotations(scanAnnotations);
    scanAnnotations.forEach(scanAnnotation -> registerTypedDataModelBeanDefinitions(scanAnnotation, registry));
  }

  @Override
  public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
  }

  private Optional<Class<?>> resolveBeanClass(BeanDefinition beanDefinition) {
    String beanClassName = beanDefinition.getBeanClassName();
    if (StringUtils.isBlank(beanClassName)) {
      return Optional.empty();
    }
    try {
      return Optional.of(ClassUtils.forName(beanClassName, ClassUtils.getDefaultClassLoader()));
    } catch (ClassNotFoundException | LinkageError e) {
      throw new IllegalStateException("数据模型扫描注解类加载失败: " + beanClassName, e);
    }
  }

  private void replaceDataModelScanAnnotations(Set<Class<?>> scanAnnotations) {
    synchronized (DATA_MODEL_SCAN_ANNOTATION_CLASSES) {
      DATA_MODEL_SCAN_ANNOTATION_CLASSES.clear();
      DATA_MODEL_SCAN_ANNOTATION_CLASSES.addAll(scanAnnotations);
    }
  }

  private void registerTypedDataModelBeanDefinitions(Class<?> scanAnnotation, BeanDefinitionRegistry registry) {
    DataModelScan dataModelScan = scanAnnotation.getAnnotation(DataModelScan.class);
    String[] basePackages = dataModelScan.value();
    if (basePackages.length == 0) {
      basePackages = new String[]{scanAnnotation.getPackage().getName()};
    }

    for (String basePackage : basePackages) {
      TypedModelClassScanner.scanModelClasses(basePackage)
          .forEach(modelClass -> registerTypedDataModelBeanDefinition(modelClass, registry));
    }
  }

  private void registerTypedDataModelBeanDefinition(Class<?> modelClass, BeanDefinitionRegistry registry) {
    Optional<Class<?>> pkClass = resolvePrimaryKeyClass(modelClass);
    if (pkClass.isEmpty()) {
      logger.debug("Skip typed data model bean registration for {} because it does not have exactly one primary key",
          modelClass.getName());
      return;
    }

    String beanName = "typedDataModel." + modelClass.getName();
    if (registry.containsBeanDefinition(beanName)) {
      return;
    }

    String modelName = resolveModelName(modelClass);
    ResolvableType objectType = ResolvableType.forClassWithGenerics(TypedDataModel.class, pkClass.get(), modelClass);
    RootBeanDefinition definition = (RootBeanDefinition) BeanDefinitionBuilder.rootBeanDefinition(TypedDataModelFactoryBean.class)
        .addConstructorArgReference(DEFERRED_MODEL_REQUEST_REGISTRY_BEAN_NAME)
        .addConstructorArgValue(modelName)
        .addConstructorArgValue(pkClass.get())
        .addConstructorArgValue(modelClass)
        .getBeanDefinition();
    definition.setLazyInit(true);
    definition.setTargetType(objectType);
    definition.setAttribute(FactoryBean.OBJECT_TYPE_ATTRIBUTE, objectType);
    registry.registerBeanDefinition(beanName, definition);
  }

  private Optional<Class<?>> resolvePrimaryKeyClass(Class<?> modelClass) {
    var primaryKeyFields = Arrays.stream(modelClass.getDeclaredFields())
        .filter(field -> field.isAnnotationPresent(PrimaryKey.class))
        .toList();
    if (primaryKeyFields.size() != 1) {
      return Optional.empty();
    }
    Field primaryKeyField = primaryKeyFields.get(0);
    return Optional.of(primaryKeyField.getType());
  }

  private String resolveModelName(Class<?> modelClass) {
    Model model = modelClass.getAnnotation(Model.class);
    if (model != null && StringUtils.isNotBlank(model.fullName())) {
      return model.fullName().trim();
    }
    return modelClass.getSimpleName();
  }
}
