package com.ouroboros.workflow.approval.testkit.snapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import com.ouroboros.workflow.approval.model.TaskState;

public class TaskEventView {
  private final String instanceId;
  private final String flowKey;
  private final String taskId;
  private final String nodeId;
  private final TaskState state;
  private final String assignee;
  private final List<String> candidateUsers;
  private final String action;
  private final String comment;
  private final long sequence;

  public TaskEventView(String instanceId, String taskId, String nodeId, TaskState state, String assignee, String action,
      String comment, long sequence) {
    this(instanceId, null, taskId, nodeId, state, assignee, Collections.<String>emptyList(), action, comment, sequence);
  }

  public TaskEventView(String instanceId, String flowKey, String taskId, String nodeId, TaskState state, String assignee,
      String action, String comment, long sequence) {
    this(instanceId, flowKey, taskId, nodeId, state, assignee, Collections.<String>emptyList(), action, comment, sequence);
  }

  public TaskEventView(String instanceId, String taskId, String nodeId, TaskState state, String assignee,
      List<String> candidateUsers, String action, String comment, long sequence) {
    this(instanceId, null, taskId, nodeId, state, assignee, candidateUsers, action, comment, sequence);
  }

  public TaskEventView(String instanceId, String flowKey, String taskId, String nodeId, TaskState state, String assignee,
      List<String> candidateUsers, String action, String comment, long sequence) {
    this.instanceId = instanceId;
    this.flowKey = flowKey;
    this.taskId = taskId;
    this.nodeId = nodeId;
    this.state = state;
    this.assignee = assignee;
    this.candidateUsers = immutableList(candidateUsers);
    this.action = action;
    this.comment = comment;
    this.sequence = sequence;
  }

  public String getInstanceId() {
    return instanceId;
  }

  public String getFlowKey() {
    return flowKey;
  }

  public String getTaskId() {
    return taskId;
  }

  public String getNodeId() {
    return nodeId;
  }

  public TaskState getState() {
    return state;
  }

  public String getAssignee() {
    return assignee;
  }

  public List<String> getCandidateUsers() {
    return candidateUsers;
  }

  public String getAction() {
    return action;
  }

  public String getComment() {
    return comment;
  }

  public long getSequence() {
    return sequence;
  }

  @Override
  public boolean equals(Object object) {
    if (this == object) {
      return true;
    }
    if (!(object instanceof TaskEventView)) {
      return false;
    }
    TaskEventView that = (TaskEventView) object;
    return sequence == that.sequence
        && Objects.equals(instanceId, that.instanceId)
        && Objects.equals(flowKey, that.flowKey)
        && Objects.equals(taskId, that.taskId)
        && Objects.equals(nodeId, that.nodeId)
        && state == that.state
        && Objects.equals(assignee, that.assignee)
        && Objects.equals(candidateUsers, that.candidateUsers)
        && Objects.equals(action, that.action)
        && Objects.equals(comment, that.comment);
  }

  @Override
  public int hashCode() {
    return Objects.hash(instanceId, flowKey, taskId, nodeId, state, assignee, candidateUsers, action, comment, sequence);
  }

  private List<String> immutableList(List<String> source) {
    ArrayList<String> copy = new ArrayList<String>();
    if (source != null) {
      copy.addAll(source);
    }
    return Collections.unmodifiableList(copy);
  }
}
