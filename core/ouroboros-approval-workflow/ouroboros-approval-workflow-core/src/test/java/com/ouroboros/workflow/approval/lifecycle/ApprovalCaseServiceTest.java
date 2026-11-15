package com.ouroboros.workflow.approval.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ouroboros.data.model.TypedDataModel;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.workflow.approval.model.events.FlowInstanceEvent;
import com.ouroboros.workflow.approval.model.events.FlowInstanceEvent.EventType;

import io.vavr.control.Try;

/** 服务级生命周期测试，使用 production data-model 查询和条件更新路径。 */
class ApprovalCaseServiceTest {

  private Map<String, ApprovalCase> store;
  private TestApprovalCaseService service;

  @BeforeEach
  void setUp() {
    store = new LinkedHashMap<>();
    service = new TestApprovalCaseService(dataModel());
  }

  @Test
  void firstSubmissionStartsAtRoundOneAndWithdrawalAllowsNextRound() {
    ApprovalCaseService.Submission first = service.reserveSubmission("order", "order-1", "flow-a");
    assertThat(first.submissionRound()).isEqualTo(1);
    assertThat(caseOf(first).getBusinessStatus()).isEqualTo(ApprovalBusinessStatus.PENDING.name());

    assertThat(service.applyInstanceEvent(event(first, EventType.STARTED, null, "start-1"))).isTrue();
    assertThat(service.applyInstanceEvent(event(first, EventType.CANCELLED, "withdrawn", "withdraw-1"))).isTrue();
    assertThat(caseOf(first).getBusinessStatus()).isEqualTo(ApprovalBusinessStatus.WITHDRAWN.name());

    ApprovalCaseService.Submission second = service.reserveSubmission("order", "order-1", "flow-a");
    assertThat(second.approvalCaseId()).isEqualTo(first.approvalCaseId());
    assertThat(second.submissionRound()).isEqualTo(2);
    assertThat(caseOf(second).getBusinessStatus()).isEqualTo(ApprovalBusinessStatus.PENDING.name());
  }

  @Test
  void oldRoundEventsCannotOverwriteNewSubmission() {
    ApprovalCaseService.Submission first = service.reserveSubmission("order", "order-1", "flow-a");
    service.applyInstanceEvent(event(first, EventType.STARTED, null, "start-1"));
    service.applyInstanceEvent(event(first, EventType.CANCELLED, "withdrawn", "withdraw-1"));
    ApprovalCaseService.Submission second = service.reserveSubmission("order", "order-1", "flow-a");

    assertThat(service.applyInstanceEvent(event(first, EventType.APPROVED, "approved", "late-old-round"))).isFalse();
    assertThat(caseOf(second).getSubmissionRound()).isEqualTo(2);
    assertThat(caseOf(second).getBusinessStatus()).isEqualTo(ApprovalBusinessStatus.PENDING.name());
  }

  @Test
  void suspendedCaseOnlyResumesFromExplicitResumedEvent() {
    ApprovalCaseService.Submission submission = service.reserveSubmission("order", "order-1", "flow-a");
    service.applyInstanceEvent(event(submission, EventType.STARTED, null, "start-1"));
    assertThat(service.applyInstanceEvent(event(submission, EventType.SUSPENDED, null, "suspend-1"))).isTrue();

    assertThat(service.applyInstanceEvent(event(submission, EventType.STARTED, null, "start-again"))).isFalse();
    assertThat(caseOf(submission).getBusinessStatus()).isEqualTo(ApprovalBusinessStatus.SUSPENDED.name());
    assertThat(service.applyInstanceEvent(event(submission, EventType.RESUMED, null, "resume-1"))).isTrue();
    assertThat(caseOf(submission).getBusinessStatus()).isEqualTo(ApprovalBusinessStatus.PROCESSING.name());
  }

  @Test
  void bindingAndStartFailureCannotRaceToLoseExecutionBinding() {
    ApprovalCaseService.Submission submission = service.reserveSubmission("order", "order-1", "flow-a");
    service.bindExecution(submission.approvalCaseId(), submission.submissionRound(), "execution-1");

    assertThatThrownBy(() -> service.markStartFailed(
        submission.approvalCaseId(), submission.submissionRound(), "late failure"))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("WORKFLOW_CASE_STATE_CONFLICT");
    assertThat(caseOf(submission).getActiveExecutionId()).isEqualTo("execution-1");
    assertThat(caseOf(submission).getBusinessStatus()).isEqualTo(ApprovalBusinessStatus.PENDING.name());
  }

  @Test
  void startedFactAtomicallyBindsExecutionBeforeCallerCanConfirmIt() {
    ApprovalCaseService.Submission submission = service.reserveSubmission("order", "order-1", "flow-a");

    assertThat(service.applyInstanceEvent(event(submission, EventType.STARTED, null, "start-1"))).isTrue();
    service.bindExecution(submission.approvalCaseId(), submission.submissionRound(), "execution-1");

    ApprovalCase current = caseOf(submission);
    assertThat(current.getBusinessStatus()).isEqualTo(ApprovalBusinessStatus.PROCESSING.name());
    assertThat(current.getActiveExecutionId()).isEqualTo("execution-1");
  }

  @Test
  void synchronouslyFinishedSourceCanConfirmItsExecutionWithoutReopeningTheCase() {
    var submission = service.reserveSubmission("order", "order-1", "flow-a");
    var completed = event(submission, EventType.APPROVED, "approved", "finished-before-return");
    completed.setProjectionVersion(1);
    completed.setStateSequence(1L);
    assertThat(service.applyInstanceEvent(completed)).isTrue();
    service.bindExecution(submission.approvalCaseId(), 1, "execution-1");
    assertThat(caseOf(submission).getBusinessStatus()).isEqualTo("APPROVED");
    assertThat(caseOf(submission).getActiveExecutionId()).isNull();
    assertThatThrownBy(() -> service.bindExecution(submission.approvalCaseId(), 1, "other-execution"))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  void businessFactOwnerDefersLegacyCaseWritesToTheSameRequiredCallbackTransaction() {
    var submission = service.reserveSubmission("order", "order-1", "flow-a");
    var event = event(submission, EventType.STARTED, null, "legacy");
    var projection = mock(com.ouroboros.workflow.approval.event.ApprovalBusinessFactProjection.class);
    when(projection.ownsInstance(event)).thenReturn(true);
    var owners = new org.springframework.beans.factory.support.DefaultListableBeanFactory();
    owners.registerSingleton("projection", projection);
    org.springframework.test.util.ReflectionTestUtils.setField(service, "businessProjections",
        owners.getBeanProvider(com.ouroboros.workflow.approval.event.ApprovalBusinessFactProjection.class));
    service.onInstanceEvent(event);
    assertThat(caseOf(submission).getBusinessStatus()).isEqualTo("PENDING");
    when(projection.ownsInstance(event)).thenReturn(false);
    service.onInstanceEvent(event);
    assertThat(caseOf(submission).getBusinessStatus()).isEqualTo("PROCESSING");
  }

  @Test
  void knownStartFailureReturnsUnboundPendingSubmissionToDraft() {
    ApprovalCaseService.Submission submission = service.reserveSubmission("order", "order-1", "flow-a");

    service.markStartFailed(submission.approvalCaseId(), submission.submissionRound(), "definition unavailable");

    ApprovalCase current = caseOf(submission);
    assertThat(current.getBusinessStatus()).isEqualTo(ApprovalBusinessStatus.DRAFT.name());
    assertThat(current.getActiveExecutionId()).isNull();
    assertThat(current.getTechnicalState()).isEqualTo(ApprovalTechnicalState.HEALTHY.name());
  }

  @Test
  void eventFromAnotherExecutionCannotChangeCurrentSubmissionRound() {
    ApprovalCaseService.Submission submission = service.reserveSubmission("order", "order-1", "flow-a");
    service.bindExecution(submission.approvalCaseId(), submission.submissionRound(), "execution-1");

    FlowInstanceEvent foreignEvent = event(submission, EventType.CANCELLED, "withdrawn", "foreign-cancel");
    foreignEvent.setInstanceId("execution-2");

    assertThat(service.applyInstanceEvent(foreignEvent)).isFalse();
    assertThat(caseOf(submission).getBusinessStatus()).isEqualTo(ApprovalBusinessStatus.PENDING.name());
    assertThat(caseOf(submission).getActiveExecutionId()).isEqualTo("execution-1");
  }

  @Test
  void exhaustedTechnicalStateAtomicallyCancelsBusinessCase() {
    ApprovalCaseService.Submission submission = service.reserveSubmission("order", "order-1", "flow-a");
    service.markUnknown(submission.approvalCaseId(), submission.submissionRound(), "remote result unknown");
    service.markExhausted(submission.approvalCaseId(), submission.submissionRound(), "cannot recover");

    ApprovalCase current = caseOf(submission);
    assertThat(current.getBusinessStatus()).isEqualTo(ApprovalBusinessStatus.CANCELED.name());
    assertThat(current.getTechnicalState()).isEqualTo(ApprovalTechnicalState.EXHAUSTED.name());
    assertThat(current.getTechnicalFailureReason()).isEqualTo("cannot recover");
  }

  private ApprovalCase caseOf(ApprovalCaseService.Submission submission) {
    return store.get(submission.approvalCaseId());
  }

  private FlowInstanceEvent event(
      ApprovalCaseService.Submission submission, EventType type, String outcome, String factId) {
    FlowInstanceEvent event = new FlowInstanceEvent();
    event.setApprovalCaseId(submission.approvalCaseId());
    event.setSubmissionRound(submission.submissionRound());
    event.setEventType(type);
    event.setOutcome(outcome);
    event.setEventFactId(factId);
    event.setInstanceId("execution-1");
    return event;
  }

  private TypedDataModel<String, ApprovalCase> dataModel() {
    TypedDataModel<String, ApprovalCase> dataModel = mock(TypedDataModel.class);
    when(dataModel.insert(any(ApprovalCase.class))).thenAnswer(invocation -> {
      ApprovalCase approvalCase = invocation.getArgument(0);
      store.put(approvalCase.getApprovalCaseId(), approvalCase);
      return Try.success(approvalCase);
    });
    when(dataModel.get(any(), anyMap())).thenAnswer(invocation ->
        Try.success(store.get(invocation.getArgument(0))));
    when(dataModel.query(anyMap())).thenAnswer(invocation -> {
      Map<String, Object> statement = invocation.getArgument(0);
      return Try.success(new ArrayList<>(store.values().stream()
          .filter(approvalCase -> matches(approvalCase, statement))
          .toList()));
    });
    when(dataModel.update(anyMap(), anyMap())).thenAnswer(invocation -> {
      Map<String, Object> where = invocation.getArgument(0);
      Map<String, Object> updates = invocation.getArgument(1);
      long affected = 0;
      for (ApprovalCase approvalCase : store.values()) {
        if (matches(approvalCase, where)) {
          apply(approvalCase, updates);
          affected++;
        }
      }
      return Try.success(affected);
    });
    return dataModel;
  }

  private boolean matches(ApprovalCase approvalCase, Map<String, Object> statement) {
    for (Map.Entry<String, Object> entry : statement.entrySet()) {
      Object actual = switch (entry.getKey()) {
        case "approvalCaseId" -> approvalCase.getApprovalCaseId();
        case "caseRouteKey" -> approvalCase.getCaseRouteKey();
        case "submissionRound" -> approvalCase.getSubmissionRound();
        case "businessStatus" -> approvalCase.getBusinessStatus();
        case "technicalState" -> approvalCase.getTechnicalState();
        case "activeExecutionId" -> approvalCase.getActiveExecutionId();
        default -> null;
      };
      Object expected = entry.getValue() instanceof Map<?, ?> condition
          ? condition.get("=") : entry.getValue();
      if (!java.util.Objects.equals(actual, expected)) {
        return false;
      }
    }
    return true;
  }

  private void apply(ApprovalCase approvalCase, Map<String, Object> updates) {
    updates.forEach((key, value) -> {
      switch (key) {
        case "submissionRound" -> approvalCase.setSubmissionRound((Integer) value);
        case "businessStatus" -> approvalCase.setBusinessStatus((String) value);
        case "technicalState" -> approvalCase.setTechnicalState((String) value);
        case "technicalFailureReason" -> approvalCase.setTechnicalFailureReason((String) value);
        case "activeExecutionId" -> approvalCase.setActiveExecutionId((String) value);
        case "lastEventFactId" -> approvalCase.setLastEventFactId((String) value);
        case "lastStateSequence" -> approvalCase.setLastStateSequence((Long) value);
        case "lastStateInstanceId" -> approvalCase.setLastStateInstanceId((String) value);
        case "updatedAt" -> approvalCase.setUpdatedAt((LocalDateTime) value);
        default -> {
        }
      }
    });
  }

  private static final class TestApprovalCaseService extends ApprovalCaseService {
    private final TypedDataModel<String, ApprovalCase> dataModel;

    private TestApprovalCaseService(TypedDataModel<String, ApprovalCase> dataModel) {
      this.dataModel = dataModel;
    }

    @Override
    protected TypedDataModel<String, ApprovalCase> getDataModel() {
      return dataModel;
    }

    @Override
    public java.util.Optional<ApprovalCase> lockByBusinessRecord(String recordType, String recordId, String flowKey) {
      return dataModel.query(Map.of()).get().stream().filter(value -> recordType.equals(value.getBusinessRecordType())
          && recordId.equals(value.getBusinessRecordId()) && flowKey.equals(value.getFlowKey())).findFirst();
    }
  }
}
