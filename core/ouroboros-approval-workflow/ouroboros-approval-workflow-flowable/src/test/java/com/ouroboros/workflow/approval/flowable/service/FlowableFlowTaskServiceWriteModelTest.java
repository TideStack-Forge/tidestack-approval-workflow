package com.ouroboros.workflow.approval.flowable.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.Process;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.TaskService;
import org.flowable.identitylink.api.IdentityLink;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.SimpleTransactionStatus;

import io.vavr.control.Try;

import com.ouroboros.util.Json;
import com.ouroboros.workflow.approval.FlowTaskAction;
import com.ouroboros.workflow.approval.FlowTaskService;
import com.ouroboros.workflow.approval.FlowFormService;
import com.ouroboros.workflow.approval.definition.FlowNode;
import com.ouroboros.workflow.approval.definition.nodes.ApprovalNode;
import com.ouroboros.workflow.approval.definition.nodes.HandleNode;
import com.ouroboros.workflow.approval.definition.nodes.SingleApprovalNode;
import com.ouroboros.workflow.approval.flowable.action.FlowableTransferAction;
import com.ouroboros.workflow.approval.flowable.transpiler.Constants;
import com.ouroboros.workflow.approval.flowable.util.BpmnUtil;

class FlowableFlowTaskServiceWriteModelTest {
  @Test
  void doActionClaimsCandidateTaskStoresPayloadCommentAndInvokesAction() {
    TaskService taskService = mock(TaskService.class);
    @SuppressWarnings("unchecked")
    FlowTaskAction<Task> action = mock(FlowTaskAction.class);
    FlowableFlowTaskService service = new FlowableFlowTaskService(taskService, Collections.singletonList(action));

    TaskQuery taskQuery = mock(TaskQuery.class);
    Task task = mock(Task.class);
    IdentityLink candidate = mock(IdentityLink.class);

    when(taskService.createTaskQuery()).thenReturn(taskQuery);
    when(taskQuery.taskId("task-1")).thenReturn(taskQuery);
    when(taskQuery.singleResult()).thenReturn(task);
    when(taskService.getIdentityLinksForTask("task-1")).thenReturn(Collections.singletonList(candidate));
    when(candidate.getUserId()).thenReturn("user-a");
    when(task.getId()).thenReturn("task-1");
    when(task.getProcessInstanceId()).thenReturn("instance-1");
    when(task.getAssignee()).thenReturn(null);
    configureApprovalNode(service, task, "approve-a", Collections.emptyList());
    when(action.supportTask(task)).thenReturn(true);
    when(action.actionName()).thenReturn("approve");
    when(action.doAction(task)).thenReturn(Try.success(null));

    var result = service.doAction(
        "task-1",
        "approve",
        "user-a",
        "looks good",
        null,
        Collections.singletonMap("approved", true));

    assertTrue(result.isSuccess(), () -> result.getCause().getMessage());
    verify(taskService).claim("task-1", "user-a");
    verify(taskService).setVariableLocal("task-1", Constants.TASK_PAYLOAD_KEY, "{\"approved\":true}");
    verify(taskService).addComment("task-1", "instance-1", "looks good");
    verify(action).doAction(task);
    var fact = org.mockito.ArgumentCaptor.forClass(com.ouroboros.workflow.approval.model.events.FlowTaskEvent.class);
    verify(service.taskEventDispatcher).dispatchEvent(fact.capture());
    assertEquals(com.ouroboros.workflow.approval.model.events.FlowTaskEvent.FactKind.ACTION_COMPLETED,
        fact.getValue().getFactKind());
    assertEquals("approve", fact.getValue().getActionName());
    assertEquals("instance-1", fact.getValue().getInstanceId());
    assertNull(fact.getValue().getState());
  }

  @Test
  void doActionFailsWhenTaskMissing() {
    TaskService taskService = mock(TaskService.class);
    FlowableFlowTaskService service = new FlowableFlowTaskService(taskService, Collections.emptyList());

    TaskQuery taskQuery = mock(TaskQuery.class);
    when(taskService.createTaskQuery()).thenReturn(taskQuery);
    when(taskQuery.taskId("task-missing")).thenReturn(taskQuery);
    when(taskQuery.singleResult()).thenReturn(null);

    var result = service.doAction("task-missing", "approve", "user-a", null, null, null);

    assertTrue(result.isFailure());
    assertEquals("未找到对应的任务: task-missing", result.getCause().getMessage());
  }

  @Test
  void doActionFailsWhenUserIsNotInCandidateList() {
    TaskService taskService = mock(TaskService.class);
    FlowableFlowTaskService service = new FlowableFlowTaskService(taskService, Collections.emptyList());

    TaskQuery taskQuery = mock(TaskQuery.class);
    Task task = mock(Task.class);
    IdentityLink candidate = mock(IdentityLink.class);

    when(taskService.createTaskQuery()).thenReturn(taskQuery);
    when(taskQuery.taskId("task-1")).thenReturn(taskQuery);
    when(taskQuery.singleResult()).thenReturn(task);
    when(taskService.getIdentityLinksForTask("task-1")).thenReturn(Collections.singletonList(candidate));
    when(candidate.getUserId()).thenReturn("other-user");

    var result = service.doAction("task-1", "approve", "user-a", null, null, null);

    assertTrue(result.isFailure());
    assertEquals("当前用户不在任务的候选人列表中: task-1", result.getCause().getMessage());
  }

  @Test
  void doActionFailsWhenTaskIsClaimedByAnotherUser() {
    TaskService taskService = mock(TaskService.class);
    FlowableFlowTaskService service = new FlowableFlowTaskService(taskService, Collections.emptyList());

    TaskQuery taskQuery = mock(TaskQuery.class);
    Task task = mock(Task.class);
    IdentityLink candidate = mock(IdentityLink.class);

    when(taskService.createTaskQuery()).thenReturn(taskQuery);
    when(taskQuery.taskId("task-1")).thenReturn(taskQuery);
    when(taskQuery.singleResult()).thenReturn(task);
    when(taskService.getIdentityLinksForTask("task-1")).thenReturn(Collections.singletonList(candidate));
    when(candidate.getUserId()).thenReturn("user-a");
    when(task.getAssignee()).thenReturn("other-user");

    var result = service.doAction("task-1", "approve", "user-a", null, null, null);

    assertTrue(result.isFailure());
    assertEquals("任务已被认领", result.getCause().getMessage());
    verify(taskService, never()).claim("task-1", "user-a");
  }

  @Test
  void doActionFailsWhenMatchingActionDoesNotExist() {
    TaskService taskService = mock(TaskService.class);
    @SuppressWarnings("unchecked")
    FlowTaskAction<Task> action = mock(FlowTaskAction.class);
    FlowableFlowTaskService service = new FlowableFlowTaskService(taskService, Collections.singletonList(action));

    TaskQuery taskQuery = mock(TaskQuery.class);
    Task task = mock(Task.class);
    IdentityLink candidate = mock(IdentityLink.class);

    when(taskService.createTaskQuery()).thenReturn(taskQuery);
    when(taskQuery.taskId("task-1")).thenReturn(taskQuery);
    when(taskQuery.singleResult()).thenReturn(task);
    when(taskService.getIdentityLinksForTask("task-1")).thenReturn(Arrays.asList(candidate));
    when(candidate.getUserId()).thenReturn("user-a");
    when(task.getAssignee()).thenReturn("user-a");
    when(task.getId()).thenReturn("task-1");
    configureApprovalNode(service, task, "approve-a", Collections.emptyList());
    when(action.supportTask(task)).thenReturn(true);
    when(action.actionName()).thenReturn("reject");

    var result = service.doAction("task-1", "approve", "user-a", null, null, Collections.singletonMap("approved", true));

    assertTrue(result.isFailure());
    assertEquals("未找到对应的流程动作: approve", result.getCause().getMessage());
    verify(action, never()).doAction(task);
  }

  @Test
  void doActionDoesNotClaimCandidateTaskWhenMatchingActionDoesNotExist() {
    TaskService taskService = mock(TaskService.class);
    @SuppressWarnings("unchecked")
    FlowTaskAction<Task> action = mock(FlowTaskAction.class);
    FlowableFlowTaskService service = new FlowableFlowTaskService(taskService, Collections.singletonList(action));

    TaskQuery taskQuery = mock(TaskQuery.class);
    Task task = mock(Task.class);
    IdentityLink candidate = mock(IdentityLink.class);

    when(taskService.createTaskQuery()).thenReturn(taskQuery);
    when(taskQuery.taskId("task-1")).thenReturn(taskQuery);
    when(taskQuery.singleResult()).thenReturn(task);
    when(taskService.getIdentityLinksForTask("task-1")).thenReturn(Collections.singletonList(candidate));
    when(candidate.getUserId()).thenReturn("user-a");
    when(task.getAssignee()).thenReturn(null);
    when(task.getId()).thenReturn("task-1");
    configureApprovalNode(service, task, "approve-a", Collections.emptyList());
    when(action.supportTask(task)).thenReturn(false);
    when(action.actionName()).thenReturn("approve");

    var result = service.doAction("task-1", "approve", "user-a", null, null, null);

    assertTrue(result.isFailure());
    assertEquals("未找到对应的流程动作: approve", result.getCause().getMessage());
    verify(taskService, never()).claim("task-1", "user-a");
    verify(action, never()).doAction(task);
  }

  @Test
  void doActionRejectsUnconfiguredAdvancedActionBeforeAnyWriteSideEffect() {
    TaskService taskService = mock(TaskService.class);
    RecordingTaskAction action = new RecordingTaskAction("transfer");
    FlowableFlowTaskService service = new FlowableFlowTaskService(taskService, Collections.singletonList(action));

    TaskQuery taskQuery = mock(TaskQuery.class);
    Task task = mock(Task.class);
    IdentityLink candidate = mock(IdentityLink.class);

    when(taskService.createTaskQuery()).thenReturn(taskQuery);
    when(taskQuery.taskId("task-1")).thenReturn(taskQuery);
    when(taskQuery.singleResult()).thenReturn(task);
    when(taskService.getIdentityLinksForTask("task-1")).thenReturn(Collections.singletonList(candidate));
    when(candidate.getUserId()).thenReturn("user-a");
    when(task.getId()).thenReturn("task-1");
    when(task.getAssignee()).thenReturn(null);
    when(task.getProcessInstanceId()).thenReturn("instance-1");
    configureApprovalNode(service, task, "approve-a", Collections.emptyList());

    var result = service.doAction(
        "task-1",
        "transfer",
        "user-a",
        "try transfer",
        Collections.singletonMap("amount", 99),
        Collections.singletonMap("targetUserId", "user-b"));

    assertTrue(result.isFailure());
    assertEquals("当前节点未配置流程动作: transfer", result.getCause().getMessage());
    verify(taskService, never()).claim("task-1", "user-a");
    verify(taskService, never()).setVariableLocal(eq("task-1"), eq(Constants.TASK_FORM_DATA_KEY), eq(Collections.singletonMap("amount", 99)));
    verify(taskService, never()).setVariableLocal(eq("task-1"), eq(Constants.TASK_PAYLOAD_KEY), anyString());
    verify(taskService, never()).addComment("task-1", "instance-1", "try transfer");
    assertNull(action.task);
  }

  @Test
  void doActionFailsClosedWhenCurrentFlowNodeCannotBeResolved() {
    TaskService taskService = mock(TaskService.class);
    RecordingTaskAction action = new RecordingTaskAction("approve");
    FlowableFlowTaskService service = new FlowableFlowTaskService(taskService, Collections.singletonList(action));

    TaskQuery taskQuery = mock(TaskQuery.class);
    Task task = mock(Task.class);
    IdentityLink candidate = mock(IdentityLink.class);

    when(taskService.createTaskQuery()).thenReturn(taskQuery);
    when(taskQuery.taskId("task-1")).thenReturn(taskQuery);
    when(taskQuery.singleResult()).thenReturn(task);
    when(taskService.getIdentityLinksForTask("task-1")).thenReturn(Collections.singletonList(candidate));
    when(candidate.getUserId()).thenReturn("user-a");
    when(task.getId()).thenReturn("task-1");
    when(task.getAssignee()).thenReturn(null);

    var result = service.doAction("task-1", "approve", "user-a", "approve", null, null);

    assertTrue(result.isFailure());
    assertEquals("未找到当前任务对应的流程节点: task-1", result.getCause().getMessage());
    verify(taskService, never()).claim("task-1", "user-a");
    verify(taskService, never()).addComment("task-1", null, "approve");
    assertNull(action.task);
  }

  @Test
  void doActionAllowsOnlyApproveForRuntimeAddSignDerivativeTask() {
    TaskService taskService = mock(TaskService.class);
    RecordingTaskAction action = new RecordingTaskAction(Constants.Value.APPROVED);
    FlowableFlowTaskService service = new FlowableFlowTaskService(taskService, Collections.singletonList(action));
    service.taskEventDispatcher = mock(com.ouroboros.workflow.approval.event.TaskEventDispatcher.class);

    TaskQuery taskQuery = mock(TaskQuery.class);
    Task task = mock(Task.class);

    when(taskService.createTaskQuery()).thenReturn(taskQuery);
    when(taskQuery.taskId("task-add-sign")).thenReturn(taskQuery);
    when(taskQuery.singleResult()).thenReturn(task);
    when(taskService.getIdentityLinksForTask("task-add-sign")).thenReturn(Collections.emptyList());
    when(task.getId()).thenReturn("task-add-sign");
    when(task.getAssignee()).thenReturn("user-c");
    when(taskService.getVariableLocal(
        "task-add-sign",
        Constants.Variable.ADD_SIGN_TASK_KIND,
        String.class)).thenReturn(Constants.Value.ADD_SIGN_TASK_KIND_ADDED);

    var approveResult = service.doAction("task-add-sign", "approve", "user-c", null, null, null);
    var addSignResult = service.doAction(
        "task-add-sign",
        "addSign",
        "user-c",
        "try nested add-sign",
        null,
        Collections.singletonMap("targetUserIds", Collections.singletonList("user-d")));

    assertTrue(approveResult.isSuccess());
    assertEquals(task, action.task);
    assertTrue(addSignResult.isFailure());
    assertEquals("当前节点未配置流程动作: addSign", addSignResult.getCause().getMessage());
    verify(taskService, never()).setVariableLocal(eq("task-add-sign"), eq(Constants.TASK_PAYLOAD_KEY), anyString());
    verify(taskService, never()).addComment("task-add-sign", null, "try nested add-sign");
  }

  @Test
  void doActionNormalizesFreeApprovalAliasBeforeAuthorizationAndActionLookup() {
    TaskService taskService = mock(TaskService.class);
    RecordingTaskAction action = new RecordingTaskAction(Constants.Value.FREE_APPROVE);
    FlowableFlowTaskService service = new FlowableFlowTaskService(taskService, Collections.singletonList(action));

    TaskQuery taskQuery = mock(TaskQuery.class);
    Task task = mock(Task.class);
    IdentityLink candidate = mock(IdentityLink.class);

    when(taskService.createTaskQuery()).thenReturn(taskQuery);
    when(taskQuery.taskId("task-1")).thenReturn(taskQuery);
    when(taskQuery.singleResult()).thenReturn(task);
    when(taskService.getIdentityLinksForTask("task-1")).thenReturn(Collections.singletonList(candidate));
    when(candidate.getUserId()).thenReturn("user-a");
    when(task.getId()).thenReturn("task-1");
    when(task.getAssignee()).thenReturn("user-a");
    configureApprovalNode(service, task, "approve-a", Collections.singletonList(Constants.Value.FREE_APPROVE));

    var result = service.doAction("task-1", "freeApproval", "user-a", null, null, null);

    assertTrue(result.isSuccess(), () -> result.getCause().getMessage());
    assertEquals(task, action.task);
  }

  @Test
  void doActionAllowsOnlyCompleteForHandleNode() {
    TaskService taskService = mock(TaskService.class);
    RecordingTaskAction completeAction = new RecordingTaskAction(Constants.Value.COMPLETE);
    FlowableFlowTaskService service = new FlowableFlowTaskService(taskService, Collections.singletonList(completeAction));

    TaskQuery taskQuery = mock(TaskQuery.class);
    Task task = mock(Task.class);
    IdentityLink candidate = mock(IdentityLink.class);

    when(taskService.createTaskQuery()).thenReturn(taskQuery);
    when(taskQuery.taskId("task-1")).thenReturn(taskQuery);
    when(taskQuery.singleResult()).thenReturn(task);
    when(taskService.getIdentityLinksForTask("task-1")).thenReturn(Collections.singletonList(candidate));
    when(candidate.getUserId()).thenReturn("user-a");
    when(task.getId()).thenReturn("task-1");
    when(task.getAssignee()).thenReturn("user-a");
    configureHandleNode(service, task, "handle-a");

    var completeResult = service.doAction("task-1", "complete", "user-a", null, null, null);
    var rejectResult = service.doAction("task-1", "reject", "user-a", null, null, null);

    assertTrue(completeResult.isSuccess());
    assertEquals(task, completeAction.task);
    assertTrue(rejectResult.isFailure());
    assertEquals("当前节点未配置流程动作: reject", rejectResult.getCause().getMessage());
  }

  @Test
  void doActionMarksTransactionRollbackOnlyWhenTaskFormSubmissionFailsAfterClaim() {
    TaskService taskService = mock(TaskService.class);
    RepositoryService repositoryService = mock(RepositoryService.class);
    FlowFormService flowFormService = mock(FlowFormService.class);
    RecordingTransactionManager transactionManager = new RecordingTransactionManager();
    RuntimeException failure = new RuntimeException("submit failed");

    @SuppressWarnings("unchecked")
    FlowTaskAction<Task> action = mock(FlowTaskAction.class);
    FlowableFlowTaskService target = new FlowableFlowTaskService(taskService, Collections.singletonList(action));
    target.repositoryService = repositoryService;
    target.flowFormServices = Collections.singletonList(flowFormService);
    FlowTaskService service = transactionalProxy(target, transactionManager);

    TaskQuery taskQuery = mock(TaskQuery.class);
    Task task = mock(Task.class);
    IdentityLink candidate = mock(IdentityLink.class);

    when(taskService.createTaskQuery()).thenReturn(taskQuery);
    when(taskQuery.taskId("task-1")).thenReturn(taskQuery);
    when(taskQuery.singleResult()).thenReturn(task);
    when(taskService.getIdentityLinksForTask("task-1")).thenReturn(Collections.singletonList(candidate));
    when(candidate.getUserId()).thenReturn("user-a");
    when(task.getId()).thenReturn("task-1");
    when(task.getAssignee()).thenReturn(null);
    when(task.getFormKey()).thenReturn("purchase-form");
    when(task.getProcessInstanceId()).thenReturn("instance-1");
    configureApprovalNode(target, task, "approve-a", Collections.emptyList());
    when(taskService.getVariable("task-1", Constants.TASK_FORM_TYPE_KEY, String.class)).thenReturn("default");
    when(flowFormService.getType()).thenReturn("default");
    when(flowFormService.submitTaskForm(eq("instance-1"), eq("task-1"), eq("purchase-form"), eq(null), eq("user-a"), anyList(), eq(Collections.singletonMap("amount", 99))))
        .thenReturn(Try.failure(failure));
    when(action.supportTask(task)).thenReturn(true);
    when(action.actionName()).thenReturn("approve");

    var result = service.doAction("task-1", "approve", "user-a", null, Collections.singletonMap("amount", 99), null);

    assertTrue(result.isFailure());
    assertEquals("submit failed", result.getCause().getMessage());
    assertEquals(0, transactionManager.getCommitCount());
    assertEquals(1, transactionManager.getRollbackCount());
    verify(taskService).claim("task-1", "user-a");
    verify(taskService, never()).setVariableLocal(eq("task-1"), eq(Constants.TASK_FORM_DATA_KEY), eq(Collections.singletonMap("amount", 99)));
    verify(action, never()).doAction(task);
  }

  @Test
  void doActionMergesSubmittedFormDataIntoCandidateFormDataVariable() {
    TaskService taskService = mock(TaskService.class);
    FlowFormService flowFormService = mock(FlowFormService.class);

    @SuppressWarnings("unchecked")
    FlowTaskAction<Task> action = mock(FlowTaskAction.class);
    FlowableFlowTaskService service = new FlowableFlowTaskService(taskService, Collections.singletonList(action));
    service.flowFormServices = Collections.singletonList(flowFormService);

    TaskQuery taskQuery = mock(TaskQuery.class);
    Task task = mock(Task.class);
    IdentityLink candidate = mock(IdentityLink.class);
    Map<String, Object> existingCandidateFormData = new HashMap<>();
    existingCandidateFormData.put("starterId", "user-starter");
    Map<String, Object> submittedFormData = new HashMap<>();
    submittedFormData.put("managerId", "user-manager");

    when(taskService.createTaskQuery()).thenReturn(taskQuery);
    when(taskQuery.taskId("task-1")).thenReturn(taskQuery);
    when(taskQuery.singleResult()).thenReturn(task);
    when(taskService.getIdentityLinksForTask("task-1")).thenReturn(Collections.singletonList(candidate));
    when(candidate.getUserId()).thenReturn("user-a");
    when(task.getId()).thenReturn("task-1");
    when(task.getAssignee()).thenReturn("user-a");
    when(task.getFormKey()).thenReturn("purchase-form");
    when(task.getProcessInstanceId()).thenReturn("instance-1");
    configureApprovalNode(service, task, "approve-a", Collections.emptyList());
    when(taskService.getVariable("task-1", Constants.TASK_FORM_TYPE_KEY, String.class)).thenReturn("default");
    when(taskService.getVariable("task-1", Constants.CANDIDATE_FORM_DATA_KEY)).thenReturn(existingCandidateFormData);
    when(flowFormService.getType()).thenReturn("default");
    when(flowFormService.submitTaskForm(eq("instance-1"), eq("task-1"), eq("purchase-form"), eq(null), eq("user-a"), anyList(), eq(submittedFormData)))
        .thenReturn(Try.success(null));
    when(action.supportTask(task)).thenReturn(true);
    when(action.actionName()).thenReturn("approve");
    when(action.doAction(task)).thenReturn(Try.success(null));

    var result = service.doAction("task-1", "approve", "user-a", null, submittedFormData, null);

    assertTrue(result.isSuccess(), () -> result.getCause().getMessage());
    Map<String, Object> expectedCandidateFormData = new HashMap<>();
    expectedCandidateFormData.put("starterId", "user-starter");
    expectedCandidateFormData.put("managerId", "user-manager");
    verify(taskService).setVariable("task-1", Constants.CANDIDATE_FORM_DATA_KEY, expectedCandidateFormData);
    verify(taskService).setVariableLocal("task-1", Constants.TASK_FORM_DATA_KEY, submittedFormData);
    verify(action).doAction(task);
  }

  @Test
  void doActionMarksTransactionRollbackOnlyWhenEngineActionReturnsFailure() {
    TaskService taskService = mock(TaskService.class);
    RecordingTransactionManager transactionManager = new RecordingTransactionManager();
    RuntimeException failure = new RuntimeException("engine action failed");

    @SuppressWarnings("unchecked")
    FlowTaskAction<Task> action = mock(FlowTaskAction.class);
    FlowableFlowTaskService target = new FlowableFlowTaskService(taskService, Collections.singletonList(action));
    FlowTaskService service = transactionalProxy(target, transactionManager);

    TaskQuery taskQuery = mock(TaskQuery.class);
    Task task = mock(Task.class);
    IdentityLink candidate = mock(IdentityLink.class);

    when(taskService.createTaskQuery()).thenReturn(taskQuery);
    when(taskQuery.taskId("task-1")).thenReturn(taskQuery);
    when(taskQuery.singleResult()).thenReturn(task);
    when(taskService.getIdentityLinksForTask("task-1")).thenReturn(Collections.singletonList(candidate));
    when(candidate.getUserId()).thenReturn("user-a");
    when(task.getId()).thenReturn("task-1");
    when(task.getAssignee()).thenReturn(null);
    configureApprovalNode(target, task, "approve-a", Collections.emptyList());
    when(action.supportTask(task)).thenReturn(true);
    when(action.actionName()).thenReturn("approve");
    when(action.doAction(task)).thenReturn(Try.failure(failure));

    var result = service.doAction("task-1", "approve", "user-a", "looks good", null, null);

    assertTrue(result.isFailure());
    assertEquals("engine action failed", result.getCause().getMessage());
    assertEquals(0, transactionManager.getCommitCount());
    assertEquals(1, transactionManager.getRollbackCount());
    verify(taskService).claim("task-1", "user-a");
    verify(taskService).addComment("task-1", null, "looks good");
    verify(action).doAction(task);
  }

  @Test
  void doActionAllowsCurrentAssigneeEvenWhenNotInCandidateList() {
    TaskService taskService = mock(TaskService.class);
    FlowTaskAction<Task> action = mock(FlowTaskAction.class);
    FlowableFlowTaskService service = new FlowableFlowTaskService(taskService, Collections.singletonList(action));

    TaskQuery taskQuery = mock(TaskQuery.class);
    Task task = mock(Task.class);

    when(taskService.createTaskQuery()).thenReturn(taskQuery);
    when(taskQuery.taskId("task-1")).thenReturn(taskQuery);
    when(taskQuery.singleResult()).thenReturn(task);
    when(taskService.getIdentityLinksForTask("task-1")).thenReturn(Collections.emptyList());
    when(task.getId()).thenReturn("task-1");
    when(task.getAssignee()).thenReturn("user-a");
    configureApprovalNode(service, task, "approve-a", Collections.emptyList());
    when(action.supportTask(task)).thenReturn(true);
    when(action.actionName()).thenReturn("approve");
    when(action.doAction(task)).thenReturn(Try.success(null));

    var result = service.doAction("task-1", "approve", "user-a", null, null, null);

    assertTrue(result.isSuccess(), () -> result.getCause().getMessage());
    verify(taskService, never()).claim("task-1", "user-a");
    verify(action).doAction(task);
  }

  @Test
  void doActionAllowsSystemActorWithoutClaimingTask() {
    TaskService taskService = mock(TaskService.class);
    RecordingTaskAction action = new RecordingTaskAction("approve");
    FlowableFlowTaskService service = new FlowableFlowTaskService(taskService, Collections.singletonList(action));

    TaskQuery taskQuery = mock(TaskQuery.class);
    Task task = mock(Task.class);
    IdentityLink candidate = mock(IdentityLink.class);

    when(taskService.createTaskQuery()).thenReturn(taskQuery);
    when(taskQuery.taskId("task-1")).thenReturn(taskQuery);
    when(taskQuery.singleResult()).thenReturn(task);
    when(taskService.getIdentityLinksForTask("task-1")).thenReturn(Collections.singletonList(candidate));
    when(candidate.getUserId()).thenReturn("human-user");
    when(task.getId()).thenReturn("task-1");
    when(task.getAssignee()).thenReturn("claimed-user");
    configureApprovalNode(
        service,
        task,
        "approve-a",
        Collections.emptyList(),
        timeout(Constants.Value.TIMEOUT_ACTION_AUTO_APPROVE, null));

    var result = service.execute(new com.ouroboros.workflow.approval.TrustedFlowTaskAction(
        "task-1", "approve", "timeout: autoApprove", null, null, "timeout-fact-1",
        com.ouroboros.workflow.approval.TrustedFlowTaskAction.Origin.TIMEOUT));

    assertTrue(result.isSuccess(), () -> result.getCause().getMessage());
    assertEquals(task, action.task);
    verify(taskService, never()).claim(anyString(), anyString());
    verify(taskService).addComment("task-1", null, "timeout: autoApprove");
  }

  @Test
  void doActionLetsSystemActorClaimUnassignedTransferTask() {
    TaskService taskService = mock(TaskService.class);
    FlowableTransferAction transferAction = new FlowableTransferAction();
    setField(transferAction, "taskService", taskService);
    FlowableFlowTaskService service = new FlowableFlowTaskService(taskService, Collections.singletonList(transferAction));

    TaskQuery taskQuery = mock(TaskQuery.class);
    Task task = mock(Task.class);
    Task refreshedTask = mock(Task.class);
    IdentityLink candidate = mock(IdentityLink.class);

    when(taskService.createTaskQuery()).thenReturn(taskQuery);
    when(taskQuery.taskId("task-1")).thenReturn(taskQuery);
    when(taskQuery.singleResult()).thenReturn(task, refreshedTask, refreshedTask);
    when(taskService.getIdentityLinksForTask("task-1")).thenReturn(Collections.singletonList(candidate));
    when(candidate.getUserId()).thenReturn("human-user");
    when(candidate.getType()).thenReturn("candidate");
    when(task.getId()).thenReturn("task-1");
    when(task.getAssignee()).thenReturn(null);
    configureApprovalNode(
        service,
        task,
        "approve-a",
        Collections.emptyList(),
        timeout(Constants.Value.TIMEOUT_ACTION_TRANSFER, "user-b"));
    when(refreshedTask.getId()).thenReturn("task-1");
    when(refreshedTask.getAssignee()).thenReturn(FlowableFlowTaskService.SYSTEM_ACTOR_USER_ID);
    when(refreshedTask.getProcessInstanceId()).thenReturn("instance-1");
    when(taskService.getVariableLocal("task-1", Constants.TASK_PAYLOAD_KEY)).thenReturn("{\"targetUserId\":\"user-b\"}");

    var result = service.execute(new com.ouroboros.workflow.approval.TrustedFlowTaskAction(
        "task-1", "transfer", "timeout: transfer", null,
        Collections.singletonMap("targetUserId", "user-b"), "timeout-fact-2",
        com.ouroboros.workflow.approval.TrustedFlowTaskAction.Origin.TIMEOUT));

    assertTrue(result.isSuccess(), () -> result.getCause().getMessage());
    verify(taskService).claim("task-1", FlowableFlowTaskService.SYSTEM_ACTOR_USER_ID);
    verify(taskService).setAssignee("task-1", "user-b");
    verify(taskService).addCandidateUser("task-1", "user-b");
    verify(taskService).addComment("task-1", "instance-1", "timeout: transfer");
    verify(taskService).addComment(
        "task-1",
        "instance-1",
        "transfer: " + FlowableFlowTaskService.SYSTEM_ACTOR_USER_ID + " -> user-b");
  }

  @Test
  void doActionDoesNotLetHumanUserUseTimeoutOnlyAction() {
    TaskService taskService = mock(TaskService.class);
    RecordingTaskAction action = new RecordingTaskAction(Constants.Value.REJECTED);
    FlowableFlowTaskService service = new FlowableFlowTaskService(taskService, Collections.singletonList(action));

    TaskQuery taskQuery = mock(TaskQuery.class);
    Task task = mock(Task.class);
    IdentityLink candidate = mock(IdentityLink.class);

    when(taskService.createTaskQuery()).thenReturn(taskQuery);
    when(taskQuery.taskId("task-1")).thenReturn(taskQuery);
    when(taskQuery.singleResult()).thenReturn(task);
    when(taskService.getIdentityLinksForTask("task-1")).thenReturn(Collections.singletonList(candidate));
    when(candidate.getUserId()).thenReturn("user-a");
    when(task.getId()).thenReturn("task-1");
    when(task.getAssignee()).thenReturn("user-a");
    configureApprovalNode(
        service,
        task,
        "approve-a",
        Collections.emptyList(),
        timeout(Constants.Value.TIMEOUT_ACTION_AUTO_REJECT, null));

    var result = service.doAction("task-1", "reject", "user-a", "reject", null, null);

    assertTrue(result.isFailure());
    assertEquals("当前节点未配置流程动作: reject", result.getCause().getMessage());
    verify(taskService, never()).addComment("task-1", null, "reject");
    assertNull(action.task);
  }

  @Test
  void doActionRejectsSystemActorTimeoutTransferWhenPayloadTargetDoesNotMatchDefinition() {
    TaskService taskService = mock(TaskService.class);
    FlowableTransferAction transferAction = new FlowableTransferAction();
    setField(transferAction, "taskService", taskService);
    FlowableFlowTaskService service = new FlowableFlowTaskService(taskService, Collections.singletonList(transferAction));

    TaskQuery taskQuery = mock(TaskQuery.class);
    Task task = mock(Task.class);
    IdentityLink candidate = mock(IdentityLink.class);

    when(taskService.createTaskQuery()).thenReturn(taskQuery);
    when(taskQuery.taskId("task-1")).thenReturn(taskQuery);
    when(taskQuery.singleResult()).thenReturn(task);
    when(taskService.getIdentityLinksForTask("task-1")).thenReturn(Collections.singletonList(candidate));
    when(candidate.getUserId()).thenReturn("human-user");
    when(task.getId()).thenReturn("task-1");
    when(task.getAssignee()).thenReturn(null);
    configureApprovalNode(
        service,
        task,
        "approve-a",
        Collections.emptyList(),
        timeout(Constants.Value.TIMEOUT_ACTION_TRANSFER, "user-b"));

    var result = service.execute(new com.ouroboros.workflow.approval.TrustedFlowTaskAction(
        "task-1", "transfer", "timeout: transfer", null,
        Collections.singletonMap("targetUserId", "user-c"), "timeout-fact-3",
        com.ouroboros.workflow.approval.TrustedFlowTaskAction.Origin.TIMEOUT));

    assertTrue(result.isFailure());
    assertEquals("当前节点未配置流程动作: transfer", result.getCause().getMessage());
    verify(taskService, never()).claim("task-1", FlowableFlowTaskService.SYSTEM_ACTOR_USER_ID);
    verify(taskService, never()).setVariableLocal(eq("task-1"), eq(Constants.TASK_PAYLOAD_KEY), anyString());
    verify(taskService, never()).addComment("task-1", null, "timeout: transfer");
  }

  @Test
  void externalCallbackCannotUseNonDecisionTrustedAction() {
    TaskService taskService = mock(TaskService.class);
    FlowableTransferAction transferAction = new FlowableTransferAction();
    setField(transferAction, "taskService", taskService);
    FlowableFlowTaskService service = new FlowableFlowTaskService(
        taskService, Collections.singletonList(transferAction));

    TaskQuery taskQuery = mock(TaskQuery.class);
    Task task = mock(Task.class);
    when(taskService.createTaskQuery()).thenReturn(taskQuery);
    when(taskQuery.taskId("task-1")).thenReturn(taskQuery);
    when(taskQuery.singleResult()).thenReturn(task);
    when(taskService.getIdentityLinksForTask("task-1")).thenReturn(Collections.emptyList());
    when(task.getId()).thenReturn("task-1");
    when(task.getAssignee()).thenReturn("user-a");
    configureApprovalNode(service, task, "approve-a", Collections.singletonList("transfer"));

    var result = service.execute(new com.ouroboros.workflow.approval.TrustedFlowTaskAction(
        "task-1", "transfer", "external callback", null,
        Collections.singletonMap("targetUserId", "user-b"), "callback-fact-1",
        com.ouroboros.workflow.approval.TrustedFlowTaskAction.Origin.EXTERNAL_CALLBACK));

    assertTrue(result.isFailure());
    assertEquals("当前节点未配置流程动作: transfer", result.getCause().getMessage());
    verify(taskService, never()).setAssignee("task-1", "user-b");
  }

  @Test
  void doActionTransferReassignsTaskAndUpdatesCandidates() {
    TaskService taskService = mock(TaskService.class);
    FlowableTransferAction transferAction = new FlowableTransferAction();
    setField(transferAction, "taskService", taskService);
    FlowableFlowTaskService service = new FlowableFlowTaskService(taskService, Collections.singletonList(transferAction));

    TaskQuery taskQuery = mock(TaskQuery.class);
    Task task = mock(Task.class);
    IdentityLink candidate = mock(IdentityLink.class);

    when(taskService.createTaskQuery()).thenReturn(taskQuery);
    when(taskQuery.taskId("task-1")).thenReturn(taskQuery);
    when(taskQuery.singleResult()).thenReturn(task);
    when(taskService.getIdentityLinksForTask("task-1")).thenReturn(Collections.singletonList(candidate));
    when(candidate.getUserId()).thenReturn("user-a");
    when(candidate.getType()).thenReturn("candidate");
    when(task.getId()).thenReturn("task-1");
    when(task.getAssignee()).thenReturn("user-a");
    when(task.getProcessInstanceId()).thenReturn("instance-1");
    configureApprovalNode(service, task, "approve-a", Collections.singletonList(Constants.Value.TIMEOUT_ACTION_TRANSFER));
    when(taskService.getVariableLocal("task-1", Constants.TASK_PAYLOAD_KEY)).thenReturn("{\"targetUserId\":\"user-b\"}");

    var result = service.doAction(
        "task-1",
        "transfer",
        "user-a",
        "transfer to backup approver",
        Collections.emptyMap(),
        Collections.singletonMap("targetUserId", "user-b"));

    assertTrue(result.isSuccess());
    verify(taskService).setAssignee("task-1", "user-b");
    verify(taskService).deleteCandidateUser("task-1", "user-a");
    verify(taskService).addCandidateUser("task-1", "user-b");
    verify(taskService).setVariableLocal("task-1", Constants.Variable.LOCAL_APPROVAL_ACTION, "transfer");
    verify(taskService).addComment("task-1", "instance-1", "transfer to backup approver");
    verify(taskService).addComment("task-1", "instance-1", "transfer: user-a -> user-b");
  }

  private FlowTaskService transactionalProxy(FlowableFlowTaskService target, RecordingTransactionManager transactionManager) {
    ProxyFactory proxyFactory = new ProxyFactory(target);
    proxyFactory.addAdvice(new TransactionInterceptor(transactionManager, new AnnotationTransactionAttributeSource()));
    return (FlowTaskService) proxyFactory.getProxy();
  }

  private SingleApprovalNode configureApprovalNode(
      FlowableFlowTaskService service,
      Task task,
      String nodeId,
      List<String> actions
  ) {
    return configureApprovalNode(service, task, nodeId, actions, null);
  }

  private SingleApprovalNode configureApprovalNode(
      FlowableFlowTaskService service,
      Task task,
      String nodeId,
      List<String> actions,
      ApprovalNode.Timeout timeout
  ) {
    SingleApprovalNode approvalNode = new SingleApprovalNode();
    approvalNode.setId(nodeId);
    approvalNode.setActions(actions);
    approvalNode.setTimeout(timeout);
    configureFlowNode(service, task, nodeId, approvalNode);
    return approvalNode;
  }

  private HandleNode configureHandleNode(FlowableFlowTaskService service, Task task, String nodeId) {
    HandleNode handleNode = new HandleNode();
    handleNode.setId(nodeId);
    configureFlowNode(service, task, nodeId, handleNode);
    return handleNode;
  }

  private void configureFlowNode(
      FlowableFlowTaskService service,
      Task task,
      String nodeId,
      FlowNode flowNode
  ) {
    service.taskEventDispatcher = mock(com.ouroboros.workflow.approval.event.TaskEventDispatcher.class);
    RepositoryService repositoryService = mock(RepositoryService.class);
    service.repositoryService = repositoryService;
    when(task.getProcessDefinitionId()).thenReturn("process-definition-1");
    when(task.getTaskDefinitionKey()).thenReturn(nodeId);
    when(repositoryService.getBpmnModel("process-definition-1")).thenReturn(buildBpmnModel(flowNode));
  }

  private BpmnModel buildBpmnModel(FlowNode flowNode) {
    BpmnModel model = new BpmnModel();
    Process process = new Process();
    model.addProcess(process);
    BpmnUtil.saveFlowDefinitionRawJson(process, deployedFlowJson(flowNode));
    return model;
  }

  private String deployedFlowJson(FlowNode flowNode) {
    return "{"
        + "\"formatVersion\":2,"
        + "\"id\":\"flow-a\","
        + "\"name\":\"Flow A\","
        + "\"category\":\"\","
        + "\"icon\":\"\","
        + "\"description\":\"\","
        + "\"formType\":\"default\","
        + "\"formKey\":\"\","
        + "\"nodes\":["
        + "{\"type\":\"发起节点\",\"id\":\"startnode\",\"name\":\"发起节点\",\"permission\":[]},"
        + flowNodeJson(flowNode) + ","
        + "{\"type\":\"结束节点\",\"id\":\"endnode\",\"name\":\"结束节点\"}"
        + "],"
        + "\"edges\":["
        + "{\"id\":\"e1\",\"type\":\"SEQUENCE\",\"sourceId\":\"startnode\",\"targetId\":" + Json.toJsonString(flowNode.getId()) + "},"
        + "{\"id\":\"e2\",\"type\":\"SEQUENCE\",\"sourceId\":" + Json.toJsonString(flowNode.getId()) + ",\"targetId\":\"endnode\"}"
        + "]"
        + "}";
  }

  private String flowNodeJson(FlowNode flowNode) {
    var permissionJson = Json.toJsonString(flowNode.getPermission() == null ? Collections.emptyList() : flowNode.getPermission());
    if (flowNode instanceof HandleNode) {
      return "{"
          + "\"type\":\"办理节点\","
          + "\"id\":" + Json.toJsonString(flowNode.getId()) + ","
          + "\"name\":\"办理节点\","
          + "\"candidates\":[{\"type\":\"User\",\"users\":[\"user-a\"]}],"
          + "\"permission\":" + permissionJson
          + "}";
    }

    ApprovalNode approvalNode = (ApprovalNode) flowNode;
    var actionsJson = Json.toJsonString(approvalNode.getActions() == null ? Collections.emptyList() : approvalNode.getActions());
    var timeoutJson = approvalNode.getTimeout() == null ? "" : ",\"timeout\":" + Json.toJsonString(approvalNode.getTimeout());
    return "{"
        + "\"type\":\"审批节点\","
        + "\"id\":" + Json.toJsonString(flowNode.getId()) + ","
        + "\"name\":\"审批节点\","
        + "\"subType\":\"单签\","
        + "\"candidates\":[],"
        + "\"permission\":" + permissionJson + ","
        + "\"actions\":" + actionsJson
        + timeoutJson
        + "}";
  }

  private ApprovalNode.Timeout timeout(String action, String transferTo) {
    ApprovalNode.Timeout timeout = new ApprovalNode.Timeout();
    timeout.setAction(action);
    timeout.setTransferTo(transferTo);
    return timeout;
  }

  private void setField(Object target, String fieldName, Object value) {
    try {
      Field field = target.getClass().getDeclaredField(fieldName);
      field.setAccessible(true);
      field.set(target, value);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("Failed to set field " + fieldName, e);
    }
  }

  private static class RecordingTransactionManager implements PlatformTransactionManager {
    private int commitCount;
    private int rollbackCount;

    @Override
    public TransactionStatus getTransaction(TransactionDefinition definition) {
      return new SimpleTransactionStatus();
    }

    @Override
    public void commit(TransactionStatus status) {
      if (status.isRollbackOnly()) {
        rollbackCount++;
        return;
      }
      commitCount++;
    }

    @Override
    public void rollback(TransactionStatus status) {
      rollbackCount++;
    }

    int getCommitCount() {
      return commitCount;
    }

    int getRollbackCount() {
      return rollbackCount;
    }
  }

  private static class RecordingTaskAction implements FlowTaskAction<Task> {
    private final String actionName;
    private Task task;

    private RecordingTaskAction(String actionName) {
      this.actionName = actionName;
    }

    @Override
    public String actionName() {
      return actionName;
    }

    @Override
    public Boolean supportTask(Object task) {
      return task instanceof Task;
    }

    @Override
    public Try<Void> doAction(Task task) {
      this.task = task;
      return Try.success(null);
    }
  }
}
