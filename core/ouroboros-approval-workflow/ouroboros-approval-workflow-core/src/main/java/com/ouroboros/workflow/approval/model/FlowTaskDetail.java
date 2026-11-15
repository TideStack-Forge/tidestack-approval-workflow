package com.ouroboros.workflow.approval.model;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class FlowTaskDetail {
  String id;
  String name;
  String flowName;
  String flowKey;
  String taskDefinitionKey;
  String instanceId;
  LocalDateTime startTime;
  LocalDateTime endTime;
  String assignee;
  List<String> candidateUsers = Collections.emptyList();
  List<String> actions = Collections.emptyList();
  Map<String, Object> fields = Collections.emptyMap();
  String starter;
  Boolean active;

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

  public String getFlowName() {
    return flowName;
  }

  public void setFlowName(String flowName) {
    this.flowName = flowName;
  }

  public String getFlowKey() {
    return flowKey;
  }

  public void setFlowKey(String flowKey) {
    this.flowKey = flowKey;
  }

  public String getTaskDefinitionKey() {
    return taskDefinitionKey;
  }

  public void setTaskDefinitionKey(String taskDefinitionKey) {
    this.taskDefinitionKey = taskDefinitionKey;
  }

  public String getInstanceId() {
    return instanceId;
  }

  public void setInstanceId(String instanceId) {
    this.instanceId = instanceId;
  }

  public LocalDateTime getStartTime() {
    return startTime;
  }

  public void setStartTime(LocalDateTime startTime) {
    this.startTime = startTime;
  }

  public LocalDateTime getEndTime() {
    return endTime;
  }

  public void setEndTime(LocalDateTime endTime) {
    this.endTime = endTime;
  }

  public String getAssignee() {
    return assignee;
  }

  public void setAssignee(String assignee) {
    this.assignee = assignee;
  }

  public String getStarter() {
    return starter;
  }

  public void setStarter(String starter) {
    this.starter = starter;
  }

  public List<String> getCandidateUsers() {
    return candidateUsers;
  }

  public void setCandidateUsers(List<String> candidateUsers) {
    this.candidateUsers = candidateUsers;
  }

  public List<String> getActions() {
    return actions;
  }

  public void setActions(List<String> actions) {
    this.actions = actions == null ? Collections.emptyList() : actions;
  }

  public Map<String, Object> getFields() {
    return fields;
  }

  public void setFields(Map<String, Object> fields) {
    this.fields = fields == null ? Collections.emptyMap() : fields;
  }

  public Boolean isActive() {
    return active;
  }

  public FlowTaskDetail setActive(Boolean active) {
    this.active = active;
    return this;
  }
}
