package com.ouroboros.workflow.approval.flowable.action;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.identitylink.api.IdentityLink;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.junit.jupiter.api.Test;

import io.vavr.control.Try;

import com.ouroboros.workflow.approval.flowable.transpiler.Constants;
import com.ouroboros.workflow.approval.flowable.listener.AddSignTaskCompletionListener;
import com.ouroboros.workflow.approval.flowable.service.FlowableAddSignTaskManager;

class FlowableTaskActionTest {
  @Test
  void approveActionSetsApprovalVariablesAndCompletesTask() throws Exception {
    assertActionBehavior(
        "com.ouroboros.workflow.approval.flowable.action.FlowableApproveAction",
        "approve",
        true);
  }

  @Test
  void rejectActionSetsApprovalVariablesAndCompletesTask() throws Exception {
    assertActionBehavior(
        "com.ouroboros.workflow.approval.flowable.action.FlowableRejectAction",
        "reject",
        false);
  }

  @Test
  void rejectActionKeepsTaskActiveWhenRejectingToCurrentNode() throws Exception {
    TaskService taskService = mock(TaskService.class);
    Task task = mock(Task.class);
    when(task.getId()).thenReturn("task-1");
    when(task.getTaskDefinitionKey()).thenReturn("approve-a");
    when(taskService.getVariableLocal("task-1", Constants.TASK_PAYLOAD_KEY))
        .thenReturn(Collections.singletonMap("targetNodeId", "approve-a"));

    Object action = Class.forName("com.ouroboros.workflow.approval.flowable.action.FlowableRejectAction")
        .getDeclaredConstructor()
        .newInstance();
    setField(action, "taskService", taskService);

    Try<?> result = (Try<?>) invoke(action, "doAction", Task.class, task);

    assertTrue(result.isSuccess());
    verify(taskService).setVariableLocal("task-1", Constants.Variable.LOCAL_APPROVAL_ACTION, "reject");
    verify(taskService, never()).complete("task-1");
  }

  @Test
  void rollbackActionSetsApprovalVariablesAndCompletesTask() throws Exception {
    assertActionBehavior(
        "com.ouroboros.workflow.approval.flowable.action.FlowableRollbackAction",
        "rollback",
        false);
  }

  @Test
  void completeActionOnlySupportsHandleTasks() throws Exception {
    TaskService taskService = mock(TaskService.class);
    Task task = mock(Task.class);
    when(task.getId()).thenReturn("task-1");
    when(taskService.getVariableLocal("task-1", Constants.Variable.TASK_KIND))
        .thenReturn(Constants.Value.TASK_KIND_HANDLE);

    Object completeAction = Class.forName("com.ouroboros.workflow.approval.flowable.action.FlowableCompleteAction")
        .getDeclaredConstructor()
        .newInstance();
    setField(completeAction, "taskService", taskService);

    assertEquals(Constants.Value.COMPLETE, invoke(completeAction, "actionName"));
    assertEquals(Boolean.TRUE, invoke(completeAction, "supportTask", Object.class, task));

    Try<?> result = (Try<?>) invoke(completeAction, "doAction", Task.class, task);

    assertTrue(result.isSuccess());
    verify(taskService).setVariableLocal("task-1", Constants.Variable.LOCAL_APPROVAL_ACTION, Constants.Value.COMPLETE);
    verify(taskService).complete("task-1");
  }

  @Test
  void approvalActionsDoNotSupportHandleTasks() throws Exception {
    TaskService taskService = mock(TaskService.class);
    Task task = mock(Task.class);
    when(task.getId()).thenReturn("task-1");
    when(taskService.getVariableLocal("task-1", Constants.Variable.TASK_KIND))
        .thenReturn(Constants.Value.TASK_KIND_HANDLE);

    Object approveAction = Class.forName("com.ouroboros.workflow.approval.flowable.action.FlowableApproveAction")
        .getDeclaredConstructor()
        .newInstance();
    setField(approveAction, "taskService", taskService);
    setField(approveAction, "addSignTaskCompletionListener", mock(AddSignTaskCompletionListener.class));
    setField(approveAction, "addSignTaskManager", mock(FlowableAddSignTaskManager.class));

    Object rejectAction = Class.forName("com.ouroboros.workflow.approval.flowable.action.FlowableRejectAction")
        .getDeclaredConstructor()
        .newInstance();
    setField(rejectAction, "taskService", taskService);

    Object rollbackAction = Class.forName("com.ouroboros.workflow.approval.flowable.action.FlowableRollbackAction")
        .getDeclaredConstructor()
        .newInstance();
    setField(rollbackAction, "taskService", taskService);

    assertEquals(Boolean.FALSE, invoke(approveAction, "supportTask", Object.class, task));
    assertEquals(Boolean.FALSE, invoke(rejectAction, "supportTask", Object.class, task));
    assertEquals(Boolean.FALSE, invoke(rollbackAction, "supportTask", Object.class, task));
  }

  @Test
  void transferActionReassignsTaskAndUpdatesCandidateUsers() throws Exception {
    TaskService taskService = mock(TaskService.class);
    Task task = mock(Task.class);
    TaskQuery taskQuery = mock(TaskQuery.class);
    IdentityLink candidateA = mock(IdentityLink.class);
    IdentityLink candidateC = mock(IdentityLink.class);
    when(task.getId()).thenReturn("task-1");
    when(task.getAssignee()).thenReturn("user-a");
    when(task.getProcessInstanceId()).thenReturn("instance-1");
    when(taskService.createTaskQuery()).thenReturn(taskQuery);
    when(taskQuery.taskId("task-1")).thenReturn(taskQuery);
    when(taskQuery.singleResult()).thenReturn(task);
    when(taskService.getVariableLocal("task-1", Constants.TASK_PAYLOAD_KEY)).thenReturn("{\"targetUserId\":\"user-b\"}");
    when(taskService.getIdentityLinksForTask("task-1")).thenReturn(Arrays.asList(candidateA, candidateC));
    when(candidateA.getType()).thenReturn("candidate");
    when(candidateA.getUserId()).thenReturn("user-a");
    when(candidateC.getType()).thenReturn("candidate");
    when(candidateC.getUserId()).thenReturn("user-c");

    Object action = Class.forName("com.ouroboros.workflow.approval.flowable.action.FlowableTransferAction").getDeclaredConstructor().newInstance();
    setField(action, "taskService", taskService);

    assertEquals("transfer", invoke(action, "actionName"));
    assertEquals(Boolean.TRUE, invoke(action, "supportTask", Object.class, task));

    Try<?> result = (Try<?>) invoke(action, "doAction", Task.class, task);

    assertTrue(result.isSuccess());
    verify(taskService).setAssignee("task-1", "user-b");
    verify(taskService).deleteCandidateUser("task-1", "user-a");
    verify(taskService).addCandidateUser("task-1", "user-b");
    verify(taskService).setVariableLocal("task-1", Constants.Variable.LOCAL_APPROVAL_ACTION, "transfer");
    verify(taskService).addComment("task-1", "instance-1", "transfer: user-a -> user-b");
  }

  @Test
  void transferActionRejectsBlankTargetUser() throws Exception {
    TaskService taskService = mock(TaskService.class);
    Task task = mock(Task.class);
    TaskQuery taskQuery = mock(TaskQuery.class);
    when(task.getId()).thenReturn("task-1");
    when(task.getAssignee()).thenReturn("user-a");
    when(taskService.createTaskQuery()).thenReturn(taskQuery);
    when(taskQuery.taskId("task-1")).thenReturn(taskQuery);
    when(taskQuery.singleResult()).thenReturn(task);
    when(taskService.getVariableLocal("task-1", Constants.TASK_PAYLOAD_KEY)).thenReturn("{\"targetUserId\":\"\"}");
    when(taskService.getIdentityLinksForTask("task-1")).thenReturn(Collections.emptyList());

    Object action = Class.forName("com.ouroboros.workflow.approval.flowable.action.FlowableTransferAction").getDeclaredConstructor().newInstance();
    setField(action, "taskService", taskService);

    Try<?> result = (Try<?>) invoke(action, "doAction", Task.class, task);

    assertTrue(result.isFailure());
    assertEquals("转办目标用户不能为空", result.getCause().getMessage());
    verify(taskService, never()).setAssignee("task-1", "");
    verify(taskService, never()).setVariableLocal("task-1", Constants.Variable.LOCAL_APPROVAL_ACTION, "transfer");
  }

  @Test
  void transferActionRejectsWhenTaskNotClaimed() throws Exception {
    TaskService taskService = mock(TaskService.class);
    Task task = mock(Task.class);
    TaskQuery taskQuery = mock(TaskQuery.class);
    when(task.getId()).thenReturn("task-1");
    when(task.getAssignee()).thenReturn(null);
    when(taskService.createTaskQuery()).thenReturn(taskQuery);
    when(taskQuery.taskId("task-1")).thenReturn(taskQuery);
    when(taskQuery.singleResult()).thenReturn(task);

    Object action = Class.forName("com.ouroboros.workflow.approval.flowable.action.FlowableTransferAction").getDeclaredConstructor().newInstance();
    setField(action, "taskService", taskService);

    Try<?> result = (Try<?>) invoke(action, "doAction", Task.class, task);

    assertFalse(result.isSuccess());
    assertEquals("任务未被认领，无法转办", result.getCause().getMessage());
    verify(taskService, never()).setAssignee("task-1", "user-b");
  }

  @Test
  void addSignBeforeCurrentAssignsTaskToInsertedApprover() throws Exception {
    TaskService taskService = mock(TaskService.class);
    RuntimeService runtimeService = mock(RuntimeService.class);
    Task task = mock(Task.class);
    Task runtimeTask = mock(Task.class);
    TaskQuery taskQuery = mock(TaskQuery.class);
    FlowableAddSignTaskManager addSignTaskManager = mock(FlowableAddSignTaskManager.class);

    when(task.getId()).thenReturn("task-1");
    when(runtimeTask.getTaskDefinitionKey()).thenReturn("approve-a");
    when(runtimeTask.getAssignee()).thenReturn("user-a");
    when(runtimeTask.getProcessInstanceId()).thenReturn("instance-1");
    when(runtimeTask.getProcessDefinitionId()).thenReturn("process-def-1");
    when(runtimeTask.getName()).thenReturn("审批任务A");
    when(taskService.createTaskQuery()).thenReturn(taskQuery);
    when(taskQuery.taskId("task-1")).thenReturn(taskQuery);
    when(taskQuery.active()).thenReturn(taskQuery);
    when(taskQuery.singleResult()).thenReturn(runtimeTask);
    when(taskService.getVariableLocal("task-1", Constants.TASK_PAYLOAD_KEY))
        .thenReturn("{\"position\":\"BEFORE_CURRENT\",\"targetUserIds\":[\"user-b\",\"user-c\"]}");

    Object action = Class.forName("com.ouroboros.workflow.approval.flowable.action.FlowableAddSignAction")
        .getDeclaredConstructor(RuntimeService.class)
        .newInstance(runtimeService);
    setField(action, "taskService", taskService);
    setField(action, "addSignTaskManager", addSignTaskManager);

    Try<?> result = (Try<?>) invoke(action, "doAction", Task.class, task);

    assertTrue(result.isSuccess());
    verify(addSignTaskManager).createBeforeCurrentGroup(runtimeTask, Arrays.asList("user-b", "user-c"));
    verify(taskService).setVariableLocal("task-1", Constants.Variable.LOCAL_APPROVAL_ACTION, "addSign");
    verify(taskService, never()).complete("task-1");
  }

  @Test
  void addSignAfterCurrentPreparesGroupAndKeepsCurrentTaskActive() throws Exception {
    TaskService taskService = mock(TaskService.class);
    RuntimeService runtimeService = mock(RuntimeService.class);
    Task task = mock(Task.class);
    Task runtimeTask = mock(Task.class);
    TaskQuery taskQuery = mock(TaskQuery.class);
    FlowableAddSignTaskManager addSignTaskManager = mock(FlowableAddSignTaskManager.class);

    when(task.getId()).thenReturn("task-1");
    when(runtimeTask.getTaskDefinitionKey()).thenReturn("approve-a");
    when(runtimeTask.getAssignee()).thenReturn("user-a");
    when(taskService.createTaskQuery()).thenReturn(taskQuery);
    when(taskQuery.taskId("task-1")).thenReturn(taskQuery);
    when(taskQuery.active()).thenReturn(taskQuery);
    when(taskQuery.singleResult()).thenReturn(runtimeTask);
    when(taskService.getVariableLocal("task-1", Constants.TASK_PAYLOAD_KEY))
        .thenReturn("{\"position\":\"AFTER_CURRENT\",\"targetUserIds\":[\"user-b\"]}");

    Object action = Class.forName("com.ouroboros.workflow.approval.flowable.action.FlowableAddSignAction")
        .getDeclaredConstructor(RuntimeService.class)
        .newInstance(runtimeService);
    setField(action, "taskService", taskService);
    setField(action, "addSignTaskManager", addSignTaskManager);

    Try<?> result = (Try<?>) invoke(action, "doAction", Task.class, task);

    assertTrue(result.isSuccess());
    verify(addSignTaskManager).prepareAfterCurrentGroup(runtimeTask, Collections.singletonList("user-b"));
    verify(taskService).setVariableLocal("task-1", Constants.Variable.LOCAL_APPROVAL_ACTION, "addSign");
    verify(taskService, never()).complete("task-1");
  }

  @Test
  void addSignRejectsEmptyTargetUsers() throws Exception {
    TaskService taskService = mock(TaskService.class);
    RuntimeService runtimeService = mock(RuntimeService.class);
    Task task = mock(Task.class);
    TaskQuery taskQuery = mock(TaskQuery.class);

    when(task.getId()).thenReturn("task-1");
    when(task.getTaskDefinitionKey()).thenReturn("approve-a");
    when(task.getAssignee()).thenReturn("user-a");
    when(taskService.createTaskQuery()).thenReturn(taskQuery);
    when(taskQuery.taskId("task-1")).thenReturn(taskQuery);
    when(taskQuery.active()).thenReturn(taskQuery);
    when(taskQuery.singleResult()).thenReturn(task);
    when(taskService.getVariableLocal("task-1", Constants.TASK_PAYLOAD_KEY))
        .thenReturn("{\"position\":\"BEFORE_CURRENT\",\"targetUserIds\":[]}");

    Object action = Class.forName("com.ouroboros.workflow.approval.flowable.action.FlowableAddSignAction")
        .getDeclaredConstructor(RuntimeService.class)
        .newInstance(runtimeService);
    setField(action, "taskService", taskService);

    Try<?> result = (Try<?>) invoke(action, "doAction", Task.class, task);

    assertTrue(result.isFailure());
    assertEquals("加签目标用户不能为空", result.getCause().getMessage());
    verify(taskService, never()).setAssignee("task-1", "user-b");
  }

  @Test
  void freeApproveSetsConsultContextAndAssignsFirstConsultUser() throws Exception {
    TaskService taskService = mock(TaskService.class);
    Task task = mock(Task.class);
    TaskQuery taskQuery = mock(TaskQuery.class);

    when(task.getId()).thenReturn("task-1");
    when(task.getTaskDefinitionKey()).thenReturn("approve-a");
    when(task.getAssignee()).thenReturn("user-a");
    when(taskService.createTaskQuery()).thenReturn(taskQuery);
    when(taskQuery.taskId("task-1")).thenReturn(taskQuery);
    when(taskQuery.active()).thenReturn(taskQuery);
    when(taskQuery.singleResult()).thenReturn(task);
    when(taskService.getVariableLocal("task-1", Constants.TASK_PAYLOAD_KEY))
        .thenReturn("{\"consultUserIds\":[\"user-b\",\"user-c\"]}");

    Object action = Class.forName("com.ouroboros.workflow.approval.flowable.action.FlowableFreeApprovalAction")
        .getDeclaredConstructor()
        .newInstance();
    setField(action, "taskService", taskService);

    Try<?> result = (Try<?>) invoke(action, "doAction", Task.class, task);

    assertTrue(result.isSuccess());
    verify(taskService).setVariable("task-1", Constants.Variable.FREE_APPROVAL_DETOUR_ACTIVE, Boolean.TRUE);
    verify(taskService).setVariable("task-1", Constants.Variable.FREE_APPROVAL_CONSULT_USERS, Arrays.asList("user-b", "user-c"));
    verify(taskService).setVariable("task-1", Constants.Variable.FREE_APPROVAL_SOURCE_NODE, "approve-a");
    verify(taskService).setVariableLocal("task-1", Constants.Variable.FREE_APPROVAL_SOURCE_ASSIGNEE, "user-a");
    verify(taskService).setVariableLocal("task-1", Constants.Variable.ADD_SIGN_PENDING_USERS, Arrays.asList("user-b", "user-c"));
    verify(taskService).setAssignee("task-1", "user-b");
    verify(taskService, never()).complete("task-1");
  }

  @Test
  void approveActionReturnsToSourceAssigneeAfterFreeApproveConsultation() throws Exception {
    TaskService taskService = mock(TaskService.class);
    Task task = mock(Task.class);
    Task runtimeTask = mock(Task.class);
    TaskQuery taskQuery = mock(TaskQuery.class);
    AddSignTaskCompletionListener addSignTaskCompletionListener = mock(AddSignTaskCompletionListener.class);

    when(task.getId()).thenReturn("task-1");
    when(taskService.getVariableLocal("task-1", Constants.Variable.ADD_SIGN_WAITING)).thenReturn(Boolean.FALSE);
    when(taskService.getVariableLocal("task-1", Constants.Variable.ADD_SIGN_PENDING_USERS))
        .thenReturn(Collections.singletonList("user-b"));
    when(taskService.getVariableLocal("task-1", Constants.Variable.ADD_SIGN_AFTER_CURRENT_PENDING)).thenReturn(Boolean.FALSE);
    when(taskService.getVariable("task-1", Constants.Variable.FREE_APPROVAL_DETOUR_ACTIVE, Boolean.class)).thenReturn(Boolean.TRUE);
    when(taskService.getVariableLocal("task-1", Constants.Variable.FREE_APPROVAL_SOURCE_ASSIGNEE, String.class)).thenReturn("user-a");
    when(taskService.createTaskQuery()).thenReturn(taskQuery);
    when(taskQuery.taskId("task-1")).thenReturn(taskQuery);
    when(taskQuery.active()).thenReturn(taskQuery);
    when(taskQuery.singleResult()).thenReturn(runtimeTask);
    when(runtimeTask.getAssignee()).thenReturn("user-b");
    when(addSignTaskCompletionListener.onTaskApproved(runtimeTask)).thenReturn(Boolean.FALSE);

    Object action = Class.forName("com.ouroboros.workflow.approval.flowable.action.FlowableApproveAction")
        .getDeclaredConstructor()
        .newInstance();
    setField(action, "taskService", taskService);
    setField(action, "addSignTaskCompletionListener", addSignTaskCompletionListener);
    setField(action, "addSignTaskManager", mock(FlowableAddSignTaskManager.class));

    Try<?> result = (Try<?>) invoke(action, "doAction", Task.class, task);

    assertTrue(result.isSuccess());
    verify(taskService).setVariable("task-1", Constants.Variable.FREE_APPROVAL_DETOUR_ACTIVE, Boolean.FALSE);
    verify(taskService).removeVariable("task-1", Constants.Variable.FREE_APPROVAL_CONSULT_USERS);
    verify(taskService).removeVariable("task-1", Constants.Variable.FREE_APPROVAL_SOURCE_NODE);
    verify(taskService).removeVariableLocal("task-1", Constants.Variable.FREE_APPROVAL_SOURCE_ASSIGNEE);
    verify(taskService).setAssignee("task-1", "user-a");
    verify(taskService, never()).complete("task-1");
  }

  @Test
  void approveActionCompletesTaskAfterCurrentAddSignAndActivatesGroup() throws Exception {
    TaskService taskService = mock(TaskService.class);
    Task task = mock(Task.class);
    Task runtimeTask = mock(Task.class);
    TaskQuery taskQuery = mock(TaskQuery.class);
    AddSignTaskCompletionListener addSignTaskCompletionListener = mock(AddSignTaskCompletionListener.class);
    FlowableAddSignTaskManager addSignTaskManager = mock(FlowableAddSignTaskManager.class);

    when(task.getId()).thenReturn("task-1");
    when(taskService.getVariableLocal("task-1", Constants.Variable.ADD_SIGN_WAITING)).thenReturn(Boolean.FALSE);
    when(taskService.getVariableLocal("task-1", Constants.Variable.ADD_SIGN_GROUP_ID, String.class)).thenReturn("group-1");
    when(taskService.getVariableLocal("task-1", Constants.Variable.ADD_SIGN_PENDING_USERS)).thenReturn(Collections.emptyList());
    when(addSignTaskCompletionListener.onTaskApproved(runtimeTask)).thenReturn(Boolean.FALSE);
    when(taskService.createTaskQuery()).thenReturn(taskQuery);
    when(taskQuery.taskId("task-1")).thenReturn(taskQuery);
    when(taskQuery.active()).thenReturn(taskQuery);
    when(taskQuery.singleResult()).thenReturn(runtimeTask);
    when(runtimeTask.getProcessInstanceId()).thenReturn("instance-1");

    Object action = Class.forName("com.ouroboros.workflow.approval.flowable.action.FlowableApproveAction")
        .getDeclaredConstructor()
        .newInstance();
    setField(action, "taskService", taskService);
    setField(action, "addSignTaskCompletionListener", addSignTaskCompletionListener);
    setField(action, "addSignTaskManager", addSignTaskManager);

    Try<?> result = (Try<?>) invoke(action, "doAction", Task.class, task);

    assertTrue(result.isSuccess());
    verify(taskService).complete("task-1");
    verify(addSignTaskManager).activateAfterCurrentGroup("instance-1", "group-1");
  }

  @Test
  void approveActionDelegatesToIndependentAddSignCompletionListener() throws Exception {
    TaskService taskService = mock(TaskService.class);
    Task task = mock(Task.class);
    Task runtimeTask = mock(Task.class);
    TaskQuery taskQuery = mock(TaskQuery.class);
    AddSignTaskCompletionListener addSignTaskCompletionListener = mock(AddSignTaskCompletionListener.class);
    FlowableAddSignTaskManager addSignTaskManager = mock(FlowableAddSignTaskManager.class);

    when(task.getId()).thenReturn("task-1");
    when(taskService.getVariableLocal("task-1", Constants.Variable.ADD_SIGN_WAITING)).thenReturn(Boolean.FALSE);
    when(addSignTaskCompletionListener.onTaskApproved(runtimeTask)).thenReturn(Boolean.TRUE);
    when(taskService.createTaskQuery()).thenReturn(taskQuery);
    when(taskQuery.taskId("task-1")).thenReturn(taskQuery);
    when(taskQuery.active()).thenReturn(taskQuery);
    when(taskQuery.singleResult()).thenReturn(runtimeTask);

    Object action = Class.forName("com.ouroboros.workflow.approval.flowable.action.FlowableApproveAction")
        .getDeclaredConstructor()
        .newInstance();
    setField(action, "taskService", taskService);
    setField(action, "addSignTaskCompletionListener", addSignTaskCompletionListener);
    setField(action, "addSignTaskManager", addSignTaskManager);

    Try<?> result = (Try<?>) invoke(action, "doAction", Task.class, task);

    assertTrue(result.isSuccess());
    verify(taskService, never()).complete("task-1");
  }

  private void assertActionBehavior(String className, String expectedAction, boolean approved) throws Exception {
    TaskService taskService = mock(TaskService.class);
    Task task = mock(Task.class);
    Task runtimeTask = mock(Task.class);
    TaskQuery taskQuery = mock(TaskQuery.class);
    when(task.getId()).thenReturn("task-1");

    if ("approve".equals(expectedAction)) {
      when(taskService.createTaskQuery()).thenReturn(taskQuery);
      when(taskQuery.taskId("task-1")).thenReturn(taskQuery);
      when(taskQuery.active()).thenReturn(taskQuery);
      when(taskQuery.singleResult()).thenReturn(runtimeTask);
      when(taskService.getVariableLocal("task-1", Constants.Variable.ADD_SIGN_WAITING)).thenReturn(Boolean.FALSE);
      when(taskService.getVariableLocal("task-1", Constants.Variable.ADD_SIGN_PENDING_USERS)).thenReturn(Collections.emptyList());
      when(runtimeTask.getProcessInstanceId()).thenReturn("instance-1");
    }

    Object action = Class.forName(className).getDeclaredConstructor().newInstance();
    setField(action, "taskService", taskService);
    AddSignTaskCompletionListener addSignTaskCompletionListener = mock(AddSignTaskCompletionListener.class);
    when(addSignTaskCompletionListener.onTaskApproved(runtimeTask)).thenReturn(Boolean.FALSE);
    setFieldIfPresent(action, "addSignTaskCompletionListener", addSignTaskCompletionListener);
    setFieldIfPresent(action, "addSignTaskManager", mock(FlowableAddSignTaskManager.class));

    assertEquals(expectedAction, invoke(action, "actionName"));
    assertEquals(Boolean.TRUE, invoke(action, "supportTask", Object.class, task));

    Try<?> result = (Try<?>) invoke(action, "doAction", Task.class, task);

    assertTrue(result.isSuccess());
    verify(taskService).setVariableLocal("task-1", Constants.Variable.LOCAL_APPROVAL_ACTION, expectedAction);
    verify(taskService).complete("task-1");
  }

  private Object invoke(Object target, String methodName) throws Exception {
    Method method = target.getClass().getMethod(methodName);
    return method.invoke(target);
  }

  private Object invoke(Object target, String methodName, Class<?> argumentType, Object argument) throws Exception {
    Method method = target.getClass().getMethod(methodName, argumentType);
    return method.invoke(target, argument);
  }

  private void setField(Object target, String fieldName, Object value) throws Exception {
    Field field = target.getClass().getDeclaredField(fieldName);
    field.setAccessible(true);
    field.set(target, value);
  }

  private void setFieldIfPresent(Object target, String fieldName, Object value) throws Exception {
    try {
      setField(target, fieldName, value);
    } catch (NoSuchFieldException ignored) {
      // Ignore when field does not exist on current action type.
    }
  }
}
