package com.ouroboros.workflow.approval;

import java.util.List;
import java.util.Objects;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.uischema.schema.SchemaNode;
import com.ouroboros.workflow.approval.definition.FlowNode;
import com.ouroboros.workflow.approval.definition.NodeFormFieldPermission;
import com.ouroboros.workflow.approval.definition.graph.StructuredFlowDefinition;

import io.vavr.control.Try;

/** 根据平台流程定义解析发起表单，表单服务与字段权限不依赖执行引擎。 */
public final class FlowInitialFormResolver {
  private final List<FlowFormService> services;

  public FlowInitialFormResolver(List<FlowFormService> services) {
    this.services = services.stream().filter(Objects::nonNull).toList();
  }

  public Try<SchemaNode> resolve(StructuredFlowDefinition definition) {
    String formKey = definition.getFormKey();
    if (formKey == null || formKey.isBlank()) {
      return Try.failure(new BusinessException("流程未绑定表单"));
    }
    String declaredType = definition.getFormType();
    String formType = declaredType == null || declaredType.isBlank() ? "default" : declaredType;
    List<NodeFormFieldPermission> permissions = "default".equalsIgnoreCase(formType)
        ? definition.getStartFlowNode().map(FlowNode::getPermission).orElseGet(List::of)
        : List.of();
    return services.stream()
        .filter(service -> formType.equalsIgnoreCase(service.getType()))
        .findFirst()
        .map(service -> service.getInitialFormSchema(formKey, permissions))
        .orElseGet(() -> Try.failure(new FlowFormServiceUnavailableException(formType)));
  }
}
