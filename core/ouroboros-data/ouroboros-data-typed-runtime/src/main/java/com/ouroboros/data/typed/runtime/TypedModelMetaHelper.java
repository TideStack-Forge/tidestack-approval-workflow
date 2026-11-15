package com.ouroboros.data.typed.runtime;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;

import com.ouroboros.data.annotation.*;
import com.ouroboros.data.exception.FieldMetadataException;
import com.ouroboros.data.exception.ModelMetadataException;
import com.ouroboros.data.model.*;
import com.ouroboros.util.JsonBag;
import com.ouroboros.util.Maps;
import com.ouroboros.util.SPI;
import com.ouroboros.util.Strings;

/**
 * @author liansz
 */
public class TypedModelMetaHelper {

  /**
   * 扫描数据模型元数据
   *
   * @param packageName 包名
   * @return
   */
  public static Map<String, DataModelMeta> scanModelMate(String packageName) {
    Set<Class<?>> modelMetadataClassSet = TypedModelClassScanner.scanModelClasses(packageName);

    Map<String, DataModelMeta> modelMetaMap = new HashMap<>();
    for (Class<?> metadataClass : modelMetadataClassSet) {
      Model modelAnnotation = metadataClass.getAnnotation(Model.class);

      String modelName = StringUtils.isNotEmpty(modelAnnotation.fullName())
          ? modelAnnotation.fullName()
          : metadataClass.getSimpleName();

      DataModelMeta modelMeta = new DataModelMeta();
      modelMeta.setDataStation(modelAnnotation.dataStation());
      modelMeta.setName(modelName);
      modelMeta.setLabel(modelAnnotation.label());
      modelMeta.setRawName(rawNameConverter(modelAnnotation.rawName(), modelMeta.getName()));
      modelMeta.setMigrationStrategy(modelAnnotation.migrationStrategy());

      // 设置扩展属性
      try {
        modelMeta.setExtraProps(buildExtraProps(modelAnnotation.extraProps()));
      } catch (Exception e) {
        throw new ModelMetadataException(String.format("模型: %1s 的 extraProps 属性配置错误: ", modelName), modelName, e);
      }

      // 构建字段元数据
      List<DataModelFieldMeta> fieldMetas;

      try {
        fieldMetas = Arrays.stream(metadataClass.getDeclaredFields())
            .filter(field -> field.isAnnotationPresent(com.ouroboros.data.annotation.Field.class))
            .map(TypedModelMetaHelper::buildFieldMeta)
            .collect(Collectors.toList());
      } catch (Exception e) {
        throw new ModelMetadataException(String.format("模型: %1s 的字段设置错误: ", modelName), modelName, e);
      }

      // 设置字段元数据
      modelMeta.setFields(fieldMetas);

      modelMeta = applyAnnotationDecorators(metadataClass, modelMeta, modelMetadataClassSet);

      modelMetaMap.put(modelMeta.getFullName(), modelMeta);
    }

    return modelMetaMap;
  }

  private static DataModelMeta applyAnnotationDecorators(
      Class<?> metadataClass,
      DataModelMeta modelMeta,
      Set<Class<?>> modelMetadataClassSet
  ) {
    var context = new DefaultTypedAnnotationDecoratorContext(modelMeta.getFullName(), modelMetadataClassSet);
    DataModelMeta decoratedMeta = applyFieldAnnotationDecorators(metadataClass, modelMeta, context);
    return applyModelAnnotationDecorators(metadataClass, decoratedMeta, context);
  }

  private static DataModelMeta applyFieldAnnotationDecorators(
      Class<?> metadataClass,
      DataModelMeta modelMeta,
      TypedAnnotationDecoratorContext context
  ) {
    var decorators = SPI.getCachedServiceStream(TypedFieldAnnotationDecorator.class)
        .map(decorator -> (TypedFieldAnnotationDecorator<?>) decorator)
        .sorted(Comparator.comparingInt(TypedFieldAnnotationDecorator<?>::getOrder)
            .thenComparing(decorator -> decorator.getClass().getName()))
        .collect(Collectors.toList());
    if (decorators.isEmpty()) {
      return modelMeta;
    }

    DataModelMeta currentModelMeta = modelMeta;
    List<DataModelFieldMeta> fieldMetas = new ArrayList<>(modelMeta.getFields());
    for (Field field : metadataClass.getDeclaredFields()) {
      int fieldIndex = indexOfField(fieldMetas, field.getName());
      if (fieldIndex < 0) {
        continue;
      }
      DataModelFieldMeta fieldMeta = fieldMetas.get(fieldIndex);
      for (TypedFieldAnnotationDecorator<?> decorator : decorators) {
        var annotation = field.getAnnotation(decorator.annotationType());
        if (annotation != null) {
          fieldMeta = Objects.requireNonNull(
              decorator.decorateAnnotation(metadataClass, field, annotation, fieldMeta, currentModelMeta, context),
              () -> decorator.getClass().getName() + " returned null field metadata"
          );
          fieldMetas.set(fieldIndex, fieldMeta);
          currentModelMeta = currentModelMeta.deepCopy();
          currentModelMeta.setFields(new ArrayList<>(fieldMetas));
        }
      }
    }
    return currentModelMeta;
  }

  private static DataModelMeta applyModelAnnotationDecorators(
      Class<?> metadataClass,
      DataModelMeta modelMeta,
      TypedAnnotationDecoratorContext context
  ) {
    var decorators = SPI.getCachedServiceStream(TypedModelAnnotationDecorator.class)
        .map(decorator -> (TypedModelAnnotationDecorator<?>) decorator)
        .sorted(Comparator.comparingInt(TypedModelAnnotationDecorator<?>::getOrder)
            .thenComparing(decorator -> decorator.getClass().getName()))
        .collect(Collectors.toList());

    DataModelMeta currentModelMeta = modelMeta;
    for (TypedModelAnnotationDecorator<?> decorator : decorators) {
      Annotation[] annotations = metadataClass.getAnnotationsByType(decorator.annotationType());
      for (Annotation annotation : annotations) {
        currentModelMeta = Objects.requireNonNull(
            decorator.decorateAnnotation(metadataClass, annotation, currentModelMeta, context),
            () -> decorator.getClass().getName() + " returned null model metadata"
        );
      }
    }
    return currentModelMeta;
  }

  private static int indexOfField(List<DataModelFieldMeta> fieldMetas, String fieldName) {
    for (int i = 0; i < fieldMetas.size(); i++) {
      if (fieldName.equals(fieldMetas.get(i).getName())) {
        return i;
      }
    }
    return -1;
  }

  /**
   * 如果不指定RawName 写死小驼峰转下划线（用户设置 > 转换器）
   *
   * @param rawName
   * @param name
   * @return
   */
  private static String rawNameConverter(String rawName, String name) {
    if (ObjectUtils.isNotEmpty(rawName)) {
      return rawName;
    }
    return Strings.toSnakeCase(name);
  }

  /**
   * 检查附加字段设置是否正确、拼接为Map
   *
   * @param extraProps
   * @return
   */
  private static Map<String, Object> buildExtraProps(String[] extraProps) {
    if (extraProps.length % 2 != 0) {
      throw new ModelMetadataException("属性长度不能被2整除");
    }

    Map<String, Object> map = new HashMap<>(8);
    for (int i = 0; i < extraProps.length; i += 2) {
      map.put(extraProps[i], extraProps[i + 1]);
    }
    return map;
  }

  /**
   * 构建字段元数据
   *
   * @param field 字段对象
   * @return 字段元数据
   */
  private static DataModelFieldMeta buildFieldMeta(Field field) {
    var fieldAnnotation = field.getAnnotation(com.ouroboros.data.annotation.Field.class);
    var relatedAnnotation = field.getAnnotation(Related.class);
    var ruleAnnotation = field.getAnnotation(Rule.class);
    var primaryKeyAnnotation = field.getAnnotation(PrimaryKey.class);
    var autoIncrementAnnotation = field.getAnnotation(AutoIncrement.class);

    String fieldName = field.getName();


    DataModelFieldMeta fieldMeta = new DataModelFieldMeta();
    fieldMeta.setName(fieldName);
    fieldMeta.setRawName(rawNameConverter(fieldAnnotation.rawName(), fieldMeta.getName()));
    fieldMeta.setLabel(StringUtils.isNotEmpty(fieldAnnotation.label()) ? fieldAnnotation.label() : null);
    fieldMeta.setRawType(ObjectUtils.isNotEmpty(fieldAnnotation.rawType()) ? fieldAnnotation.rawType() : null);
    fieldMeta.setIsNullable(fieldAnnotation.nullable());
    fieldMeta.setRules(ruleAnnotation != null ? Arrays.asList(ruleAnnotation.value()) : new ArrayList<>());
    fieldMeta.setDefaultValue(fieldAnnotation.defaultValueExpression());
    fieldMeta.setDecimalDigits(fieldAnnotation.decimalDigits() != -1 ? fieldAnnotation.decimalDigits() : null);
    fieldMeta.setSize(fieldAnnotation.size() != -1 ? fieldAnnotation.size() : null);
    fieldMeta.setIsUnsigned(fieldAnnotation.unsigned());

    // 检查附加字段设置是否正确、拼接为Map
    Map<String, Object> extraProps;
    try {
      extraProps = buildExtraProps(fieldAnnotation.extraProps());
    } catch (Exception e) {
      throw new FieldMetadataException(String.format("字段: %1s extraProps 属性设置错误：", fieldName), fieldName, null, e);
    }

    // 将主键注解信息保存到字段的extraProps中，供PrimaryKeyDecorator使用
    Map<String, Object> annotationProps = new HashMap<>();
    if (primaryKeyAnnotation != null) {
      annotationProps.put("isPrimaryKey", true);
      if (ObjectUtils.isNotEmpty(primaryKeyAnnotation.generator())) {
        annotationProps.put("primaryKeyGenerator", primaryKeyAnnotation.generator());
      }
    }
    if (autoIncrementAnnotation != null) {
      annotationProps.put("isAutoIncrement", true);
      fieldMeta.setIsAutoIncrement(true);
    }

    // 设置字段类型
    String fieldType;

    // 关联字段map
    Map<String, Object> relationMap = new HashMap<>(8);

    // 从@Related注解读取关联信息（类型、模型、关联键、被关联模型键）
    if (relatedAnnotation != null) {
      String relatedType = relatedAnnotation.type();
      if (ObjectUtils.isNotEmpty(relatedType)) {
        fieldType = relatedType;
      } else if (field.getType().isAssignableFrom(List.class)) {
        fieldType = "Collection";
      } else {
        fieldType = "Model";
      }

      String model, key, referenceKey;
      try {
        model = relatedAnnotation.model();
        if (ObjectUtils.isEmpty(model)) {
          throw new FieldMetadataException("model 属性不能为空！", fieldName, null);
        }

        key = ObjectUtils.isNotEmpty(relatedAnnotation.key()) ? relatedAnnotation.key() : null;

        referenceKey = ObjectUtils.isNotEmpty(relatedAnnotation.referenceKey()) ? relatedAnnotation.referenceKey() : null;
      } catch (Exception e) {
        throw new FieldMetadataException(String.format("字段: %1s @Related属性设置错误：", fieldName), fieldName, null, e);
      }

      relationMap.put("model", model);
      relationMap.put("key", key);
      relationMap.put("referenceKey", referenceKey);
    } else {
      fieldType = buildValueTypeName(field.getType(), fieldAnnotation).orElseThrow(
          () -> new FieldMetadataException(
              String.format("字段: %s 无法解析的数据类型 %s，请使用@Field注解指定数据类型", fieldName, field.getType().getSimpleName()),
              fieldName,
              null
          )
      );
    }

    // 设置字段类型
    fieldMeta.setType(fieldType);

    // 设置扩展属性（合并用户配置、关联信息和注解信息）
    fieldMeta.setExtraProps(Maps.merge(extraProps, relationMap, annotationProps));

    return fieldMeta;
  }

  /**
   * 通过字段类型或者字段注解获取字段类型
   *
   * @param clazz
   * @param fieldAnnotation
   * @return
   */
  private static Optional<String> buildValueTypeName(Class<?> clazz, com.ouroboros.data.annotation.Field fieldAnnotation) {
    if (fieldAnnotation != null && ObjectUtils.isNotEmpty(fieldAnnotation.type())) {
      return Optional.of(fieldAnnotation.type());
    }

    if (clazz == Boolean.class) {
      return Optional.of("Boolean");
    } else if (clazz == LocalDateTime.class) {
      return Optional.of("DateTime");
    } else if (clazz == LocalDate.class) {
      return Optional.of("Date");
    } else if (clazz == Date.class) {
      return Optional.of("Date");
    } else if (clazz == BigDecimal.class) {
      return Optional.of("Decimal");
    } else if (clazz == Double.class) {
      return Optional.of("Double");
    } else if (clazz == Float.class) {
      return Optional.of("Float");
    } else if (clazz == Integer.class) {
      return Optional.of("Integer");
    } else if (clazz == Long.class) {
      return Optional.of("Long");
    } else if (clazz == Short.class) {
      return Optional.of("Short");
    } else if (clazz == String.class) {
      return Optional.of("String");
    } else if (clazz == LocalTime.class) {
      return Optional.of("Time");
    } else if (clazz == Map.class) {
      return Optional.of("Map");
    } else if (clazz == List.class) {
      return Optional.of("List");
    } else if (clazz == JsonBag.class) {
      return Optional.of("Json");
    }

    return Optional.empty();
  }
}
