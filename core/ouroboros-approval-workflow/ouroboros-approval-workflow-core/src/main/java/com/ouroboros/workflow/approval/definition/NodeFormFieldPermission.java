package com.ouroboros.workflow.approval.definition;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class NodeFormFieldPermission {
  String name;
  String label;
  Boolean visible;
  Boolean writable;
  Boolean required;

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

  public Boolean isVisible() {
    return visible;
  }

  public void setVisible(Boolean visible) {
    this.visible = visible;
  }

  public Boolean isWritable() {
    return writable;
  }

  public void setWritable(Boolean writable) {
    this.writable = writable;
  }

  public Boolean isRequired() {
    return required;
  }

  public void setRequired(Boolean required) {
    this.required = required;
  }
}
