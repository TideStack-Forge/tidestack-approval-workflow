package com.ouroboros.workflow.approval.flowable.event;

import java.util.Map;

import org.flowable.engine.RuntimeService;
import org.junit.jupiter.api.Test;

import com.ouroboros.workflow.approval.flowable.transpiler.Constants;
import com.ouroboros.workflow.approval.model.events.FlowInstanceEvent;
import com.ouroboros.workflow.approval.model.events.FlowTaskEvent;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FlowableTaskFactContextProviderTest {
  @Test
  void sourceIdentityIsCapturedOnceWhileTaskExistsAndNeverReadAgainAfterItsDeletion() {
    var runtime = mock(RuntimeService.class);
    when(runtime.getVariables("instance-1")).thenReturn(Map.of(
        Constants.Variable.BUSINESS_RECORD_TYPE, "sales.Order", Constants.Variable.BUSINESS_RECORD_ID, "42",
        Constants.Variable.APPROVAL_CASE_ID, "case-1", Constants.Variable.SUBMISSION_ROUND, 3,
        Constants.Variable.CALLBACK_FLOW, "sales.Order.processCallback"));
    var provider = new FlowableTaskFactContextProvider(runtime);
    var fact = new FlowTaskEvent();
    fact.setInstanceId("instance-1");
    provider.capture(fact);
    assertEquals(new FlowInstanceEvent.BusinessContext("sales.Order", "42"), fact.getBusinessContext());
    assertEquals("case-1", fact.getApprovalCaseId());
    assertEquals(3, fact.getSubmissionRound());
    assertEquals("sales.Order.processCallback", fact.getCallbackFlow());
    when(runtime.getVariables("instance-1")).thenThrow(new IllegalStateException("deleted"));
    assertDoesNotThrow(() -> provider.capture(fact));
    verify(runtime).getVariables("instance-1");
  }

  @Test
  void unboundWorkflowStaysUnbound() {
    var runtime = mock(RuntimeService.class);
    when(runtime.getVariables("unbound")).thenReturn(Map.of());
    var fact = new FlowTaskEvent();
    fact.setInstanceId("unbound");
    new FlowableTaskFactContextProvider(runtime).capture(fact);
    assertNull(fact.getBusinessContext());
    assertEquals(1, fact.getProjectionVersion());
  }
}
