package com.ouroboros.workflow.approval.flowable.service;

import java.util.Map;
import java.util.Optional;

import org.flowable.engine.RepositoryService;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.Test;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.workflow.approval.flowable.event.FlowableSourceTransaction;
import com.ouroboros.workflow.approval.lifecycle.ApprovalCase;
import com.ouroboros.workflow.approval.lifecycle.ApprovalCaseService;
import com.ouroboros.workflow.approval.flowable.transpiler.Constants;
import com.ouroboros.workflow.approval.model.events.FlowInstanceEvent;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FlowableBusinessBindingValidatorTest {
  private final RuntimeService runtime = mock(RuntimeService.class);
  private final TaskService tasks = mock(TaskService.class);
  private final RepositoryService repository = mock(RepositoryService.class);
  private final ApprovalCaseService cases = mock(ApprovalCaseService.class);
  private final FlowableSourceTransaction transaction = mock(FlowableSourceTransaction.class);
  private final HistoryService history = mock(HistoryService.class);
  private final FlowableBusinessBindingValidator validator = new FlowableBusinessBindingValidator(runtime, tasks, repository,
      history, cases, transaction);

  @Test
  void authoritativeInspectionReadsEngineStateAndRejectsMissingOrForeignOwnership() {
    arrange();
    var instance = runtime.createProcessInstanceQuery().processInstanceId("instance").singleResult();
    when(instance.getProcessVariables()).thenReturn(Map.of("businessRecordType", "sales.Order", "businessRecordId", 42,
        "approvalCaseId", "case", "submissionRound", 2, "callbackFlow", "sales.Order.processCallback"));
    var baseline = validator.inspectInstance("approval", "instance");
    assertEquals(FlowInstanceEvent.EventType.STARTED, baseline.eventType());
    assertEquals("42", baseline.businessContext().recordId());
    assertEquals(2, baseline.submissionRound());
    when(instance.isSuspended()).thenReturn(true);
    assertEquals(FlowInstanceEvent.EventType.SUSPENDED, validator.inspectInstance("approval", "instance").eventType());
    when(instance.isSuspended()).thenReturn(false);
    when(instance.getProcessVariables()).thenReturn(Map.of("businessRecordType", "sales.Order", "businessRecordId", "42",
        Constants.PROCESS_RESUME_TIME_VARIABLE_NAME, java.time.LocalDateTime.now()));
    assertEquals(FlowInstanceEvent.EventType.RESUMED, validator.inspectInstance("approval", "instance").eventType());
    assertThrows(BusinessException.class, () -> validator.inspectInstance("foreign-flow", "instance"));
    when(instance.getProcessVariables()).thenReturn(Map.of());
    assertThrows(BusinessException.class, () -> validator.inspectInstance("approval", "instance"));
  }

  @Test
  void authoritativeHistoryUsesOnlyPersistedOutcomesAndKeepsAmbiguousDeletionPending() {
    arrange();
    when(runtime.createProcessInstanceQuery().processInstanceId("instance").singleResult()).thenReturn(null);
    var query = mock(org.flowable.engine.history.HistoricProcessInstanceQuery.class, RETURNS_SELF);
    when(history.createHistoricProcessInstanceQuery()).thenReturn(query);
    assertThrows(BusinessException.class, () -> validator.inspectInstance("approval", "instance"));
    var ended = mock(org.flowable.engine.history.HistoricProcessInstance.class);
    when(query.singleResult()).thenReturn(ended);
    assertThrows(BusinessException.class, () -> validator.inspectInstance("approval", "instance"));
    when(ended.getEndTime()).thenReturn(new java.util.Date());
    when(ended.getProcessDefinitionKey()).thenReturn("approval");
    var values = new java.util.HashMap<String, Object>();
    values.put("businessRecordType", "sales.Order"); values.put("businessRecordId", "42");
    when(ended.getProcessVariables()).thenReturn(values);
    for (String outcome : java.util.List.of("approved", "rejected", "withdrawn", "cancelled", "terminated")) {
      values.put(Constants.Variable.PROCESS_OUTCOME, outcome);
      var snapshot = validator.inspectInstance("approval", "instance");
      assertEquals(outcome, snapshot.outcome());
      assertEquals("approved".equals(outcome) ? FlowInstanceEvent.EventType.APPROVED
          : "rejected".equals(outcome) ? FlowInstanceEvent.EventType.REJECTED : FlowInstanceEvent.EventType.CANCELLED, snapshot.eventType());
    }
    values.remove(Constants.Variable.PROCESS_OUTCOME);
    values.put(Constants.Variable.PROCESS_APPROVED, false);
    assertThrows(BusinessException.class, () -> validator.inspectInstance("approval", "instance"));
    when(ended.getEndActivityId()).thenReturn("normal-end");
    assertEquals(FlowInstanceEvent.EventType.REJECTED, validator.inspectInstance("approval", "instance").eventType());
    values.put(Constants.Variable.PROCESS_APPROVED, true);
    assertEquals(FlowInstanceEvent.EventType.APPROVED, validator.inspectInstance("approval", "instance").eventType());
    values.put(Constants.Variable.PROCESS_OUTCOME, "unknown");
    assertThrows(BusinessException.class, () -> validator.inspectInstance("approval", "instance"));
  }

  @Test
  void validatesActualEngineOwnershipAndCurrentCaseBeforeAllowingTheImplementation() {
    arrange();
    var current = new ApprovalCase();
    current.setApprovalCaseId("case");
    current.setSubmissionRound(2);
    current.setActiveExecutionId("instance");
    when(cases.lockByBusinessRecord("sales.Order", "42", "approval")).thenReturn(Optional.of(current));
    when(runtime.getVariables("instance")).thenReturn(Map.of("businessRecordType", "sales.Order", "businessRecordId", 42,
        "approvalCaseId", "case", "submissionRound", 2));
    assertDoesNotThrow(() -> validator.validate("approval", "sales.Order", "42", "instance", "task"));
    verify(transaction).requireParticipation();
    current.setSubmissionRound(3);
    assertThrows(BusinessException.class, () -> validator.validate("approval", "sales.Order", "42", "instance", "task"));
    current.setSubmissionRound(2);
    current.setActiveExecutionId("replacement");
    assertThrows(BusinessException.class, () -> validator.validate("approval", "sales.Order", "42", "instance", "task"));
  }

  @Test
  void rejectsForeignRecordTaskAndUndeployedFlowAndKeepsTrustedLegacyInstancesUsable() {
    arrange();
    when(runtime.getVariables("instance")).thenReturn(Map.of("businessRecordType", "sales.Order", "businessRecordId", "42"));
    when(cases.lockByBusinessRecord("sales.Order", "42", "approval")).thenReturn(Optional.empty());
    assertDoesNotThrow(() -> validator.validate("approval", "sales.Order", "42", "instance", "task"));
    assertThrows(BusinessException.class, () -> validator.validate("approval", "sales.Order", "different", "instance", "task"));
    when(tasks.createTaskQuery().taskId("task").singleResult().getProcessInstanceId()).thenReturn("foreign");
    assertThrows(BusinessException.class, () -> validator.validate("approval", "sales.Order", "42", "instance", "task"));
    assertDoesNotThrow(() -> validator.validate("approval", "sales.Order", "42", null, null));
    when(repository.createProcessDefinitionQuery().processDefinitionKey("approval").latestVersion().singleResult()).thenReturn(null);
    assertThrows(BusinessException.class, () -> validator.validate("approval", "sales.Order", "42", null, null));
  }

  private void arrange() {
    when(repository.createProcessDefinitionQuery()).thenReturn(mock(org.flowable.engine.repository.ProcessDefinitionQuery.class, RETURNS_SELF));
    when(runtime.createProcessInstanceQuery()).thenReturn(mock(org.flowable.engine.runtime.ProcessInstanceQuery.class, RETURNS_SELF));
    when(tasks.createTaskQuery()).thenReturn(mock(org.flowable.task.api.TaskQuery.class, RETURNS_SELF));
    when(repository.createProcessDefinitionQuery().processDefinitionKey("approval").latestVersion().singleResult())
        .thenReturn(mock(ProcessDefinition.class));
    var instance = mock(ProcessInstance.class);
    when(instance.getProcessDefinitionKey()).thenReturn("approval");
    when(runtime.createProcessInstanceQuery().processInstanceId("instance").singleResult()).thenReturn(instance);
    var task = mock(Task.class);
    when(task.getProcessInstanceId()).thenReturn("instance");
    when(tasks.createTaskQuery().taskId("task").singleResult()).thenReturn(task);
  }
}
