package com.ouroboros.workflow.approval.definition.metadata;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.ouroboros.workflow.approval.definition.FlowDefinition.Notification;
import com.ouroboros.workflow.approval.definition.FlowNode;
import com.ouroboros.workflow.approval.definition.v2.FlowEdgeV2;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ApprovalWorkflowDefinitionMetadata {
  public static final int FORMAT_VERSION = 1;

  private Integer formatVersion = FORMAT_VERSION;
  private String name;
  @JsonAlias("label")
  private String title;
  private String description = "";
  private Integer version = 1;
  @JsonAlias("isEnabled")
  private Boolean enabled = true;
  private FormBinding form = new FormBinding();
  private Notification notification;
  private JsonNode editorLayout;
  private List<FlowNode> nodes = new ArrayList<>();
  private List<FlowEdgeV2> edges = new ArrayList<>();

  public Integer getFormatVersion() {
    return formatVersion;
  }

  public void setFormatVersion(Integer formatVersion) {
    this.formatVersion = formatVersion;
  }

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

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public Integer getVersion() {
    return version;
  }

  public void setVersion(Integer version) {
    this.version = version;
  }

  public Boolean getEnabled() {
    return enabled;
  }

  public void setEnabled(Boolean enabled) {
    this.enabled = enabled;
  }

  public FormBinding getForm() {
    return form;
  }

  public void setForm(FormBinding form) {
    this.form = form == null ? new FormBinding() : form;
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
    this.nodes = nodes == null ? new ArrayList<>() : nodes;
  }

  public List<FlowEdgeV2> getEdges() {
    return edges;
  }

  public void setEdges(List<FlowEdgeV2> edges) {
    this.edges = edges == null ? new ArrayList<>() : edges;
  }

  @JsonProperty("formType")
  public void setLegacyFormType(String formType) {
    getForm().setFormType(formType);
  }

  @JsonProperty("formKey")
  public void setLegacyFormKey(String formKey) {
    getForm().setFormId(formKey);
  }

  @JsonIgnore
  public boolean isEnabledEffective() {
    return enabled == null || enabled;
  }

  @JsonIgnore
  public String getDisplayTitle() {
    return title == null || title.isBlank() ? name : title;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class FormBinding {
    private String formType = "";
    @JsonAlias("formKey")
    private String formId = "";

    public String getFormType() {
      return formType;
    }

    public void setFormType(String formType) {
      this.formType = formType;
    }

    public String getFormId() {
      return formId;
    }

    public void setFormId(String formId) {
      this.formId = formId;
    }
  }
}
