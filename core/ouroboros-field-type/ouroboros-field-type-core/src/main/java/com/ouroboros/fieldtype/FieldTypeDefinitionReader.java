package com.ouroboros.fieldtype;

import java.util.*;
import java.util.stream.Collectors;

import io.vavr.control.Either;

public class FieldTypeDefinitionReader {

  public Either<FieldTypeDefinitionException, FieldTypeDefinition> read(Map<String, Object> rawDefine,
                                                                        FieldTypeDefinitionSource source,
                                                                        String sourceLocation) {
    var fieldName = Optional.ofNullable(rawDefine)
        .map(map -> map.get("name"))
        .map(Object::toString)
        .orElse(null);

    if (rawDefine == null) {
      return Either.left(new FieldTypeDefinitionException("字段定义不能为空", fieldName, source, sourceLocation, "read"));
    }

    if (fieldName == null || fieldName.trim().isEmpty()) {
      return Either.left(new FieldTypeDefinitionException("字段名称不能为空", null, source, sourceLocation, "read"));
    }

    var label = Optional.ofNullable(rawDefine.get("label"))
        .map(Object::toString)
        .orElse(null);
    if (label == null || label.trim().isEmpty()) {
      return Either.left(new FieldTypeDefinitionException("字段标签不能为空", fieldName, source, sourceLocation, "read"));
    }

    var definition = new FieldTypeDefinition();
    definition.setName(fieldName);
    definition.setLabel(label);
    definition.setDescription(Optional.ofNullable(rawDefine.get("description")).map(Object::toString).orElse(null));
    definition.setOrder(Optional.ofNullable(rawDefine.get("order"))
        .filter(Integer.class::isInstance)
        .map(Integer.class::cast)
        .orElse(0));
    definition.setIsPrimaryKeyType(Optional.ofNullable(rawDefine.get("isPrimaryKeyType"))
        .filter(Boolean.class::isInstance)
        .map(Boolean.class::cast)
        .orElse(false));
    definition.setDataType(asMap(rawDefine.get("dataType")));
    definition.setUi(asMap(rawDefine.get("ui")));
    definition.setValidations(asMap(rawDefine.get("validations")));
    definition.setValidationErrors(asMap(rawDefine.get("validationErrors")));
    definition.setParamsUiSchema(asListOfMaps(rawDefine.get("paramsUiSchema")));
    definition.setPrimaryKeyGenerator(Optional.ofNullable(rawDefine.get("primaryKeyGenerator")).map(Object::toString).orElse(null));
    definition.setDefaultParams(extractDefaultParams(definition.getParamsUiSchema()));
    definition.setSource(source);
    definition.setSourceLocation(sourceLocation);

    return Either.right(definition);
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> asMap(Object value) {
    return value instanceof Map<?, ?> map
        ? map.entrySet().stream().collect(Collectors.toMap(
            entry -> String.valueOf(entry.getKey()),
            Map.Entry::getValue,
            (a, b) -> b,
            LinkedHashMap::new))
        : null;
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> asListOfMaps(Object value) {
    if (!(value instanceof List<?> list)) {
      return new ArrayList<>();
    }
    return list.stream()
        .filter(Map.class::isInstance)
        .map(item -> ((Map<Object, Object>) item).entrySet().stream().collect(Collectors.toMap(
            entry -> String.valueOf(entry.getKey()),
            Map.Entry::getValue,
            (a, b) -> b,
            LinkedHashMap::new)))
        .collect(Collectors.toList());
  }

  private Map<String, Object> extractDefaultParams(List<Map<String, Object>> paramsUiSchema) {
    var defaultParams = new LinkedHashMap<String, Object>();
    paramsUiSchema.forEach(param -> {
      var name = param.get("name");
      if (name != null && param.containsKey("value")) {
        defaultParams.put(name.toString(), param.get("value"));
      }
    });
    return defaultParams;
  }
}
