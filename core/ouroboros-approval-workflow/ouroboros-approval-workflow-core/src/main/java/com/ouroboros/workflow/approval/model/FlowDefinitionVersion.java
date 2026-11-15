package com.ouroboros.workflow.approval.model;

import java.time.LocalDateTime;

public class FlowDefinitionVersion {
  Integer version;
  String processDefinitionId;
  String deploymentId;
  LocalDateTime deploymentTime;
  String deployedBy;
  String definition;

  public Integer getVersion() {
    return version;
  }

  public void setVersion(Integer version) {
    this.version = version;
  }

  public String getProcessDefinitionId() {
    return processDefinitionId;
  }

  public void setProcessDefinitionId(String processDefinitionId) {
    this.processDefinitionId = processDefinitionId;
  }

  public String getDeploymentId() {
    return deploymentId;
  }

  public void setDeploymentId(String deploymentId) {
    this.deploymentId = deploymentId;
  }

  public LocalDateTime getDeploymentTime() {
    return deploymentTime;
  }

  public void setDeploymentTime(LocalDateTime deploymentTime) {
    this.deploymentTime = deploymentTime;
  }

  public String getDeployedBy() {
    return deployedBy;
  }

  public void setDeployedBy(String deployedBy) {
    this.deployedBy = deployedBy;
  }

  public String getDefinition() {
    return definition;
  }

  public void setDefinition(String definition) {
    this.definition = definition;
  }
}
