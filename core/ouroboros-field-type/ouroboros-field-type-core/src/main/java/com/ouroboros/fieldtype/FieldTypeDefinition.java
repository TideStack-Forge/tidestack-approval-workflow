package com.ouroboros.fieldtype;

import java.util.*;

public class FieldTypeDefinition {
  private String name;
  private String label;
  private String description;
  private Integer order;
  private Boolean isPrimaryKeyType;
  private Map<String, Object> dataType;
  private Map<String, Object> ui;
  private Map<String, Object> validations;
  private Map<String, Object> validationErrors;
  private List<Map<String, Object>> paramsUiSchema = new ArrayList<>();
  private String primaryKeyGenerator;
  private Map<String, Object> defaultParams = new LinkedHashMap<>();
  private FieldTypeDefinitionSource source;
  private String sourceLocation;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getLabel() {
    return label;
  }

  public void setLabel(String label) {
    this.label = label;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public Integer getOrder() {
    return order;
  }

  public void setOrder(Integer order) {
    this.order = order;
  }

  public Boolean getIsPrimaryKeyType() {
    return isPrimaryKeyType;
  }

  public void setIsPrimaryKeyType(Boolean primaryKeyType) {
    isPrimaryKeyType = primaryKeyType;
  }

  public Map<String, Object> getDataType() {
    return dataType;
  }

  public void setDataType(Map<String, Object> dataType) {
    this.dataType = dataType;
  }

  public Map<String, Object> getUi() {
    return ui;
  }

  public void setUi(Map<String, Object> ui) {
    this.ui = ui;
  }

  public Map<String, Object> getValidations() {
    return validations;
  }

  public void setValidations(Map<String, Object> validations) {
    this.validations = validations;
  }

  public Map<String, Object> getValidationErrors() {
    return validationErrors;
  }

  public void setValidationErrors(Map<String, Object> validationErrors) {
    this.validationErrors = validationErrors;
  }

  public List<Map<String, Object>> getParamsUiSchema() {
    return paramsUiSchema;
  }

  public void setParamsUiSchema(List<Map<String, Object>> paramsUiSchema) {
    this.paramsUiSchema = paramsUiSchema;
  }

  public String getPrimaryKeyGenerator() {
    return primaryKeyGenerator;
  }

  public void setPrimaryKeyGenerator(String primaryKeyGenerator) {
    this.primaryKeyGenerator = primaryKeyGenerator;
  }

  public Map<String, Object> getDefaultParams() {
    return defaultParams;
  }

  public void setDefaultParams(Map<String, Object> defaultParams) {
    this.defaultParams = defaultParams;
  }

  public FieldTypeDefinitionSource getSource() {
    return source;
  }

  public void setSource(FieldTypeDefinitionSource source) {
    this.source = source;
  }

  public String getSourceLocation() {
    return sourceLocation;
  }

  public void setSourceLocation(String sourceLocation) {
    this.sourceLocation = sourceLocation;
  }
}
