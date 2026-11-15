package com.ouroboros.workflow.approval.model.events;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ouroboros.util.Dates;
import com.ouroboros.workflow.approval.definition.FlowDefinition.Notification;
import com.ouroboros.workflow.approval.definition.FlowNode;
import com.ouroboros.workflow.approval.model.TaskState;

public class FlowTaskEvent {
  private java.time.Instant occurredAt;

  public java.time.Instant getOccurredAt() { return occurredAt; }
  public void setOccurredAt(java.time.Instant occurredAt) { this.occurredAt = occurredAt; }
  private String eventFactId;
  private Integer projectionVersion;
  private com.ouroboros.workflow.approval.event.FlowBusinessNotificationOwner businessNotificationOwner;

  @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
  public com.ouroboros.workflow.approval.event.FlowBusinessNotificationOwner getBusinessNotificationOwner() { return businessNotificationOwner; }
  public void setBusinessNotificationOwner(com.ouroboros.workflow.approval.event.FlowBusinessNotificationOwner owner) { businessNotificationOwner = owner; }
  private FactKind factKind = FactKind.STATE_CHANGED;
  private FlowInstanceEvent.BusinessContext businessContext;
  private String approvalCaseId;
  private Integer submissionRound;
  private String callbackFlow;

  public enum FactKind { STATE_CHANGED, ACTION_COMPLETED }

  public Integer getProjectionVersion() { return projectionVersion; }
  public void setProjectionVersion(Integer projectionVersion) { this.projectionVersion = projectionVersion; }
  public FactKind getFactKind() { return factKind; }
  public void setFactKind(FactKind factKind) { this.factKind = factKind; }
  public FlowInstanceEvent.BusinessContext getBusinessContext() { return businessContext; }
  public void setBusinessContext(FlowInstanceEvent.BusinessContext businessContext) { this.businessContext = businessContext; }
  public String getApprovalCaseId() { return approvalCaseId; }
  public void setApprovalCaseId(String approvalCaseId) { this.approvalCaseId = approvalCaseId; }
  public Integer getSubmissionRound() { return submissionRound; }
  public void setSubmissionRound(Integer submissionRound) { this.submissionRound = submissionRound; }
  public String getCallbackFlow() { return callbackFlow; }
  public void setCallbackFlow(String callbackFlow) { this.callbackFlow = callbackFlow; }
  String flowKey;
  String nodeId;
  String taskName;
  String assignee;
  List<String> candidateUsers;
  String instanceId;
  String taskId;
  String actionName;
  TaskState state;
  String comment;
  LocalDateTime eventTime;
  Notification notification;
  FlowNode flowNode;
  Supplier<FlowNode> flowNodeSupplier;

  public String getEventFactId() {
    return eventFactId;
  }

  public void setEventFactId(String eventFactId) {
    this.eventFactId = eventFactId;
  }

  public String getFlowKey() {
    return flowKey;
  }

  public void setFlowKey(String flowKey) {
    this.flowKey = flowKey;
  }

  public String getNodeId() {
    return Optional.ofNullable(nodeId)
        .orElseGet(() -> Optional.ofNullable(this.getFlowNode())
            .map(FlowNode::getId)
            .orElse(null));
  }

  public void setNodeId(String nodeId) {
    this.nodeId = nodeId;
  }

  public String getTaskName() {
    return Optional.ofNullable(taskName)
        .orElseGet(() -> Optional.ofNullable(this.getFlowNode())
            .map(FlowNode::getName)
            .orElse(null));
  }

  public FlowNode getFlowNode() {
    if (flowNode == null) {
      flowNode = Optional.ofNullable(flowNodeSupplier)
          .map(Supplier::get)
          .orElse(null);
    }
    return flowNode;
  }

  public void setTaskName(String taskName) {
    this.taskName = taskName;
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

  public String getActionName() {
    return actionName;
  }

  public void setActionName(String actionName) {
    this.actionName = actionName;
  }

  public TaskState getState() {
    return state;
  }

  public void setState(TaskState state) {
    this.state = state;
  }

  public String getComment() {
    return comment;
  }

  public void setComment(String comment) {
    this.comment = comment;
  }

  @JsonProperty("eventTime")
  public LocalDateTime getEventTime() {
    return eventTime;
  }

  @JsonProperty("eventTime")
  public void setEventTime(LocalDateTime eventTime) {
    this.eventTime = eventTime;
  }

  @JsonIgnore
  public void setEventTime(Date eventTime) {
    this.eventTime = Dates.toLocalDateTime(eventTime);
  }

  public Notification getNotification() {
    return notification;
  }

  public void setNotification(Notification notification) {
    this.notification = notification;
  }

  public void setFlowNodeSupplier(Supplier<FlowNode> flowNodeSupplier) {
    this.flowNodeSupplier = flowNodeSupplier;
  }

  public void setFlowNode(FlowNode flowNode) {
    this.flowNode = flowNode;
  }
}
