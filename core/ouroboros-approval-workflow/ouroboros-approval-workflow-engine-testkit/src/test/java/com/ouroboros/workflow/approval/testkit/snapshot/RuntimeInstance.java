package com.ouroboros.workflow.approval.testkit.snapshot;

import java.util.Objects;

public class RuntimeInstance {
  private final String instanceId;
  private final String flowKey;

  public RuntimeInstance(String instanceId, String flowKey) {
    this.instanceId = instanceId;
    this.flowKey = flowKey;
  }

  public String getInstanceId() {
    return instanceId;
  }

  public String getFlowKey() {
    return flowKey;
  }

  @Override
  public boolean equals(Object object) {
    if (this == object) {
      return true;
    }
    if (!(object instanceof RuntimeInstance)) {
      return false;
    }
    RuntimeInstance that = (RuntimeInstance) object;
    return Objects.equals(instanceId, that.instanceId) && Objects.equals(flowKey, that.flowKey);
  }

  @Override
  public int hashCode() {
    return Objects.hash(instanceId, flowKey);
  }
}
