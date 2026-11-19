package com.ouroboros.workflow.approval.form;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.ouroboros.util.Json;
import com.ouroboros.workflow.approval.model.ExpressionVariable;
import com.ouroboros.workflow.approval.model.FlowForm;
import com.ouroboros.workflow.approval.model.FlowFormField;

public final class FlowFormSourceAssembler {
  public Map<String, Object> build(
      String formType,
      String formKey,
      FlowForm form) {
    List<Map<String, Object>> fields = (form == null ? List.<FlowFormField>of() : form.getFields()).stream()
        .map(field -> buildWorkflowField(field, form)).toList();
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("sourceKind", "form-fields");
    data.put("revisionToken", buildRevisionToken(formType, formKey, form, fields));
    data.put("fields", fields);
    data.put("boundForm", buildBoundForm(formType, formKey, form));
    data.put("boundFormSummary", form == null ? "" : boundFormTitle(form, formKey) + "（" + formKey + "）");
    return data;
  }

  private String buildRevisionToken(String formType, String formKey, FlowForm form, List<Map<String, Object>> fields) {
    Map<String, Object> material = new LinkedHashMap<>();
    material.put("formType", formType);
    material.put("formKey", formKey);
    material.put("formName", form == null ? null : form.getName());
    material.put("formTitle", form == null ? null : form.getTitle());
    material.put("fields", Optional.ofNullable(fields).orElseGet(Collections::emptyList).stream()
        .map(this::sortValue)
        .collect(Collectors.toList()));
    return "form-fields:" + formType + ":" + formKey + ":" + sha256(Json.toJsonString(sortValue(material)));
  }

  private Object sortValue(Object value) {
    if (value instanceof Map<?, ?> map) {
      Map<String, Object> sorted = new java.util.TreeMap<>();
      map.forEach((key, item) -> sorted.put(String.valueOf(key), sortValue(item)));
      return sorted;
    }
    if (value instanceof List<?> list) {
      return list.stream()
          .map(this::sortValue)
          .collect(Collectors.toList());
    }
    return value;
  }

  private String sha256(String value) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
      StringBuilder builder = new StringBuilder();
      for (byte b : digest) {
        builder.append(String.format("%02x", b));
      }
      return "sha256:" + builder;
    } catch (NoSuchAlgorithmException e) {
      throw new com.ouroboros.exception.InternalException("SHA-256 digest is unavailable", e);
    }
  }

  private Map<String, Object> buildBoundForm(String formType, String formKey, FlowForm form) {
    if (form == null) {
      return null;
    }
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("formType", formType);
    data.put("formKey", formKey);
    data.put("formTitle", form.getTitle());
    return data;
  }

  private String boundFormTitle(FlowForm form, String formKey) {
    return Optional.ofNullable(form.getTitle()).orElse(formKey);
  }

  private Map<String, Object> buildWorkflowField(FlowFormField field, FlowForm form) {
    Optional<ExpressionVariable> expressionVariable = findExpressionVariable(field, form);
    Map<String, Object> item = new LinkedHashMap<>();
    item.put("name", field.getName());
    item.put("value", field.getName());
    item.put("label", field.getLabel() != null ? field.getLabel() : expressionVariable.map(ExpressionVariable::getLabel).orElse(field.getName()));
    item.put("type", field.getType() != null ? field.getType() : expressionVariable.map(ExpressionVariable::getType).orElse(null));
    expressionVariable.map(ExpressionVariable::getExtraProps).ifPresent(item::putAll);
    item.putAll(field.getExtraProps());
    item.put("usages", buildWorkflowFieldUsages(field, form));
    return item;
  }

  private Optional<ExpressionVariable> findExpressionVariable(FlowFormField field, FlowForm form) {
    return form.getExpressionVariables().stream()
        .filter(variable -> field.getName().equals(variable.getName()))
        .findFirst();
  }

  private List<String> buildWorkflowFieldUsages(FlowFormField field, FlowForm form) {
    Object rawUsages = field.getExtraProps().get("usages");
    List<String> usages = rawUsages instanceof List
        ? ((List<?>) rawUsages).stream()
            .filter(String.class::isInstance)
            .map(String.class::cast)
            .collect(Collectors.toCollection(ArrayList::new))
        : new ArrayList<>(Collections.singletonList("permission"));
    if (form.getExpressionVariables().stream().anyMatch(variable -> field.getName().equals(variable.getName()))
        && !usages.contains("condition")) {
      usages.add("condition");
    }
    return usages;
  }
}
