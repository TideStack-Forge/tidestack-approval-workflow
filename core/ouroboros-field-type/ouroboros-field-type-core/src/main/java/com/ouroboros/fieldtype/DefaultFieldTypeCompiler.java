package com.ouroboros.fieldtype;

import java.util.*;

import io.vavr.control.Either;

import com.ouroboros.data.model.DataModelFieldMeta;
import com.ouroboros.uischema.schema.FormItemNode;
import com.ouroboros.util.Json;

public class DefaultFieldTypeCompiler implements FieldTypeCompiler {
  private final FieldTypeTemplateResolver templateResolver = new FieldTypeTemplateResolver();

  @Override
  public Either<FieldTypeCompileException, CompiledFieldType> compile(FieldTypeDefinition definition,
                                                                      Map<String, Object> params,
                                                                      FieldTypeCatalogContext context) {
    var mergedParams = new LinkedHashMap<String, Object>();
    mergedParams.putAll(Optional.ofNullable(definition.getDefaultParams()).orElseGet(Collections::emptyMap));
    mergedParams.putAll(Optional.ofNullable(params).orElseGet(Collections::emptyMap));

    var dataTypeResult = Optional.ofNullable(definition.getDataType())
        .map(dataType -> templateResolver.resolveMap(dataType, mergedParams, definition))
        .orElseGet(() -> Either.right(null));
    if (dataTypeResult.isLeft()) {
      return Either.left(dataTypeResult.getLeft());
    }

    var inputUiResult = resolveFormItemTemplate(definition, mergedParams, true);
    if (inputUiResult.isLeft()) {
      return Either.left(inputUiResult.getLeft());
    }

    var displayUiResult = resolveFormItemTemplate(definition, mergedParams, false);
    if (displayUiResult.isLeft()) {
      return Either.left(displayUiResult.getLeft());
    }

    var primaryKeyGeneratorResult = resolvePrimaryKeyGenerator(definition, mergedParams);
    if (primaryKeyGeneratorResult.isLeft()) {
      return Either.left(primaryKeyGeneratorResult.getLeft());
    }

    return Either.right(new DefaultCompiledFieldType(
        buildDataFieldMeta(dataTypeResult.get(), definition),
        buildFormItem(inputUiResult.get()),
        buildFormItem(displayUiResult.get()),
        primaryKeyGeneratorResult.get()));
  }

  private Either<FieldTypeCompileException, Map<String, Object>> resolveFormItemTemplate(
      FieldTypeDefinition definition,
      Map<String, Object> mergedParams,
      boolean input) {
    var ui = definition.getUi();
    if (ui == null) {
      return Either.right(null);
    }
    var key = input ? "input" : "display";
    var template = ui.get(key);
    Map<?, ?> formItemTemplate = template instanceof Map<?, ?> templateMap ? templateMap : null;
    if (!input) {
      var inputTemplate = ui.get("input");
      if (formItemTemplate == null && inputTemplate instanceof Map<?, ?> inputTemplateMap) {
        formItemTemplate = copyAndMarkStatic(inputTemplateMap);
      }
    }
    if (formItemTemplate == null) {
      return Either.right(null);
    }
    return templateResolver.resolveMap(buildFormItemTemplate(formItemTemplate, definition), mergedParams, definition, true);
  }

  private Either<FieldTypeCompileException, String> resolvePrimaryKeyGenerator(FieldTypeDefinition definition,
                                                                               Map<String, Object> mergedParams) {
    if (definition.getPrimaryKeyGenerator() == null) {
      return Either.right(null);
    }
    return templateResolver.resolve(definition.getPrimaryKeyGenerator(), mergedParams, definition)
        .flatMap(value -> value instanceof String
            ? Either.right((String) value)
            : Either.left(new FieldTypeCompileException(
                "主键生成器模板必须解析为字符串",
                definition.getName(),
                definition.getSource(),
                definition.getSourceLocation(),
                "compile")));
  }

  private DataModelFieldMeta buildDataFieldMeta(Map<String, Object> dataType, FieldTypeDefinition definition) {
    if (dataType == null) {
      return null;
    }
    return Json.toBean(DataModelFieldMeta.class, Json.toJsonString(dataType));
  }

  private FormItemNode buildFormItem(Map<String, Object> ui) {
    if (ui == null) {
      return null;
    }
    var formItem = new FormItemNode();
    ui.forEach((key, value) -> {
      if ("type".equals(key)) {
        formItem.setType(String.valueOf(value));
      } else if ("static".equals(key) && value instanceof Boolean booleanValue) {
        formItem.setStatic(booleanValue);
      } else {
        formItem.setProp(key, value);
      }
    });
    return formItem;
  }

  private Map<String, Object> buildFormItemTemplate(Map<?, ?> ui, FieldTypeDefinition definition) {
    var formItemTemplate = new LinkedHashMap<String, Object>();
    ui.forEach((key, value) -> formItemTemplate.put(String.valueOf(key), value));
    if (definition.getValidations() != null) {
      formItemTemplate.put("validations", definition.getValidations());
    }
    if (definition.getValidationErrors() != null) {
      formItemTemplate.put("validationErrors", definition.getValidationErrors());
    }
    return formItemTemplate;
  }

  private Map<String, Object> copyAndMarkStatic(Map<?, ?> ui) {
    var copy = new LinkedHashMap<String, Object>();
    ui.forEach((key, value) -> copy.put(String.valueOf(key), value));
    copy.put("static", true);
    return copy;
  }
}
