package com.ouroboros.workflow.approval.model;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class FlowForm {
  String name;
  String title;
  List<FlowFormField> fields;
  List<ExpressionVariable> expressionVariables;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public List<FlowFormField> getFields() {
    return fields;
  }

  public List<ExpressionVariable> getExpressionVariables() {
    var sourceFields = Optional.ofNullable(getFields()).orElse(Collections.emptyList());
    return Optional.ofNullable(expressionVariables)
        .orElse(sourceFields.stream()
            .map(field -> new ExpressionVariable()
                .setName(field.getName())
                .setLabel(field.getLabel())
                .setType(field.getType())
                .setExtraProps(field.getExtraProps())
            )
            .collect(Collectors.toList()));
  }

  public FlowForm setExpressionVariables(List<ExpressionVariable> expressionVariables) {
    this.expressionVariables = expressionVariables;
    return this;
  }

  public void setFields(List<FlowFormField> fields) {
    this.fields = fields;
  }
}
