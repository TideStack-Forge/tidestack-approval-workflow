package com.ouroboros.workflow.approval.model;

import java.time.LocalDateTime;

public class FlowInstanceDetail {
  String instanceId;
  String flowKey;
  String status;
  String outcome;
  String starter;

  LocalDateTime startTime;
  LocalDateTime endTime;

  String taskId;
  String taskName;
  boolean cancellable;

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
}
