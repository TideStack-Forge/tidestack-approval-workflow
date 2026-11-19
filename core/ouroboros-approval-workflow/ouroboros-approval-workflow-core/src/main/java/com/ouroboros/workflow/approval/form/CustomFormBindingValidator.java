package com.ouroboros.workflow.approval.form;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.workflow.approval.FlowFormProvider;
import com.ouroboros.workflow.approval.definition.condition.CombinationCondition;
import com.ouroboros.workflow.approval.definition.condition.CompareCondition;
import com.ouroboros.workflow.approval.definition.condition.Condition;
import com.ouroboros.workflow.approval.definition.metadata.ApprovalWorkflowDefinitionMetadata;
import com.ouroboros.workflow.approval.model.ExpressionVariable;

/** Deployment validation only; invalid references remain editable in saved drafts. */
public final class CustomFormBindingValidator {
  private CustomFormBindingValidator() {}

  public static void validate(ApprovalWorkflowDefinitionMetadata definition, List<FlowFormProvider> providers) {
    if (!CustomFlowFormProvider.TYPE.equals(definition.getForm().getFormType())) {
      return;
    }
    var provider = providers.stream().filter(value -> CustomFlowFormProvider.TYPE.equals(value.getType())).findFirst()
        .orElseThrow(() -> new BusinessException("自定义审批表单来源不可用"));
    var form = provider.get(definition.getForm().getFormId())
        .orElseThrow(() -> new BusinessException("自定义审批表单不存在: " + definition.getForm().getFormId()));
    Set<String> fields = form.getFields().stream().map(value -> value.getName()).collect(Collectors.toSet());
    Map<String, ExpressionVariable> conditions = form.getExpressionVariables().stream()
        .collect(Collectors.toMap(ExpressionVariable::getName, Function.identity()));
    for (var node : definition.getNodes()) {
      if (node.getPermission() != null) {
        node.getPermission().forEach(permission -> {
          if (!fields.contains(permission.getName())) {
            throw new BusinessException("流程节点 " + node.getId() + " 引用了失效字段: " + permission.getName());
          }
        });
      }
    }
    definition.getEdges().forEach(edge -> validateCondition(edge.getCondition(), conditions, edge.getId()));
  }

  private static void validateCondition(Condition condition, Map<String, ExpressionVariable> fields, String edge) {
    if (condition == null) {
      return;
    }
    if (condition instanceof CombinationCondition group) {
      group.getChildren().forEach(child -> validateCondition(child, fields, edge));
      return;
    }
    if (!(condition instanceof CompareCondition comparison)) {
      throw new BusinessException("流程连线 " + edge + " 使用了不支持的条件");
    }
    String name = comparison.getLeft() == null ? null : comparison.getLeft().getField();
    var field = fields.get(name);
    if (field == null) {
      throw new BusinessException("流程连线 " + edge + " 引用了不可用于条件的字段: " + name);
    }
    Object supported = field.getExtraProps().get("operators");
    if (!(supported instanceof Collection<?> operators) || !operators.contains(comparison.getOp())) {
      throw new BusinessException("流程连线 " + edge + " 字段 " + name + " 不支持操作符: " + comparison.getOp());
    }
    String operator = comparison.getOp();
    if (!Set.of("is_empty", "is_not_empty").contains(operator)) {
      boolean expectsList = Set.of("between", "not_between", "select_any_in", "select_not_any_in").contains(operator);
      if (expectsList != (comparison.getRight() instanceof List<?>)) {
        throw new BusinessException("流程连线 " + edge + " 字段 " + name + " 的条件值形状不匹配: " + operator);
      }
      if (comparison.getRight() instanceof List<?> values) {
        values.forEach(value -> validateValue(field, value, edge));
      } else {
        validateValue(field, comparison.getRight(), edge);
      }
    }
    // Keep the canonical operator parser and operand-shape rules authoritative.
    comparison.toExpression();
  }

  private static void validateValue(ExpressionVariable field, Object value, String edge) {
    String valueType = String.valueOf(field.getExtraProps().get("valueType"));
    if ((Set.of("Integer", "Long", "Float", "Double", "Decimal").contains(valueType) && !(value instanceof Number))
        || ("Boolean".equals(valueType) && !(value instanceof Boolean))
        || ("String".equals(valueType) && !(value instanceof String))) {
      throw new BusinessException("流程连线 " + edge + " 字段 " + field.getName() + " 的条件值类型不匹配: " + valueType);
    }
    Object rawOptions = field.getExtraProps().get("options");
    if (rawOptions instanceof List<?> options && !options.isEmpty()
        && options.stream().noneMatch(option -> option instanceof Map<?, ?> item
            && java.util.Objects.equals(item.get("value"), value))) {
      throw new BusinessException("流程连线 " + edge + " 字段 " + field.getName() + " 的条件选项已失效: " + value);
    }
  }
}
