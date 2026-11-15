package com.ouroboros.workflow.approval;

import com.ouroboros.workflow.approval.model.events.FlowInstanceEvent;

/** Authoritative engine state, read under the canonical business-case lock; not a new source fact. */
public record ApprovalInstanceState(String flowKey, String instanceId, FlowInstanceEvent.BusinessContext businessContext,
    String approvalCaseId, Integer submissionRound, String callbackFlow, FlowInstanceEvent.EventType eventType, String outcome) {
}
