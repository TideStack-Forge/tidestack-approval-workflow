package com.ouroboros.workflow.approval.model;

import java.util.ArrayList;
import java.util.List;

public class FlowDurationStatistic {
  String flowKey;
  Long instanceCount;
  Long avgDurationMillis;
  List<NodeDurationStatistic> nodes = new ArrayList<NodeDurationStatistic>();
  List<ApproverDurationStatistic> approvers = new ArrayList<ApproverDurationStatistic>();

  public String getFlowKey() {
    return flowKey;
  }

  public void setFlowKey(String flowKey) {
    this.flowKey = flowKey;
  }

  public Long getInstanceCount() {
    return instanceCount;
  }

  public void setInstanceCount(Long instanceCount) {
    this.instanceCount = instanceCount;
  }

  public Long getAvgDurationMillis() {
    return avgDurationMillis;
  }

  public void setAvgDurationMillis(Long avgDurationMillis) {
    this.avgDurationMillis = avgDurationMillis;
  }

  public List<NodeDurationStatistic> getNodes() {
    return nodes;
  }

  public void setNodes(List<NodeDurationStatistic> nodes) {
    this.nodes = nodes;
  }

  public List<ApproverDurationStatistic> getApprovers() {
    return approvers;
  }

  public void setApprovers(List<ApproverDurationStatistic> approvers) {
    this.approvers = approvers;
  }

  public static class NodeDurationStatistic {
    String nodeId;
    String nodeName;
    Long taskCount;
    Long avgDurationMillis;

    public String getNodeId() {
      return nodeId;
    }

    public void setNodeId(String nodeId) {
      this.nodeId = nodeId;
    }

    public String getNodeName() {
      return nodeName;
    }

    public void setNodeName(String nodeName) {
      this.nodeName = nodeName;
    }

    public Long getTaskCount() {
      return taskCount;
    }

    public void setTaskCount(Long taskCount) {
      this.taskCount = taskCount;
    }

    public Long getAvgDurationMillis() {
      return avgDurationMillis;
    }

    public void setAvgDurationMillis(Long avgDurationMillis) {
      this.avgDurationMillis = avgDurationMillis;
    }
  }

  public static class ApproverDurationStatistic {
    String userId;
    String userName;
    Long handledCount;
    Long avgHandleMillis;

    public String getUserId() {
      return userId;
    }

    public void setUserId(String userId) {
      this.userId = userId;
    }

    public String getUserName() {
      return userName;
    }

    public void setUserName(String userName) {
      this.userName = userName;
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
  }
}
