package com.ouroboros.workflow.approval.model;

public class FlowInstanceStatistic {
  String flowKey;
  String displayName;
  String category;
  String description;
  Long total;
  Long running;
  Long suspended;
  Long finished;
  Long avgDurationMillis;
  Long nodeCount;
  Long avgNodeDurationMillis;
  Long handledCount;
  Long avgHandleMillis;
  String status;

  public String getFlowKey() {
    return flowKey;
  }

  public void setFlowKey(String flowKey) {
    this.flowKey = flowKey;
  }

  public String getDisplayName() {
    return displayName;
  }

  public void setDisplayName(String displayName) {
    this.displayName = displayName;
  }

  public String getCategory() {
    return category;
  }

  public void setCategory(String category) {
    this.category = category;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public Long getTotal() {
    return total;
  }

  public void setTotal(Long total) {
    this.total = total;
  }

  public Long getRunning() {
    return running;
  }

  public void setRunning(Long running) {
    this.running = running;
  }

  public Long getFinished() {
    return finished;
  }

  public void setFinished(Long finished) {
    this.finished = finished;
  }

  public Long getAvgDurationMillis() {
    return avgDurationMillis;
  }

  public void setAvgDurationMillis(Long avgDurationMillis) {
    this.avgDurationMillis = avgDurationMillis;
  }

  public Long getNodeCount() {
    return nodeCount;
  }

  public void setNodeCount(Long nodeCount) {
    this.nodeCount = nodeCount;
  }

  public Long getAvgNodeDurationMillis() {
    return avgNodeDurationMillis;
  }

  public void setAvgNodeDurationMillis(Long avgNodeDurationMillis) {
    this.avgNodeDurationMillis = avgNodeDurationMillis;
  }

  public Long getHandledCount() {
    return handledCount;
  }

  public void setHandledCount(Long handledCount) {
    this.handledCount = handledCount;
  }

  public Long getAvgHandleMillis() {
    return avgHandleMillis;
  }

  public void setAvgHandleMillis(Long avgHandleMillis) {
    this.avgHandleMillis = avgHandleMillis;
  }

  public Long getSuspended() {
    return suspended;
  }

  public void setSuspended(Long suspended) {
    this.suspended = suspended;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }
}
