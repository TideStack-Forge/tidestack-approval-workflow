package com.ouroboros.workflow.approval.event;

import com.ouroboros.data.annotation.Field;
import com.ouroboros.data.annotation.Model;
import com.ouroboros.data.annotation.PrimaryKey;

/** Source lifecycle cursor. It commits with the engine mutation and the original Outbox. */
@Model(rawName = "t_approval_workflow_instance_fact_state", label = "审批实例事实版本")
public class FlowInstanceFactState {
  @Field @PrimaryKey
  private String instanceId;
  @Field
  private Long stateSequence;
  @Field
  private String lastEventFactId;
  @Field
  private String eventType;
  @Field(nullable = true)
  private String outcome;
  @Field
  private String flowKey;
  @Field(nullable = true)
  private String businessRecordType;
  @Field(nullable = true)
  private String businessRecordId;
  @Field(nullable = true)
  private String approvalCaseId;
  @Field(nullable = true)
  private Integer submissionRound;
  @Field(nullable = true)
  private String callbackFlow;

  public String getInstanceId() { return instanceId; }
  public void setInstanceId(String instanceId) { this.instanceId = instanceId; }
  public Long getStateSequence() { return stateSequence; }
  public void setStateSequence(Long stateSequence) { this.stateSequence = stateSequence; }
  public String getLastEventFactId() { return lastEventFactId; }
  public void setLastEventFactId(String lastEventFactId) { this.lastEventFactId = lastEventFactId; }
  public String getEventType() { return eventType; }
  public void setEventType(String eventType) { this.eventType = eventType; }
  public String getOutcome() { return outcome; }
  public void setOutcome(String outcome) { this.outcome = outcome; }
  public String getFlowKey() { return flowKey; }
  public void setFlowKey(String flowKey) { this.flowKey = flowKey; }
  public String getBusinessRecordType() { return businessRecordType; }
  public void setBusinessRecordType(String businessRecordType) { this.businessRecordType = businessRecordType; }
  public String getBusinessRecordId() { return businessRecordId; }
  public void setBusinessRecordId(String businessRecordId) { this.businessRecordId = businessRecordId; }
  public String getApprovalCaseId() { return approvalCaseId; }
  public void setApprovalCaseId(String approvalCaseId) { this.approvalCaseId = approvalCaseId; }
  public Integer getSubmissionRound() { return submissionRound; }
  public void setSubmissionRound(Integer submissionRound) { this.submissionRound = submissionRound; }
  public String getCallbackFlow() { return callbackFlow; }
  public void setCallbackFlow(String callbackFlow) { this.callbackFlow = callbackFlow; }
}
