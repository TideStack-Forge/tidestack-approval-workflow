package com.ouroboros.workflow.approval.testkit.snapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import com.ouroboros.workflow.approval.model.TaskState;

public class TaskView {
  private final String taskId;
  private final String nodeId;
  private final String assignee;
  private final List<String> candidateUsers;
  private final TaskState state;
  private final String actionName;
  private final String comment;

  public TaskView(String taskId, String nodeId, String assignee, List<String> candidateUsers, TaskState state,
      String actionName, String comment) {
    this.taskId = taskId;
    this.nodeId = nodeId;
    this.assignee = assignee;
    this.candidateUsers = immutableList(candidateUsers);
    this.state = state;
    this.actionName = actionName;
    this.comment = comment;
  }

  public String getTaskId() {
    return taskId;
  }

  public String getNodeId() {
    return nodeId;
  }

  public String getAssignee() {
    return assignee;
  }

  public List<String> getCandidateUsers() {
    return candidateUsers;
  }

  public TaskState getState() {
    return state;
  }

  public String getActionName() {
    return actionName;
  }

  public String getComment() {
    return comment;
  }

  @Override
  public boolean equals(Object object) {
    if (this == object) {
      return true;
    }
    if (!(object instanceof TaskView)) {
      return false;
    }
    TaskView taskView = (TaskView) object;
    return Objects.equals(taskId, taskView.taskId)
        && Objects.equals(nodeId, taskView.nodeId)
        && Objects.equals(assignee, taskView.assignee)
        && Objects.equals(candidateUsers, taskView.candidateUsers)
        && state == taskView.state
        && Objects.equals(actionName, taskView.actionName)
        && Objects.equals(comment, taskView.comment);
  }

  @Override
  public int hashCode() {
    return Objects.hash(taskId, nodeId, assignee, candidateUsers, state, actionName, comment);
  }

  private List<String> immutableList(List<String> source) {
    ArrayList<String> copy = new ArrayList<String>();
    if (source != null) {
      copy.addAll(source);
    }
    return Collections.unmodifiableList(copy);
  }
}
