package com.ouroboros.workflow.approval.form;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.ouroboros.data.model.DataModelFieldMeta;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.fieldtype.CompiledFieldType;
import com.ouroboros.fieldtype.FieldTypeCatalog;
import com.ouroboros.fieldtype.FieldTypeCatalogContext;
import com.ouroboros.util.ResourceUtil;
import com.ouroboros.workflow.approval.model.ExpressionVariable;

/** The common projection used by dynamic forms and custom form descriptions. */
public final class ApprovalFormFieldProjector {
  private static final Map<String, Set<String>> OPERATORS = loadOperators();
  private final FieldTypeCatalog catalog;
  private final FieldTypeCatalogContext context;

  public ApprovalFormFieldProjector(FieldTypeCatalog catalog, FieldTypeCatalogContext context) {
    this.catalog = catalog;
    this.context = context;
  }

  public boolean supportsCondition(String type) {
    return OPERATORS.containsKey(type) && catalog.getMetadata(context, type).isPresent();
  }

  public CompiledFieldType compile(String type, Map<String, Object> params) {
    return catalog.compile(context, type, params).fold(error -> {
      throw new BusinessException("审批表单字段类型或参数不合法: " + type + ": " + error.getMessage(), error);
    }, value -> value);
  }

  public Optional<ExpressionVariable> condition(
      String name, String label, String type, CompiledFieldType compiled) {
    if (!supportsCondition(type) || compiled.toInputFormItem() == null) {
      return Optional.empty();
    }
    var input = compiled.toInputFormItem();
    Map<String, Object> props = new LinkedHashMap<>(input.getProps());
    props.remove("type");
    props.put("operators", OPERATORS.get(type));
    Optional.ofNullable(compiled.toDataFieldMeta()).map(DataModelFieldMeta::getType)
        .ifPresent(valueType -> props.put("valueType", valueType));
    return Optional.of(new ExpressionVariable().setName(name).setLabel(label).setType(input.getType()).setExtraProps(props));
  }

  private static Map<String, Set<String>> loadOperators() {
    Map<String, Set<String>> result = new LinkedHashMap<>();
    ResourceUtil.getResourceContentLinesList("META-INF/expressionvariables/BasicFieldOperators").forEach(lines -> {
      try (lines) {
        lines.map(String::trim).filter(line -> !line.isEmpty() && !line.startsWith("#"))
            .map(line -> line.split(":"))
            .filter(parts -> parts.length == 2)
            .forEach(parts -> {
              var operators = result.computeIfAbsent(parts[0].trim(), key -> new LinkedHashSet<>());
              Arrays.stream(parts[1].split(",")).map(String::trim).map(String::toLowerCase).forEach(operators::add);
            });
      }
    });
    return result;
  }
}
