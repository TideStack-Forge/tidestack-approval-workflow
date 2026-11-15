package com.ouroboros.workflow.approval.definition.v2;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.ouroboros.workflow.approval.definition.FlowDefinition.Notification;
import com.ouroboros.workflow.approval.definition.FlowNode;

@JsonIgnoreProperties(ignoreUnknown = true)
public class FlowDefinitionV2 {
  Integer formatVersion = 2;
  String id;
  String name;
  String category = "";
  String icon = "";
  String description = "";
  String formType = "";
  String formKey = "";
  Notification notification;
  @JsonInclude(JsonInclude.Include.NON_NULL)
  JsonNode editorLayout;
  List<FlowNode> nodes = new ArrayList<FlowNode>();
  List<FlowEdgeV2> edges = new ArrayList<FlowEdgeV2>();

  public Integer getFormatVersion() {
    return formatVersion;
  }

  public void setFormatVersion(Integer formatVersion) {
    this.formatVersion = formatVersion;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getCategory() {
    return category;
  }

  public void setCategory(String category) {
    this.category = category;
  }

  public String getIcon() {
    return icon;
  }

  public void setIcon(String icon) {
    this.icon = icon;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getFormType() {
    return formType;
  }

  public void setFormType(String formType) {
    this.formType = formType;
  }

  public String getFormKey() {
    return formKey;
  }

  public void setFormKey(String formKey) {
    this.formKey = formKey;
  }

  public Notification getNotification() {
    return notification;
  }

  public void setNotification(Notification notification) {
    this.notification = notification;
  }

  public JsonNode getEditorLayout() {
    return editorLayout;
  }

  public void setEditorLayout(JsonNode editorLayout) {
    this.editorLayout = editorLayout;
  }

  public List<FlowNode> getNodes() {
    return nodes;
  }

  public void setNodes(List<FlowNode> nodes) {
    this.nodes = nodes;
  }

  public List<FlowEdgeV2> getEdges() {
    return edges;
  }

  public void setEdges(List<FlowEdgeV2> edges) {
    this.edges = edges;
  }
}
