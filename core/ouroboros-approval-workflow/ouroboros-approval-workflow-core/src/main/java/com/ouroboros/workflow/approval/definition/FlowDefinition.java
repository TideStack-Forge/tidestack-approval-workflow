package com.ouroboros.workflow.approval.definition;

import java.beans.Transient;
import java.util.List;

import org.apache.commons.lang3.ObjectUtils;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ouroboros.workflow.approval.util.DefinitionUtil;
import com.ouroboros.workflow.approval.util.TreeGraphNode;

public class FlowDefinition {
  String id;
  String name;
  String category = "";
  String icon = "";
  String description = "";
  String formType = "";
  String formKey = "";
  Notification notification;
  List<FlowNode> nodes;

  @JsonIgnore
  List<TreeGraphNode> treeGraphNodes;
  @JsonIgnore
  TreeGraphNode endNode;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
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

  public List<FlowNode> getNodes() {
    return nodes;
  }

  public void setNodes(List<FlowNode> nodes) {
    this.nodes = nodes;
  }

  @Transient
  public List<TreeGraphNode> getTreeGraphNodes() {
    if (ObjectUtils.isEmpty(treeGraphNodes)) {
      treeGraphNodes = DefinitionUtil.traversePreOrder(nodes);
    }
    return treeGraphNodes;
  }

  @Transient
  public TreeGraphNode getStartNode() {
    return getTreeGraphNodes().get(0);
  }

  @Transient
  public TreeGraphNode getEndNode() {
    if (endNode == null) {
      endNode = getTreeGraphNodes()
          .stream()
          .filter(TreeGraphNode::isEndNode)
          .findFirst()
          .orElse(null);
    }
    return endNode;
  }

  public static class Notification {
    String taskCreatedTemplate;
    String taskTimeoutRemindTemplate;
    String instanceCompletedTemplate;
    String instanceRejectedTemplate;

    public String getTaskCreatedTemplate() {
      return taskCreatedTemplate;
    }

    public void setTaskCreatedTemplate(String taskCreatedTemplate) {
      this.taskCreatedTemplate = taskCreatedTemplate;
    }

    public String getTaskTimeoutRemindTemplate() {
      return taskTimeoutRemindTemplate;
    }

    public void setTaskTimeoutRemindTemplate(String taskTimeoutRemindTemplate) {
      this.taskTimeoutRemindTemplate = taskTimeoutRemindTemplate;
    }

    public String getInstanceCompletedTemplate() {
      return instanceCompletedTemplate;
    }

    public void setInstanceCompletedTemplate(String instanceCompletedTemplate) {
      this.instanceCompletedTemplate = instanceCompletedTemplate;
    }

    public String getInstanceRejectedTemplate() {
      return instanceRejectedTemplate;
    }

    public void setInstanceRejectedTemplate(String instanceRejectedTemplate) {
      this.instanceRejectedTemplate = instanceRejectedTemplate;
    }
  }
}
