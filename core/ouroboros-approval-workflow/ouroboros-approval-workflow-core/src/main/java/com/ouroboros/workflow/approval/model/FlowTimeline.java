package com.ouroboros.workflow.approval.model;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import com.ouroboros.workflow.approval.definition.FlowNode;

public class FlowTimeline {
  private FlowNode flowNode;

  private List<TaskInstance> taskInstances;

  private TaskState state;

  public FlowNode getFlowNode() {
    return flowNode;
  }

  public void setFlowNode(FlowNode flowNode) {
    this.flowNode = flowNode;
  }

  public List<TaskInstance> getTaskInstances() {
    return taskInstances;
  }

  public void setTaskInstances(List<TaskInstance> taskInstances) {
    this.taskInstances = taskInstances;
  }

  public TaskState getState() {
    return state;
  }

  public void setState(TaskState state) {
    this.state = state;
  }

  public static class TaskInstance {
    private String instanceId;
    private String taskId;
    private String assignee;
    private List<String> candidateUsers = Collections.emptyList();
    private TaskState state;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String comment;

    public String getInstanceId() {
      return instanceId;
    }

    public void setInstanceId(String instanceId) {
      this.instanceId = instanceId;
    }

    public String getTaskId() {
      return taskId;
    }

    public void setTaskId(String taskId) {
      this.taskId = taskId;
    }

    public String getAssignee() {
      return assignee;
    }

    public void setAssignee(String assignee) {
      this.assignee = assignee;
    }

    public List<String> getCandidateUsers() {
      return candidateUsers;
    }

    public void setCandidateUsers(List<String> candidateUsers) {
      this.candidateUsers = candidateUsers;
    }

    public TaskState getState() {
      return state;
    }

    public void setState(TaskState state) {
      this.state = state;
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

    public String getComment() {
      return comment;
    }

    public void setComment(String comment) {
      this.comment = comment;
    }
  }
}
