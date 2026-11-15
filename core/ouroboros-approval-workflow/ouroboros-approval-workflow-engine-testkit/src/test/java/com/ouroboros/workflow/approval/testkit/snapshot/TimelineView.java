package com.ouroboros.workflow.approval.testkit.snapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import com.ouroboros.workflow.approval.model.TaskState;

public class TimelineView {
  private final String instanceId;
  private final List<NodeTimelineView> nodes;

  public TimelineView(String instanceId, List<NodeTimelineView> nodes) {
    this.instanceId = instanceId;
    this.nodes = immutableList(nodes);
  }

  public String getInstanceId() {
    return instanceId;
  }

  public List<NodeTimelineView> getNodes() {
    return nodes;
  }

  @Override
  public boolean equals(Object object) {
    if (this == object) {
      return true;
    }
    if (!(object instanceof TimelineView)) {
      return false;
    }
    TimelineView that = (TimelineView) object;
    return Objects.equals(instanceId, that.instanceId) && Objects.equals(nodes, that.nodes);
  }

  @Override
  public int hashCode() {
    return Objects.hash(instanceId, nodes);
  }

  private <T> List<T> immutableList(List<T> source) {
    ArrayList<T> copy = new ArrayList<T>();
    if (source != null) {
      copy.addAll(source);
    }
    return Collections.unmodifiableList(copy);
  }

  public static class NodeTimelineView {
    private final String nodeId;
    private final TaskState state;
    private final List<TaskView> taskInstances;

    public NodeTimelineView(String nodeId, TaskState state, List<TaskView> taskInstances) {
      this.nodeId = nodeId;
      this.state = state;
      this.taskInstances = immutableTasks(taskInstances);
    }

    public String getNodeId() {
      return nodeId;
    }

    public TaskState getState() {
      return state;
    }

    public List<TaskView> getTaskInstances() {
      return taskInstances;
    }

    @Override
    public boolean equals(Object object) {
      if (this == object) {
        return true;
      }
      if (!(object instanceof NodeTimelineView)) {
        return false;
      }
      NodeTimelineView that = (NodeTimelineView) object;
      return Objects.equals(nodeId, that.nodeId)
          && state == that.state
          && Objects.equals(taskInstances, that.taskInstances);
    }

    @Override
    public int hashCode() {
      return Objects.hash(nodeId, state, taskInstances);
    }

    private List<TaskView> immutableTasks(List<TaskView> source) {
      ArrayList<TaskView> copy = new ArrayList<TaskView>();
      if (source != null) {
        copy.addAll(source);
      }
      return Collections.unmodifiableList(copy);
    }
  }
}
