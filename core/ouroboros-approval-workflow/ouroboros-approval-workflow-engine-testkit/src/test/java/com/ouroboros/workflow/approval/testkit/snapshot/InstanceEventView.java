package com.ouroboros.workflow.approval.testkit.snapshot;

import java.util.Objects;

public class InstanceEventView {
  private final String instanceId;
  private final String flowKey;
  private final String state;
  private final Boolean approved;
  private final String comment;
  private final String lastTaskId;
  private final long sequence;

  public InstanceEventView(String instanceId, String flowKey, String state, Boolean approved, String comment,
      String lastTaskId, long sequence) {
    this.instanceId = instanceId;
    this.flowKey = flowKey;
    this.state = state;
    this.approved = approved;
    this.comment = comment;
    this.lastTaskId = lastTaskId;
    this.sequence = sequence;
  }

  public String getInstanceId() {
    return instanceId;
  }

  public String getFlowKey() {
    return flowKey;
  }

  public String getState() {
    return state;
  }

  public Boolean getApproved() {
    return approved;
  }

  public String getComment() {
    return comment;
  }

  public String getLastTaskId() {
    return lastTaskId;
  }

  public long getSequence() {
    return sequence;
  }

  @Override
  public boolean equals(Object object) {
    if (this == object) {
      return true;
    }
    if (!(object instanceof InstanceEventView)) {
      return false;
    }
    InstanceEventView that = (InstanceEventView) object;
    return sequence == that.sequence
        && Objects.equals(instanceId, that.instanceId)
        && Objects.equals(flowKey, that.flowKey)
        && Objects.equals(state, that.state)
        && Objects.equals(approved, that.approved)
        && Objects.equals(comment, that.comment)
        && Objects.equals(lastTaskId, that.lastTaskId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(instanceId, flowKey, state, approved, comment, lastTaskId, sequence);
  }
}
