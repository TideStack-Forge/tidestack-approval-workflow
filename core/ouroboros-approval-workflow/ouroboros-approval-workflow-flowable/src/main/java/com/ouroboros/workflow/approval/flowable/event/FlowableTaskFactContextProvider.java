package com.ouroboros.workflow.approval.flowable.event;

import java.util.Map;

import org.flowable.engine.RuntimeService;
import org.springframework.stereotype.Component;

import com.ouroboros.workflow.approval.event.BuiltInFlowTaskFactContextProvider;
import com.ouroboros.workflow.approval.flowable.transpiler.Constants;
import com.ouroboros.workflow.approval.model.events.FlowInstanceEvent;
import com.ouroboros.workflow.approval.model.events.FlowTaskEvent;

/** Reads engine variables before a task command can remove its runtime identity. */
@Component
public class FlowableTaskFactContextProvider implements BuiltInFlowTaskFactContextProvider {
  private final RuntimeService runtimeService;

  public FlowableTaskFactContextProvider(RuntimeService runtimeService) { this.runtimeService = runtimeService; }

  @Override
  public void capture(FlowTaskEvent event) {
    if (event.getProjectionVersion() != null) { return; }
    Map<String, Object> variables = runtimeService.getVariables(event.getInstanceId());
    Object recordType = variables.get(Constants.Variable.BUSINESS_RECORD_TYPE);
    Object recordId = variables.get(Constants.Variable.BUSINESS_RECORD_ID);
    if (recordType instanceof String type && recordId != null) {
      event.setBusinessContext(new FlowInstanceEvent.BusinessContext(type, String.valueOf(recordId)));
    }
    if (variables.get(Constants.Variable.APPROVAL_CASE_ID) instanceof String id) { event.setApprovalCaseId(id); }
    if (variables.get(Constants.Variable.SUBMISSION_ROUND) instanceof Number round) { event.setSubmissionRound(round.intValue()); }
    if (variables.get(Constants.Variable.CALLBACK_FLOW) instanceof String flow) { event.setCallbackFlow(flow); }
    event.setProjectionVersion(1);
  }
}
