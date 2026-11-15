package com.ouroboros.workflow.approval.flowable.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.TaskService;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.runtime.DataObject;
import org.flowable.engine.task.Comment;
import org.flowable.identitylink.api.IdentityLink;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.flowable.task.api.history.HistoricTaskInstanceQuery;
import org.flowable.variable.api.history.HistoricVariableInstance;
import org.flowable.variable.api.history.HistoricVariableInstanceQuery;
import org.junit.jupiter.api.Test;

import com.ouroboros.workflow.approval.model.FlowTaskDetail;
import com.ouroboros.workflow.approval.flowable.transpiler.Constants;

public class FlowableFlowTaskServiceReadModelTest {
  @Test
  void getTaskListMapsActiveTaskDetailsFromFlowableQueries() {
    TaskService taskService = mock(TaskService.class);
    HistoryService historyService = mock(HistoryService.class);
    RepositoryService repositoryService = mock(RepositoryService.class);
    FlowableFlowTaskService service = new FlowableFlowTaskService(taskService, Collections.emptyList());
    service.historyService = historyService;
    service.repositoryService = repositoryService;

    TaskQuery taskQuery = mock(TaskQuery.class);
    Task task = mock(Task.class);
    HistoricTaskInstanceQuery historicTaskQuery = mock(HistoricTaskInstanceQuery.class);
    HistoricTaskInstance historicTask = mock(HistoricTaskInstance.class);
    HistoricVariableInstanceQuery variableQuery = mock(HistoricVariableInstanceQuery.class);
    HistoricVariableInstance starterVariable = mock(HistoricVariableInstance.class);
    ProcessDefinition definition = mock(ProcessDefinition.class);
    IdentityLink candidate = mock(IdentityLink.class);
    IdentityLink assigneeLink = mock(IdentityLink.class);

    when(taskService.createTaskQuery()).thenReturn(taskQuery);
    when(taskQuery.taskCandidateOrAssigned("user-a")).thenReturn(taskQuery);
    when(taskQuery.active()).thenReturn(taskQuery);
    when(taskQuery.orderByTaskCreateTime()).thenReturn(taskQuery);
    when(taskQuery.desc()).thenReturn(taskQuery);
    when(taskQuery.listPage(0, 20)).thenReturn(Collections.singletonList(task));

    when(task.getId()).thenReturn("task-1");
    when(task.getName()).thenReturn("经理审批");
    when(task.getProcessInstanceId()).thenReturn("instance-1");
    when(task.getProcessDefinitionId()).thenReturn("process-definition-1");
    when(task.getTaskDefinitionKey()).thenReturn("approve-a");
    when(task.getAssignee()).thenReturn("approver-1");

    when(historyService.createHistoricVariableInstanceQuery()).thenReturn(variableQuery);
    when(variableQuery.processInstanceId("instance-1")).thenReturn(variableQuery);
    when(variableQuery.variableName("__processStarter")).thenReturn(variableQuery);
    when(variableQuery.listPage(0, 1)).thenReturn(Collections.singletonList(starterVariable));
    when(starterVariable.getValue()).thenReturn("starter-1");

    when(historyService.createHistoricTaskInstanceQuery()).thenReturn(historicTaskQuery);
    when(historicTaskQuery.taskId("task-1")).thenReturn(historicTaskQuery);
    when(historicTaskQuery.singleResult()).thenReturn(historicTask);
    when(historicTask.getStartTime()).thenReturn(dateOf(2026, Month.MARCH, 18, 9, 30));
    when(historicTask.getEndTime()).thenReturn(null);

    when(repositoryService.getProcessDefinition("process-definition-1")).thenReturn(definition);
    when(definition.getKey()).thenReturn("flow-a");
    when(definition.getName()).thenReturn("采购审批");

    when(taskService.getIdentityLinksForTask("task-1")).thenReturn(Arrays.asList(candidate, assigneeLink));
    when(candidate.getType()).thenReturn("candidate");
    when(candidate.getUserId()).thenReturn("candidate-1");
    when(assigneeLink.getType()).thenReturn("assignee");
    when(assigneeLink.getUserId()).thenReturn("approver-1");

    List<FlowTaskDetail> details = service.getTaskList("user-a", 0, 20);

    assertEquals(1, details.size());
    FlowTaskDetail detail = details.get(0);
    assertEquals("task-1", detail.getId());
    assertEquals("经理审批", detail.getName());
    assertEquals("instance-1", detail.getInstanceId());
    assertEquals("flow-a", detail.getFlowKey());
    assertEquals("采购审批", detail.getFlowName());
    assertEquals("approve-a", detail.getTaskDefinitionKey());
    assertEquals("starter-1", detail.getStarter());
    assertEquals("approver-1", detail.getAssignee());
    assertEquals(Arrays.asList("candidate-1", "approver-1"), detail.getCandidateUsers());
    assertEquals(LocalDateTime.of(2026, 3, 18, 9, 30), detail.getStartTime());
    assertNull(detail.getEndTime());
    assertEquals(Boolean.TRUE, detail.isActive());
  }

  @Test
  void getCandidateUsersIncludesMultipleApprovalAssigneesWhenIdentityLinksOnlyContainCurrentTaskAssignee() {
    TaskService taskService = mock(TaskService.class);
    FlowableFlowTaskService service = new FlowableFlowTaskService(taskService, Collections.emptyList());

    TaskQuery taskQuery = mock(TaskQuery.class);
    Task task = mock(Task.class);
    IdentityLink assigneeLink = mock(IdentityLink.class);

    when(taskService.createTaskQuery()).thenReturn(taskQuery);
    when(taskQuery.taskId("task-1")).thenReturn(taskQuery);
    when(taskQuery.singleResult()).thenReturn(task);
    when(task.getTaskDefinitionKey()).thenReturn("parallel-approval");

    when(taskService.getIdentityLinksForTask("task-1")).thenReturn(Collections.singletonList(assigneeLink));
    when(assigneeLink.getType()).thenReturn("assignee");
    when(assigneeLink.getUserId()).thenReturn("approver-1");
    when(taskService.getVariable(
        "task-1",
        Constants.Variable.getMultipleApprovalAssigneeListVariableName("parallel-approval")))
        .thenReturn(Arrays.asList("approver-1", "approver-2", "approver-2", "", null));

    List<String> candidates = service.getCandidateUsers("task-1");

    assertEquals(Arrays.asList("approver-1", "approver-2"), candidates);
  }

  @Test
  void getHistoryTaskListMapsFinishedTaskDetails() {
    TaskService taskService = mock(TaskService.class);
    HistoryService historyService = mock(HistoryService.class);
    RepositoryService repositoryService = mock(RepositoryService.class);
    FlowableFlowTaskService service = new FlowableFlowTaskService(taskService, Collections.emptyList());
    service.historyService = historyService;
    service.repositoryService = repositoryService;

    HistoricTaskInstanceQuery historicTaskQuery = mock(HistoricTaskInstanceQuery.class);
    HistoricTaskInstance historicTask = mock(HistoricTaskInstance.class);
    HistoricVariableInstanceQuery variableQuery = mock(HistoricVariableInstanceQuery.class);
    HistoricVariableInstance starterVariable = mock(HistoricVariableInstance.class);
    ProcessDefinition definition = mock(ProcessDefinition.class);

    when(historyService.createHistoricTaskInstanceQuery()).thenReturn(historicTaskQuery);
    when(historicTaskQuery.taskAssignee("user-a")).thenReturn(historicTaskQuery);
    when(historicTaskQuery.finished()).thenReturn(historicTaskQuery);
    when(historicTaskQuery.orderByHistoricTaskInstanceEndTime()).thenReturn(historicTaskQuery);
    when(historicTaskQuery.desc()).thenReturn(historicTaskQuery);
    when(historicTaskQuery.listPage(0, 20)).thenReturn(Collections.singletonList(historicTask));

    when(historicTask.getId()).thenReturn("task-h-1");
    when(historicTask.getName()).thenReturn("财务审批");
    when(historicTask.getProcessInstanceId()).thenReturn("instance-2");
    when(historicTask.getProcessDefinitionId()).thenReturn("process-definition-2");
    when(historicTask.getTaskDefinitionKey()).thenReturn("approve-b");
    when(historicTask.getAssignee()).thenReturn("user-a");
    when(historicTask.getStartTime()).thenReturn(dateOf(2026, Month.MARCH, 17, 10, 0));
    when(historicTask.getEndTime()).thenReturn(dateOf(2026, Month.MARCH, 17, 11, 15));

    when(historyService.createHistoricVariableInstanceQuery()).thenReturn(variableQuery);
    when(variableQuery.processInstanceId("instance-2")).thenReturn(variableQuery);
    when(variableQuery.variableName("__processStarter")).thenReturn(variableQuery);
    when(variableQuery.singleResult()).thenReturn(starterVariable);
    when(starterVariable.getValue()).thenReturn("starter-2");

    when(repositoryService.getProcessDefinition("process-definition-2")).thenReturn(definition);
    when(definition.getKey()).thenReturn("flow-b");
    when(definition.getName()).thenReturn("报销审批");

    List<FlowTaskDetail> details = service.getHistoryTaskList("user-a", 0, 20);

    assertEquals(1, details.size());
    FlowTaskDetail detail = details.get(0);
    assertEquals("task-h-1", detail.getId());
    assertEquals("flow-b", detail.getFlowKey());
    assertEquals("报销审批", detail.getFlowName());
    assertEquals("starter-2", detail.getStarter());
    assertEquals("user-a", detail.getAssignee());
    assertEquals(LocalDateTime.of(2026, 3, 17, 10, 0), detail.getStartTime());
    assertEquals(LocalDateTime.of(2026, 3, 17, 11, 15), detail.getEndTime());
    assertEquals(Boolean.FALSE, detail.isActive());
    assertEquals(Collections.emptyList(), detail.getCandidateUsers());
  }

  @Test
  void setCandidateUsersOnlyAppliesDiff() {
    TaskService taskService = mock(TaskService.class);
    HistoryService historyService = mock(HistoryService.class);
    RepositoryService repositoryService = mock(RepositoryService.class);
    FlowableFlowTaskService service = new FlowableFlowTaskService(taskService, Collections.emptyList());
    service.historyService = historyService;
    service.repositoryService = repositoryService;

    IdentityLink keep = mock(IdentityLink.class);
    IdentityLink remove = mock(IdentityLink.class);
    when(taskService.getIdentityLinksForTask("task-1")).thenReturn(Arrays.asList(keep, remove));
    when(keep.getType()).thenReturn("candidate");
    when(keep.getUserId()).thenReturn("user-keep");
    when(remove.getType()).thenReturn("candidate");
    when(remove.getUserId()).thenReturn("user-remove");

    service.setCandidateUsers("task-1", Arrays.asList("user-keep", "user-add"));

    verify(taskService).deleteCandidateUser("task-1", "user-remove");
    verify(taskService).addCandidateUser("task-1", "user-add");
    verify(taskService, never()).deleteCandidateUser("task-1", "user-keep");
    verify(taskService, never()).addCandidateUser("task-1", "user-keep");
  }

  @Test
  void getTaskDetailReturnsNullWhenTaskMissing() {
    TaskService taskService = mock(TaskService.class);
    HistoryService historyService = mock(HistoryService.class);
    RepositoryService repositoryService = mock(RepositoryService.class);
    FlowableFlowTaskService service = new FlowableFlowTaskService(taskService, Collections.emptyList());
    service.historyService = historyService;
    service.repositoryService = repositoryService;

    TaskQuery taskQuery = mock(TaskQuery.class);
    when(taskService.createTaskQuery()).thenReturn(taskQuery);
    when(taskQuery.taskId("task-missing")).thenReturn(taskQuery);
    when(taskQuery.singleResult()).thenReturn(null);

    assertNull(service.getTaskDetail("task-missing"));
  }

  @Test
  void getTaskCountUsesCandidateOrAssignedActiveQuery() {
    TaskService taskService = mock(TaskService.class);
    FlowableFlowTaskService service = new FlowableFlowTaskService(taskService, Collections.emptyList());

    TaskQuery taskQuery = mock(TaskQuery.class);
    when(taskService.createTaskQuery()).thenReturn(taskQuery);
    when(taskQuery.active()).thenReturn(taskQuery);
    when(taskQuery.taskCandidateOrAssigned("user-a")).thenReturn(taskQuery);
    when(taskQuery.count()).thenReturn(3L);

    assertEquals(3L, service.getTaskCount("user-a"));
  }

  @Test
  void getTaskListOrdersByCreateTimeDescBeforeListing() {
    TaskService taskService = mock(TaskService.class);
    HistoryService historyService = mock(HistoryService.class);
    RepositoryService repositoryService = mock(RepositoryService.class);
    FlowableFlowTaskService service = new FlowableFlowTaskService(taskService, Collections.emptyList());
    service.historyService = historyService;
    service.repositoryService = repositoryService;

    TaskQuery taskQuery = mock(TaskQuery.class);
    TaskQuery orderedQuery = mock(TaskQuery.class);

    when(taskService.createTaskQuery()).thenReturn(taskQuery);
    when(taskQuery.active()).thenReturn(taskQuery);
    when(taskQuery.taskCandidateOrAssigned("user-a")).thenReturn(taskQuery);
    when(taskQuery.list()).thenThrow(new AssertionError("active task list should order by create time before listing"));
    when(taskQuery.orderByTaskCreateTime()).thenReturn(orderedQuery);
    when(orderedQuery.desc()).thenReturn(orderedQuery);
    when(orderedQuery.list()).thenReturn(Collections.emptyList());

    assertEquals(Collections.emptyList(), service.getTaskList("user-a"));
  }

  @Test
  void getTaskListByFlowKeyOrdersByCreateTimeDescBeforeListing() {
    TaskService taskService = mock(TaskService.class);
    HistoryService historyService = mock(HistoryService.class);
    RepositoryService repositoryService = mock(RepositoryService.class);
    FlowableFlowTaskService service = new FlowableFlowTaskService(taskService, Collections.emptyList());
    service.historyService = historyService;
    service.repositoryService = repositoryService;

    TaskQuery taskQuery = mock(TaskQuery.class);
    TaskQuery orderedQuery = mock(TaskQuery.class);

    when(taskService.createTaskQuery()).thenReturn(taskQuery);
    when(taskQuery.active()).thenReturn(taskQuery);
    when(taskQuery.taskCandidateOrAssigned("user-a")).thenReturn(taskQuery);
    when(taskQuery.processDefinitionKey("flow-a")).thenReturn(taskQuery);
    when(taskQuery.list()).thenThrow(new AssertionError("filtered active task list should order by create time before listing"));
    when(taskQuery.orderByTaskCreateTime()).thenReturn(orderedQuery);
    when(orderedQuery.desc()).thenReturn(orderedQuery);
    when(orderedQuery.list()).thenReturn(Collections.emptyList());

    assertEquals(Collections.emptyList(), service.getTaskList("user-a", "flow-a"));
  }

  @Test
  void getTaskListByFlowKeyOrdersByCreateTimeDescBeforePaging() {
    TaskService taskService = mock(TaskService.class);
    HistoryService historyService = mock(HistoryService.class);
    RepositoryService repositoryService = mock(RepositoryService.class);
    FlowableFlowTaskService service = new FlowableFlowTaskService(taskService, Collections.emptyList());
    service.historyService = historyService;
    service.repositoryService = repositoryService;

    TaskQuery taskQuery = mock(TaskQuery.class);
    TaskQuery orderedQuery = mock(TaskQuery.class);

    when(taskService.createTaskQuery()).thenReturn(taskQuery);
    when(taskQuery.active()).thenReturn(taskQuery);
    when(taskQuery.taskCandidateOrAssigned("user-a")).thenReturn(taskQuery);
    when(taskQuery.processDefinitionKey("flow-a")).thenReturn(taskQuery);
    when(taskQuery.listPage(0, 20)).thenThrow(new AssertionError("filtered active task list should order by create time before paging"));
    when(taskQuery.orderByTaskCreateTime()).thenReturn(orderedQuery);
    when(orderedQuery.desc()).thenReturn(orderedQuery);
    when(orderedQuery.listPage(0, 20)).thenReturn(Collections.emptyList());

    assertEquals(Collections.emptyList(), service.getTaskList("user-a", "flow-a", 0, 20));
  }

  @Test
  void getAllActiveTaskListOrdersByCreateTimeDescBeforeListing() {
    TaskService taskService = mock(TaskService.class);
    HistoryService historyService = mock(HistoryService.class);
    RepositoryService repositoryService = mock(RepositoryService.class);
    FlowableFlowTaskService service = new FlowableFlowTaskService(taskService, Collections.emptyList());
    service.historyService = historyService;
    service.repositoryService = repositoryService;

    TaskQuery taskQuery = mock(TaskQuery.class);
    TaskQuery orderedQuery = mock(TaskQuery.class);

    when(taskService.createTaskQuery()).thenReturn(taskQuery);
    when(taskQuery.active()).thenReturn(taskQuery);
    when(taskQuery.list()).thenThrow(new AssertionError("all active task list should order by create time before listing"));
    when(taskQuery.orderByTaskCreateTime()).thenReturn(orderedQuery);
    when(orderedQuery.desc()).thenReturn(orderedQuery);
    when(orderedQuery.list()).thenReturn(Collections.emptyList());

    assertEquals(Collections.emptyList(), service.getAllActiveTaskList());
  }

  @Test
  void readHelpersExposePayloadFormCommentAndAssignee() {
    TaskService taskService = mock(TaskService.class);
    FlowableFlowTaskService service = new FlowableFlowTaskService(taskService, Collections.emptyList());

    DataObject dataObject = mock(DataObject.class);
    Comment oldComment = mock(Comment.class);
    Comment latestComment = mock(Comment.class);
    TaskQuery taskQuery = mock(TaskQuery.class);
    Task task = mock(Task.class);
    Map<String, Object> formData = new HashMap<>();
    formData.put("amount", 99);

    when(taskService.getDataObject("task-1", Constants.TASK_PAYLOAD_KEY)).thenReturn(dataObject);
    when(dataObject.getValue()).thenReturn("{\"approved\":true}");
    when(taskService.getVariableLocal("task-1", Constants.TASK_FORM_DATA_KEY)).thenReturn(formData);
    when(taskService.getTaskComments("task-1")).thenReturn(Arrays.asList(oldComment, latestComment));
    when(oldComment.getTime()).thenReturn(dateOf(2026, Month.MARCH, 18, 8, 0));
    when(oldComment.getFullMessage()).thenReturn("old comment");
    when(latestComment.getTime()).thenReturn(dateOf(2026, Month.MARCH, 18, 8, 30));
    when(latestComment.getFullMessage()).thenReturn("latest comment");
    when(taskService.createTaskQuery()).thenReturn(taskQuery);
    when(taskQuery.taskId("task-1")).thenReturn(taskQuery);
    when(taskQuery.singleResult()).thenReturn(task);
    when(task.getAssignee()).thenReturn("approver-1");

    Map<?, ?> payload = service.getPayload("task-1", HashMap.class);

    assertEquals(Boolean.TRUE, payload.get("approved"));
    assertEquals(formData, service.getTaskFormData("task-1"));
    assertEquals("latest comment", service.getComment("task-1"));
    assertEquals("approver-1", service.getTaskAssignee("task-1"));

    service.setTaskAssignee("task-1", "approver-2");

    verify(taskService).setAssignee("task-1", "approver-2");
  }

  private Date dateOf(int year, Month month, int day, int hour, int minute) {
    return Date.from(LocalDateTime.of(year, month, day, hour, minute)
        .atZone(ZoneId.systemDefault())
        .toInstant());
  }
}
