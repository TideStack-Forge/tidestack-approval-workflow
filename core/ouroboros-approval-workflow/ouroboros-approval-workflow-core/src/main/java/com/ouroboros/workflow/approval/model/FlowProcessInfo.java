package com.ouroboros.workflow.approval.model;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

public class FlowProcessInfo {
  String instanceId;
  String flowKey;
  String status;
  String outcome;
  String starter;
  LocalDateTime startTime;
  LocalDateTime endTime;
  List<TaskInfo> tasks = Collections.emptyList();
  Boolean processable = false;
  String processableTaskId;
  String externalUrl;
  boolean diagramAvailable;
  boolean timelineAvailable;
  boolean cancellable;

  public String getExternalUrl() { return externalUrl; }
  public void setExternalUrl(String externalUrl) { this.externalUrl = externalUrl; }
  public boolean isDiagramAvailable() { return diagramAvailable; }
  public void setDiagramAvailable(boolean diagramAvailable) { this.diagramAvailable = diagramAvailable; }
  public boolean isTimelineAvailable() { return timelineAvailable; }
  public void setTimelineAvailable(boolean timelineAvailable) { this.timelineAvailable = timelineAvailable; }
  public boolean isCancellable() { return cancellable; }
  public void setCancellable(boolean cancellable) { this.cancellable = cancellable; }

  public String getInstanceId() {
    return instanceId;
  }

  public void setInstanceId(String instanceId) {
    this.instanceId = instanceId;
  }

  public String getFlowKey() {
    return flowKey;
  }

  public void setFlowKey(String flowKey) {
    this.flowKey = flowKey;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getOutcome() {
    return outcome;
  }

  public void setOutcome(String outcome) {
    this.outcome = outcome;
  }

  public String getStarter() {
    return starter;
  }

  public void setStarter(String starter) {
    this.starter = starter;
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

  public List<TaskInfo> getTasks() {
    return tasks;
  }

  public void setTasks(List<TaskInfo> tasks) {
    this.tasks = tasks == null ? Collections.emptyList() : tasks;
  }

  public Boolean isProcessable() {
    return processable;
  }

  public void setProcessable(Boolean processable) {
    this.processable = Boolean.TRUE.equals(processable);
  }

  public String getProcessableTaskId() {
    return processableTaskId;
  }

  public void setProcessableTaskId(String processableTaskId) {
    this.processableTaskId = processableTaskId;
  }

  public static class TaskInfo {
    String taskId;
    String taskName;
    String taskDefinitionKey;
    String nodeType;
    String assignee;
    LocalDateTime startTime;
    Boolean active;

    public String getTaskId() {
      return taskId;
    }

    public void setTaskId(String taskId) {
      this.taskId = taskId;
    }

    public String getTaskName() {
      return taskName;
    }

    public void setTaskName(String taskName) {
      this.taskName = taskName;
    }

    public String getTaskDefinitionKey() {
      return taskDefinitionKey;
    }

    public void setTaskDefinitionKey(String taskDefinitionKey) {
      this.taskDefinitionKey = taskDefinitionKey;
    }

    public String getNodeType() {
      return nodeType;
    }

    public void setNodeType(String nodeType) {
      this.nodeType = nodeType;
    }

    public String getAssignee() {
      return assignee;
    }

    public void setAssignee(String assignee) {
      this.assignee = assignee;
    }

    public LocalDateTime getStartTime() {
      return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
      this.startTime = startTime;
    }

    public Boolean isActive() {
      return active;
    }

    public void setActive(Boolean active) {
      this.active = active;
    }
  }
}
