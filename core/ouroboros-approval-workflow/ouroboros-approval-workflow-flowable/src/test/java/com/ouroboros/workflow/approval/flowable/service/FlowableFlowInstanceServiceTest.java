package com.ouroboros.workflow.approval.flowable.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.Process;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.history.HistoricProcessInstanceQuery;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.repository.ProcessDefinitionQuery;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.runtime.ProcessInstanceQuery;
import org.flowable.identitylink.api.IdentityLink;
import org.flowable.identitylink.api.history.HistoricIdentityLink;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.flowable.task.api.history.HistoricTaskInstanceQuery;
import org.flowable.variable.api.history.HistoricVariableInstance;
import org.flowable.variable.api.history.HistoricVariableInstanceQuery;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.SimpleTransactionStatus;

import io.vavr.control.Try;

import com.ouroboros.uischema.schema.SchemaNode;
import com.ouroboros.util.SPI;
import com.ouroboros.workflow.approval.CurrentUserSupplier;
import com.ouroboros.workflow.approval.FlowInstanceService;
import com.ouroboros.workflow.approval.FlowDefinitionService;
import com.ouroboros.workflow.approval.FlowFormService;
import com.ouroboros.workflow.approval.event.InstanceEventDispatcher;
import com.ouroboros.workflow.approval.definition.NodeFormFieldPermission;
import com.ouroboros.workflow.approval.definition.nodes.SingleApprovalNode;
import com.ouroboros.workflow.approval.flowable.transpiler.Constants;
import com.ouroboros.workflow.approval.flowable.util.BpmnUtil;
import com.ouroboros.workflow.approval.listener.FlowInstanceListener;
import com.ouroboros.workflow.approval.model.FlowDurationStatistic;
import com.ouroboros.workflow.approval.model.FlowInstanceDetail;
import com.ouroboros.workflow.approval.model.FlowInstanceStatistic;
import com.ouroboros.workflow.approval.model.FlowProcessInfo;
import com.ouroboros.workflow.approval.model.FlowTimeline;
import com.ouroboros.workflow.approval.model.TaskState;
import com.ouroboros.workflow.approval.model.events.FlowInstanceEvent;

public class FlowableFlowInstanceServiceTest {
  @Test
  void boundStartReservesAndBindsTheCanonicalCaseInsideTheEngineService() {
    var commands = mock(FlowInstanceCommandService.class);
    var cases = mock(com.ouroboros.workflow.approval.lifecycle.ApprovalCaseService.class);
    var service = new FlowableFlowInstanceService(commands, mock(FlowInstanceQueryService.class));
    service.approvalCaseService = cases;
    when(cases.reserveSubmission("sales.Order", "42", "flow-a"))
        .thenReturn(new com.ouroboros.workflow.approval.lifecycle.ApprovalCaseService.Submission("case", 2));
    when(commands.start(eq("flow-a"), eq("actor"), any(), any(), any(), any()))
        .thenAnswer(call -> {
          Map<String, Object> variables = call.getArgument(3);
          assertEquals("42", variables.get("businessRecordId"));
          assertEquals("case", variables.get("approvalCaseId"));
          assertEquals(2, variables.get("submissionRound"));
          return Try.success("execution");
        });
    assertTrue(service.start("flow-a", "actor", Map.of(),
        Map.of("businessRecordType", "sales.Order", "businessRecordId", 42), Map.of()).isSuccess());
    verify(cases).bindExecution("case", 2, "execution");
  }

  @Test
  void aRejectedLocalStartReleasesItsReservationWithoutBindingAnExecution() {
    var commands = mock(FlowInstanceCommandService.class);
    var cases = mock(com.ouroboros.workflow.approval.lifecycle.ApprovalCaseService.class);
    var service = new FlowableFlowInstanceService(commands, mock(FlowInstanceQueryService.class));
    service.approvalCaseService = cases;
    when(cases.reserveSubmission("sales.Order", "42", "flow-a"))
        .thenReturn(new com.ouroboros.workflow.approval.lifecycle.ApprovalCaseService.Submission("case", 1));
    when(commands.start(any(), any(), any(), any(), any(), any()))
        .thenReturn(Try.failure(new com.ouroboros.exception.BusinessException("invalid definition")));
    assertTrue(service.start("flow-a", "actor", Map.of(),
        Map.of("businessRecordType", "sales.Order", "businessRecordId", "42"), Map.of()).isFailure());
    verify(cases).markStartFailed("case", 1, "invalid definition");
    verify(cases, never()).bindExecution(any(), anyInt(), any());
  }

  @Test
  void incompleteBusinessContextCannotReachTheLocalEngine() {
    var commands = mock(FlowInstanceCommandService.class);
    var service = new FlowableFlowInstanceService(commands, mock(FlowInstanceQueryService.class));
    org.junit.jupiter.api.Assertions.assertThrows(com.ouroboros.exception.BusinessException.class,
        () -> service.start("flow-a", "actor", Map.of(), Map.of("businessRecordId", "42"), Map.of()));
    org.mockito.Mockito.verifyNoInteractions(commands);
  }

  @Test
  void startStartsProcessWithStarterVariablesAndCompletesInitialTask() {
    TaskService taskService = mock(TaskService.class);
    RuntimeService runtimeService = mock(RuntimeService.class);
    RepositoryService repositoryService = buildActiveRepositoryService(v2FlowJson());
    FlowFormService flowFormService = mock(FlowFormService.class);
    FlowDefinitionService flowDefinitionService = mock(FlowDefinitionService.class);

    FlowableFlowInstanceService service = new FlowableFlowInstanceService(taskService, runtimeService, repositoryService, null, flowDefinitionService, Collections.singletonList(flowFormService), null);

    ProcessInstance processInstance = mock(ProcessInstance.class);
    TaskQuery taskQuery = mock(TaskQuery.class);
    Task initialTask = mock(Task.class);
    Map<String, Object> formData = new HashMap<String, Object>();
    formData.put("amount", 1200);

    when(flowDefinitionService.getUserSelectCandidateApprovalNodes("flow-a")).thenReturn(Collections.emptyList());
    when(flowFormService.getType()).thenReturn("default");
    when(runtimeService.startProcessInstanceByKey(eq("flow-a"), any(Map.class))).thenReturn(processInstance);
    when(processInstance.getId()).thenReturn("instance-1");
    when(flowFormService.submitInitialForm(eq("instance-1"), eq("purchase-form"), eq("starter-1"), anyList(), eq(formData)))
        .thenReturn(Try.success("form-1"));
    when(taskService.createTaskQuery()).thenReturn(taskQuery);
    when(taskQuery.processInstanceId("instance-1")).thenReturn(taskQuery);
    when(taskQuery.singleResult()).thenReturn(initialTask);
    when(initialTask.getId()).thenReturn("task-1");

    Try<String> result = service.start("flow-a", "starter-1", Collections.emptyMap(), Collections.emptyMap(), formData, CompletableFuture.completedFuture(null));

    assertTrue(result.isSuccess());
    assertEquals("instance-1", result.get());

    ArgumentCaptor<Map<String, Object>> variablesCaptor = ArgumentCaptor.forClass(Map.class);
    verify(runtimeService).startProcessInstanceByKey(eq("flow-a"), variablesCaptor.capture());
    Map<String, Object> variables = variablesCaptor.getValue();
    assertEquals("starter-1", variables.get(Constants.Variable.PROCESS_STARTER));
    assertEquals("flow-a", variables.get(Constants.Variable.PROCESS_FLOW_KEY));
    assertEquals(Collections.emptyMap(), variables.get(Constants.Variable.USER_SELECTED_ASSIGNEES));
    assertEquals("default", variables.get(Constants.TASK_FORM_TYPE_KEY));
    assertEquals(1200, variables.get("amount"));
    assertEquals(Collections.singletonMap("amount", 1200), variables.get(Constants.CANDIDATE_FORM_DATA_KEY));
    ArgumentCaptor<Map<String, Object>> conditionContextCaptor = ArgumentCaptor.forClass(Map.class);
    verify(flowDefinitionService).getUserSelectCandidateApprovalNodes(eq("flow-a"), conditionContextCaptor.capture());
    assertEquals(1200, conditionContextCaptor.getValue().get("amount"));
    verify(runtimeService).setVariable("instance-1", Constants.TASK_FORM_ID_KEY, "form-1");
    verify(taskService).setAssignee("task-1", "starter-1");
    verify(taskService).setVariableLocal("task-1", Constants.Variable.TASK_APPROVED, true);
    verify(taskService).setVariableLocal("task-1", Constants.TASK_FORM_DATA_KEY, formData);
    verify(taskService).complete("task-1");
  }

  @Test
  void startShouldStoreInitialFormDataInCandidateFormDataWithoutOverwritingVariables() {
    TaskService taskService = mock(TaskService.class);
    RuntimeService runtimeService = mock(RuntimeService.class);
    RepositoryService repositoryService = buildActiveRepositoryService(v2FlowJson());
    FlowFormService flowFormService = mock(FlowFormService.class);
    FlowDefinitionService flowDefinitionService = mock(FlowDefinitionService.class);

    FlowableFlowInstanceService service = new FlowableFlowInstanceService(taskService, runtimeService, repositoryService, null, flowDefinitionService, Collections.singletonList(flowFormService), null);

    ProcessInstance processInstance = mock(ProcessInstance.class);
    Map<String, Object> formData = new HashMap<String, Object>();
    formData.put("managerId", "form-user");
    formData.put(Constants.Variable.PROCESS_STARTER, "fake-starter");
    formData.put(Constants.CANDIDATE_FORM_DATA_KEY, Collections.singletonMap("bad", true));
    Map<String, Object> instanceVariables = new HashMap<String, Object>();
    instanceVariables.put("managerId", "explicit-user");
    instanceVariables.put(Constants.Variable.PROCESS_STARTER, "explicit-fake-starter");
    instanceVariables.put(Constants.TASK_FORM_TYPE_KEY, "fake-form-type");

    when(flowDefinitionService.getUserSelectCandidateApprovalNodes("flow-a")).thenReturn(Collections.emptyList());
    when(flowFormService.getType()).thenReturn("default");
    when(runtimeService.startProcessInstanceByKey(eq("flow-a"), any(Map.class))).thenReturn(processInstance);
    when(processInstance.getId()).thenReturn("instance-1");
    when(flowFormService.submitInitialForm(eq("instance-1"), eq("purchase-form"), eq("starter-1"), anyList(), eq(formData)))
        .thenReturn(Try.success("form-1"));

    Try<String> result = service.start("flow-a", "starter-1", Collections.emptyMap(), instanceVariables, formData, CompletableFuture.completedFuture(null));

    assertTrue(result.isSuccess());
    ArgumentCaptor<Map<String, Object>> variablesCaptor = ArgumentCaptor.forClass(Map.class);
    verify(runtimeService).startProcessInstanceByKey(eq("flow-a"), variablesCaptor.capture());
    Map<String, Object> variables = variablesCaptor.getValue();
    assertEquals("starter-1", variables.get(Constants.Variable.PROCESS_STARTER));
    assertEquals("default", variables.get(Constants.TASK_FORM_TYPE_KEY));
    assertEquals("explicit-user", variables.get("managerId"));
    assertEquals(formData, variables.get(Constants.CANDIDATE_FORM_DATA_KEY));
  }

  @Test
  void startFailsWhenRequiredUserSelectAssigneeMissing() {
    TaskService taskService = mock(TaskService.class);
    RuntimeService runtimeService = mock(RuntimeService.class);
    RepositoryService repositoryService = buildActiveRepositoryService(v2FlowJson());
    FlowDefinitionService flowDefinitionService = mock(FlowDefinitionService.class);

    FlowableFlowInstanceService service = new FlowableFlowInstanceService(taskService, runtimeService, repositoryService, null, flowDefinitionService, Collections.emptyList(), null);

    SingleApprovalNode approvalNode = new SingleApprovalNode();
    approvalNode.setId("approve-a");
    approvalNode.setName("经理审批");
    when(flowDefinitionService.getUserSelectCandidateApprovalNodes("flow-a")).thenReturn(Collections.singletonList(approvalNode));

    Try<String> result = service.start("flow-a", "starter-1", Collections.emptyMap(), Collections.emptyMap(), null, CompletableFuture.completedFuture(null));

    assertTrue(result.isFailure());
    assertEquals("节点：【经理审批】 未选择人员", result.getCause().getMessage());
    verify(runtimeService, never()).startProcessInstanceByKey(eq("flow-a"), any(Map.class));
  }

  @Test
  void startMarksTransactionRollbackOnlyWhenInitialFormSubmissionFails() {
    TaskService taskService = mock(TaskService.class);
    RuntimeService runtimeService = mock(RuntimeService.class);
    RepositoryService repositoryService = buildActiveRepositoryService(v2FlowJson());
    FlowFormService flowFormService = mock(FlowFormService.class);
    FlowDefinitionService flowDefinitionService = mock(FlowDefinitionService.class);
    RecordingTransactionManager transactionManager = new RecordingTransactionManager();

    FlowableFlowInstanceService target = new FlowableFlowInstanceService(taskService, runtimeService, repositoryService, null, flowDefinitionService, Collections.singletonList(flowFormService), null);
    FlowInstanceService service = transactionalProxy(target, transactionManager);

    ProcessInstance processInstance = mock(ProcessInstance.class);
    RuntimeException failure = new RuntimeException("submit failed");

    when(flowDefinitionService.getUserSelectCandidateApprovalNodes("flow-a")).thenReturn(Collections.emptyList());
    when(flowFormService.getType()).thenReturn("default");
    when(runtimeService.startProcessInstanceByKey(eq("flow-a"), any(Map.class))).thenReturn(processInstance);
    when(processInstance.getId()).thenReturn("instance-1");
    when(flowFormService.submitInitialForm(eq("instance-1"), eq("purchase-form"), eq("starter-1"), anyList(), eq(Collections.emptyMap())))
        .thenReturn(Try.failure(failure));

    Try<String> result = service.start("flow-a", "starter-1", Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(),
        CompletableFuture.completedFuture(null));

    assertTrue(result.isFailure());
    assertEquals("submit failed", result.getCause().getMessage());
    assertEquals(0, transactionManager.getCommitCount());
    assertEquals(1, transactionManager.getRollbackCount());
    verify(runtimeService, never()).setVariable(anyString(), eq(Constants.TASK_FORM_ID_KEY), any());
  }

  @Test
  void getInitialFormDelegatesToMatchingFlowFormService() {
    TaskService taskService = mock(TaskService.class);
    RuntimeService runtimeService = mock(RuntimeService.class);
    RepositoryService repositoryService = buildActiveRepositoryService(v2FlowJson());
    FlowFormService flowFormService = mock(FlowFormService.class);

    FlowableFlowInstanceService service = new FlowableFlowInstanceService(taskService, runtimeService, repositoryService, null, null, Collections.singletonList(flowFormService), null);

    SchemaNode schemaNode = new SchemaNode();
    schemaNode.setType("form");
    when(flowFormService.getType()).thenReturn("default");
    when(flowFormService.getInitialFormSchema(eq("purchase-form"), anyList())).thenReturn(Try.success(schemaNode));

    Try<SchemaNode> result = service.getInitialForm("flow-a");

    assertTrue(result.isSuccess());
    assertEquals(schemaNode, result.get());
  }

  @Test
  void getInitialFormUsesStartNodePermissionsEvenWhenStartNodeIsNotFirst() {
    TaskService taskService = mock(TaskService.class);
    RuntimeService runtimeService = mock(RuntimeService.class);
    RepositoryService repositoryService = buildActiveRepositoryService(v2FlowJsonWithStartNodeNotFirst());
    FlowFormService flowFormService = mock(FlowFormService.class);

    FlowableFlowInstanceService service = new FlowableFlowInstanceService(taskService, runtimeService, repositoryService, null, null, Collections.singletonList(flowFormService), null);

    SchemaNode schemaNode = new SchemaNode();
    schemaNode.setType("form");
    when(flowFormService.getType()).thenReturn("default");
    when(flowFormService.getInitialFormSchema(eq("purchase-form"), anyList())).thenReturn(Try.success(schemaNode));

    Try<SchemaNode> result = service.getInitialForm("flow-a");

    assertTrue(result.isSuccess());
    ArgumentCaptor<List> permissionsCaptor = ArgumentCaptor.forClass(List.class);
    verify(flowFormService).getInitialFormSchema(eq("purchase-form"), permissionsCaptor.capture());
    assertEquals(Collections.singletonList("start-field"), permissionNames(permissionsCaptor.getValue()));
  }

  @Test
  void getInitialFormIgnoresStartNodePermissionsForBusinessModelForm() {
    TaskService taskService = mock(TaskService.class);
    RuntimeService runtimeService = mock(RuntimeService.class);
    RepositoryService repositoryService = buildActiveRepositoryService(businessModelFlowJsonWithStartPermission());
    FlowFormService flowFormService = mock(FlowFormService.class);

    FlowableFlowInstanceService service = new FlowableFlowInstanceService(taskService, runtimeService, repositoryService, null, null, Collections.singletonList(flowFormService), null);

    SchemaNode schemaNode = new SchemaNode();
    schemaNode.setType("form");
    when(flowFormService.getType()).thenReturn("business-model");
    when(flowFormService.getInitialFormSchema(eq("purchase-form"), anyList())).thenReturn(Try.success(schemaNode));

    Try<SchemaNode> result = service.getInitialForm("flow-a");

    assertTrue(result.isSuccess());
    ArgumentCaptor<List> permissionsCaptor = ArgumentCaptor.forClass(List.class);
    verify(flowFormService).getInitialFormSchema(eq("purchase-form"), permissionsCaptor.capture());
    assertTrue(permissionsCaptor.getValue().isEmpty());
  }

  @Test
  void startUsesStartNodePermissionsEvenWhenStartNodeIsNotFirst() {
    TaskService taskService = mock(TaskService.class);
    RuntimeService runtimeService = mock(RuntimeService.class);
    RepositoryService repositoryService = buildActiveRepositoryService(v2FlowJsonWithStartNodeNotFirst());
    FlowFormService flowFormService = mock(FlowFormService.class);
    FlowDefinitionService flowDefinitionService = mock(FlowDefinitionService.class);

    FlowableFlowInstanceService service = new FlowableFlowInstanceService(taskService, runtimeService, repositoryService, null, flowDefinitionService, Collections.singletonList(flowFormService), null);

    ProcessInstance processInstance = mock(ProcessInstance.class);
    when(flowDefinitionService.getUserSelectCandidateApprovalNodes("flow-a")).thenReturn(Collections.emptyList());
    when(flowFormService.getType()).thenReturn("default");
    when(runtimeService.startProcessInstanceByKey(eq("flow-a"), any(Map.class))).thenReturn(processInstance);
    when(processInstance.getId()).thenReturn("instance-1");
    when(flowFormService.submitInitialForm(eq("instance-1"), eq("purchase-form"), eq("starter-1"), anyList(), eq(Collections.emptyMap())))
        .thenReturn(Try.success("form-1"));

    Try<String> result = service.start("flow-a", "starter-1", Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(),
        CompletableFuture.completedFuture(null));

    assertTrue(result.isSuccess());
    ArgumentCaptor<List> permissionsCaptor = ArgumentCaptor.forClass(List.class);
    verify(flowFormService).submitInitialForm(eq("instance-1"), eq("purchase-form"), eq("starter-1"), permissionsCaptor.capture(),
        eq(Collections.emptyMap()));
    assertEquals(Collections.singletonList("start-field"), permissionNames(permissionsCaptor.getValue()));
  }

  @Test
  void getInstancesMapsLatestTaskAndStarter() {
    TaskService taskService = mock(TaskService.class);
    RuntimeService runtimeService = mock(RuntimeService.class);
    RepositoryService repositoryService = mock(RepositoryService.class);
    HistoryService historyService = mock(HistoryService.class);

    FlowableFlowInstanceService service = new FlowableFlowInstanceService(taskService, runtimeService, repositoryService, historyService, null, Collections.emptyList(), null);

    HistoricProcessInstanceQuery processQuery = mock(HistoricProcessInstanceQuery.class);
    HistoricProcessInstance historicProcessInstance = mock(HistoricProcessInstance.class);
    HistoricTaskInstanceQuery taskQuery = mock(HistoricTaskInstanceQuery.class);
    HistoricTaskInstance latestTask = mock(HistoricTaskInstance.class);
    HistoricVariableInstanceQuery variableQuery = mock(HistoricVariableInstanceQuery.class);
    HistoricVariableInstance starterVariable = mock(HistoricVariableInstance.class);
    ProcessInstanceQuery runtimeQuery = mock(ProcessInstanceQuery.class);
    ProcessInstanceQuery activeRuntimeQuery = mock(ProcessInstanceQuery.class);
    ProcessInstance runtimeInstance = mock(ProcessInstance.class);

    when(historyService.createHistoricProcessInstanceQuery()).thenReturn(processQuery);
    when(processQuery.processDefinitionKey("flow-a")).thenReturn(processQuery);
    when(processQuery.unfinished()).thenReturn(processQuery);
    when(processQuery.orderByProcessInstanceStartTime()).thenReturn(processQuery);
    when(processQuery.desc()).thenReturn(processQuery);
    when(processQuery.list()).thenReturn(Collections.singletonList(historicProcessInstance));
    when(processQuery.listPage(eq(0), anyInt())).thenReturn(Collections.singletonList(historicProcessInstance));

    when(historicProcessInstance.getId()).thenReturn("instance-1");
    when(historicProcessInstance.getProcessDefinitionKey()).thenReturn("flow-a");
    when(historicProcessInstance.getStartTime()).thenReturn(dateOf(2026, Month.MARCH, 18, 9, 0));
    when(historicProcessInstance.getEndTime()).thenReturn(null);

    when(runtimeService.createProcessInstanceQuery()).thenReturn(runtimeQuery);
    when(runtimeQuery.processDefinitionKey("flow-a")).thenReturn(runtimeQuery);
    when(runtimeQuery.active()).thenReturn(activeRuntimeQuery);
    when(activeRuntimeQuery.processInstanceIds(any())).thenReturn(activeRuntimeQuery);
    when(activeRuntimeQuery.list()).thenReturn(Collections.singletonList(runtimeInstance));
    when(runtimeInstance.getId()).thenReturn("instance-1");

    when(historyService.createHistoricTaskInstanceQuery()).thenReturn(taskQuery);
    when(taskQuery.processInstanceId("instance-1")).thenReturn(taskQuery);
    when(taskQuery.orderByHistoricTaskInstanceStartTime()).thenReturn(taskQuery);
    when(taskQuery.desc()).thenReturn(taskQuery);
    when(taskQuery.listPage(0, 1)).thenReturn(Collections.singletonList(latestTask));
    when(latestTask.getId()).thenReturn("task-1");
    when(latestTask.getName()).thenReturn("经理审批");

    when(historyService.createHistoricVariableInstanceQuery()).thenReturn(variableQuery);
    when(variableQuery.processInstanceId("instance-1")).thenReturn(variableQuery);
    when(variableQuery.variableName(anyString())).thenReturn(variableQuery);
    when(variableQuery.singleResult()).thenReturn(starterVariable);
    when(starterVariable.getValue()).thenReturn("starter-1");

    List<FlowInstanceDetail> details = service.getInstances("flow-a", "running", 0, 20);

    assertEquals(1, details.size());
    FlowInstanceDetail detail = details.get(0);
    assertEquals("instance-1", detail.getInstanceId());
    assertEquals("flow-a", detail.getFlowKey());
    assertEquals("starter-1", detail.getStarter());
    assertEquals("running", detail.getStatus());
    assertEquals("task-1", detail.getTaskId());
    assertEquals("经理审批", detail.getTaskName());
    assertEquals(LocalDateTime.of(2026, 3, 18, 9, 0), detail.getStartTime());
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
  void processInfoReadsKeepBuiltInTaskPermissionsAndPresentationCapabilities(boolean directRead) {
    TaskService taskService = mock(TaskService.class);
    RuntimeService runtimeService = mock(RuntimeService.class);
    RepositoryService repositoryService = mock(RepositoryService.class);
    HistoryService historyService = mock(HistoryService.class);

    FlowableFlowInstanceService service = new FlowableFlowInstanceService(taskService, runtimeService, repositoryService, historyService, null, Collections.emptyList(), null);

    HistoricVariableInstanceQuery approvalUrlQuery = mock(HistoricVariableInstanceQuery.class);
    HistoricVariableInstance approvalUrlVariable = mock(HistoricVariableInstance.class);
    HistoricProcessInstanceQuery processQuery = mock(HistoricProcessInstanceQuery.class);
    HistoricProcessInstance historicProcessInstance = mock(HistoricProcessInstance.class);
    HistoricVariableInstanceQuery starterQuery = mock(HistoricVariableInstanceQuery.class);
    HistoricVariableInstance starterVariable = mock(HistoricVariableInstance.class);
    HistoricVariableInstanceQuery outcomeQuery = mock(HistoricVariableInstanceQuery.class);
    ProcessInstanceQuery activeRuntimeQuery = mock(ProcessInstanceQuery.class);
    ProcessInstanceQuery suspendedRuntimeQuery = mock(ProcessInstanceQuery.class);
    ProcessInstanceQuery instanceRuntimeQuery = mock(ProcessInstanceQuery.class);
    ProcessInstance runtimeInstance = mock(ProcessInstance.class);
    TaskQuery activeTaskQuery = mock(TaskQuery.class);
    Task activeTask = mock(Task.class);

    if (directRead) {
      when(historyService.createHistoricVariableInstanceQuery()).thenReturn(starterQuery, outcomeQuery);
    } else {
      when(historyService.createHistoricVariableInstanceQuery()).thenReturn(approvalUrlQuery, starterQuery, outcomeQuery);
    }
    when(approvalUrlQuery.variableValueEquals("approvalUrl", "/demo/customer/1/approval")).thenReturn(approvalUrlQuery);
    when(approvalUrlQuery.list()).thenReturn(Collections.singletonList(approvalUrlVariable));
    when(approvalUrlVariable.getProcessInstanceId()).thenReturn("instance-1");

    when(historyService.createHistoricProcessInstanceQuery()).thenReturn(processQuery);
    when(processQuery.processInstanceId("instance-1")).thenReturn(processQuery);
    when(processQuery.singleResult()).thenReturn(historicProcessInstance);
    when(historicProcessInstance.getId()).thenReturn("instance-1");
    when(historicProcessInstance.getProcessDefinitionKey()).thenReturn("flow-a");
    when(historicProcessInstance.getProcessDefinitionId()).thenReturn("process-definition-1");
    when(historicProcessInstance.getStartTime()).thenReturn(dateOf(2026, Month.MARCH, 18, 9, 0));
    when(historicProcessInstance.getEndTime()).thenReturn(null);

    when(runtimeService.createProcessInstanceQuery()).thenReturn(activeRuntimeQuery, suspendedRuntimeQuery, instanceRuntimeQuery);
    when(activeRuntimeQuery.processInstanceIds(any())).thenReturn(activeRuntimeQuery);
    when(activeRuntimeQuery.active()).thenReturn(activeRuntimeQuery);
    when(activeRuntimeQuery.list()).thenReturn(Collections.singletonList(runtimeInstance));
    when(suspendedRuntimeQuery.processInstanceIds(any())).thenReturn(suspendedRuntimeQuery);
    when(suspendedRuntimeQuery.suspended()).thenReturn(suspendedRuntimeQuery);
    when(suspendedRuntimeQuery.list()).thenReturn(Collections.emptyList());
    when(runtimeInstance.getId()).thenReturn("instance-1");
    when(instanceRuntimeQuery.processInstanceId("instance-1")).thenReturn(instanceRuntimeQuery);
    when(instanceRuntimeQuery.singleResult()).thenReturn(runtimeInstance);

    when(starterQuery.processInstanceId("instance-1")).thenReturn(starterQuery);
    when(starterQuery.variableName(Constants.Variable.PROCESS_STARTER)).thenReturn(starterQuery);
    when(starterQuery.singleResult()).thenReturn(starterVariable);
    when(starterVariable.getValue()).thenReturn("starter-1");
    when(outcomeQuery.processInstanceId("instance-1")).thenReturn(outcomeQuery);
    when(outcomeQuery.variableName(Constants.Variable.PROCESS_OUTCOME)).thenReturn(outcomeQuery);
    when(outcomeQuery.singleResult()).thenReturn(null);

    when(taskService.createTaskQuery()).thenReturn(activeTaskQuery);
    when(activeTaskQuery.processInstanceId("instance-1")).thenReturn(activeTaskQuery);
    when(activeTaskQuery.active()).thenReturn(activeTaskQuery);
    when(activeTaskQuery.orderByTaskCreateTime()).thenReturn(activeTaskQuery);
    when(activeTaskQuery.asc()).thenReturn(activeTaskQuery);
    when(activeTaskQuery.list()).thenReturn(Collections.singletonList(activeTask));
    when(activeTask.getId()).thenReturn("task-1");
    when(activeTask.getName()).thenReturn("经理审批");
    when(activeTask.getTaskDefinitionKey()).thenReturn("approve-a");
    when(activeTask.getAssignee()).thenReturn("approver-1");
    when(activeTask.getCreateTime()).thenReturn(dateOf(2026, Month.MARCH, 18, 9, 5));
    when(repositoryService.getBpmnModel("process-definition-1")).thenReturn(buildBpmnModel(v2FlowJson()));

    Map<String, FlowProcessInfo> result;
    try (var discovery = org.mockito.Mockito.mockStatic(SPI.class, org.mockito.Mockito.withSettings()
        .defaultAnswer(org.mockito.Mockito.CALLS_REAL_METHODS).mockMaker(org.mockito.MockMakers.INLINE))) {
      discovery.when(() -> SPI.getCachedServiceStream(CurrentUserSupplier.class))
          .thenAnswer(ignored -> java.util.stream.Stream.of((CurrentUserSupplier) () -> "approver-1"));
      result = directRead
          ? Map.of("/demo/customer/1/approval", service.getProcessInfo("instance-1").get())
          : service.getProcessInfoByApprovalUrls(
              "flow-a", Collections.singletonList("/demo/customer/1/approval")).get();
    }

    FlowProcessInfo info = result.get("/demo/customer/1/approval");
    assertEquals("instance-1", info.getInstanceId());
    assertEquals("flow-a", info.getFlowKey());
    assertEquals("running", info.getStatus());
    assertEquals("starter-1", info.getStarter());
    assertEquals(1, info.getTasks().size());
    assertEquals("task-1", info.getTasks().get(0).getTaskId());
    assertEquals("审批节点", info.getTasks().get(0).getNodeType());
    assertEquals(LocalDateTime.of(2026, 3, 18, 9, 5), info.getTasks().get(0).getStartTime());
    assertEquals(Boolean.TRUE, info.isProcessable());
    assertEquals("task-1", info.getProcessableTaskId());
    assertTrue(info.isDiagramAvailable());
    assertTrue(info.isTimelineAvailable());
    assertTrue(info.isCancellable());
  }

  @Test
  void getInstancesSeparatesRunningAndSuspendedInstancesByRuntimeState() {
    TaskService taskService = mock(TaskService.class);
    RuntimeService runtimeService = mock(RuntimeService.class);
    RepositoryService repositoryService = mock(RepositoryService.class);
    HistoryService historyService = mock(HistoryService.class);

    FlowableFlowInstanceService service = new FlowableFlowInstanceService(taskService, runtimeService, repositoryService, historyService, null, Collections.emptyList(), null);

    HistoricProcessInstanceQuery processQuery = mock(HistoricProcessInstanceQuery.class);
    HistoricProcessInstance activeInstance = mock(HistoricProcessInstance.class);
    HistoricProcessInstance suspendedInstance = mock(HistoricProcessInstance.class);
    HistoricTaskInstanceQuery taskQuery = mock(HistoricTaskInstanceQuery.class);
    HistoricVariableInstanceQuery variableQuery = mock(HistoricVariableInstanceQuery.class);
    ProcessInstanceQuery runtimeQuery = mock(ProcessInstanceQuery.class);
    ProcessInstanceQuery activeRuntimeQuery = mock(ProcessInstanceQuery.class);
    ProcessInstanceQuery suspendedRuntimeQuery = mock(ProcessInstanceQuery.class);
    ProcessInstance activeRuntime = mock(ProcessInstance.class);
    ProcessInstance suspendedRuntime = mock(ProcessInstance.class);

    when(historyService.createHistoricProcessInstanceQuery()).thenReturn(processQuery);
    when(processQuery.processDefinitionKey("flow-a")).thenReturn(processQuery);
    when(processQuery.unfinished()).thenReturn(processQuery);
    when(processQuery.orderByProcessInstanceStartTime()).thenReturn(processQuery);
    when(processQuery.desc()).thenReturn(processQuery);
    when(processQuery.list()).thenReturn(Arrays.asList(activeInstance, suspendedInstance));
    when(processQuery.listPage(eq(0), anyInt())).thenReturn(Arrays.asList(activeInstance, suspendedInstance));

    when(activeInstance.getId()).thenReturn("instance-active");
    when(activeInstance.getProcessDefinitionKey()).thenReturn("flow-a");
    when(activeInstance.getStartTime()).thenReturn(dateOf(2026, Month.MARCH, 18, 9, 0));
    when(suspendedInstance.getId()).thenReturn("instance-suspended");
    when(suspendedInstance.getProcessDefinitionKey()).thenReturn("flow-a");
    when(suspendedInstance.getStartTime()).thenReturn(dateOf(2026, Month.MARCH, 18, 10, 0));

    when(historyService.createHistoricTaskInstanceQuery()).thenReturn(taskQuery);
    when(taskQuery.processInstanceId(anyString())).thenReturn(taskQuery);
    when(taskQuery.orderByHistoricTaskInstanceStartTime()).thenReturn(taskQuery);
    when(taskQuery.desc()).thenReturn(taskQuery);
    when(taskQuery.listPage(0, 1)).thenReturn(Collections.emptyList());

    when(historyService.createHistoricVariableInstanceQuery()).thenReturn(variableQuery);
    when(variableQuery.processInstanceId(anyString())).thenReturn(variableQuery);
    when(variableQuery.variableName(anyString())).thenReturn(variableQuery);
    when(variableQuery.singleResult()).thenReturn(null);

    when(runtimeService.createProcessInstanceQuery()).thenReturn(runtimeQuery);
    when(runtimeQuery.processDefinitionKey("flow-a")).thenReturn(runtimeQuery);
    when(runtimeQuery.active()).thenReturn(activeRuntimeQuery);
    when(runtimeQuery.suspended()).thenReturn(suspendedRuntimeQuery);
    when(activeRuntimeQuery.processInstanceIds(any())).thenReturn(activeRuntimeQuery);
    when(suspendedRuntimeQuery.processInstanceIds(any())).thenReturn(suspendedRuntimeQuery);
    when(activeRuntimeQuery.list()).thenReturn(Collections.singletonList(activeRuntime));
    when(suspendedRuntimeQuery.list()).thenReturn(Collections.singletonList(suspendedRuntime));
    when(activeRuntime.getId()).thenReturn("instance-active");
    when(suspendedRuntime.getId()).thenReturn("instance-suspended");

    List<FlowInstanceDetail> running = service.getInstances("flow-a", "running", 0, 20);
    List<FlowInstanceDetail> suspended = service.getInstances("flow-a", "suspended", 0, 20);

    assertEquals(1, running.size());
    assertEquals("instance-active", running.get(0).getInstanceId());
    assertEquals("running", running.get(0).getStatus());
    assertEquals(1, suspended.size());
    assertEquals("instance-suspended", suspended.get(0).getInstanceId());
    assertEquals("suspended", suspended.get(0).getStatus());
  }

  @Test
  void getInstancesWithoutStatusStillMarksSuspendedInstancesAsSuspended() {
    TaskService taskService = mock(TaskService.class);
    RuntimeService runtimeService = mock(RuntimeService.class);
    RepositoryService repositoryService = mock(RepositoryService.class);
    HistoryService historyService = mock(HistoryService.class);

    FlowableFlowInstanceService service = new FlowableFlowInstanceService(taskService, runtimeService, repositoryService, historyService, null, Collections.emptyList(), null);

    HistoricProcessInstanceQuery processQuery = mock(HistoricProcessInstanceQuery.class);
    HistoricProcessInstance activeInstance = mock(HistoricProcessInstance.class);
    HistoricProcessInstance suspendedInstance = mock(HistoricProcessInstance.class);
    HistoricTaskInstanceQuery taskQuery = mock(HistoricTaskInstanceQuery.class);
    HistoricVariableInstanceQuery variableQuery = mock(HistoricVariableInstanceQuery.class);
    ProcessInstanceQuery runtimeQuery = mock(ProcessInstanceQuery.class);
    ProcessInstanceQuery activeRuntimeQuery = mock(ProcessInstanceQuery.class);
    ProcessInstanceQuery suspendedRuntimeQuery = mock(ProcessInstanceQuery.class);
    ProcessInstance activeRuntime = mock(ProcessInstance.class);
    ProcessInstance suspendedRuntime = mock(ProcessInstance.class);

    when(historyService.createHistoricProcessInstanceQuery()).thenReturn(processQuery);
    when(processQuery.processDefinitionKey("flow-a")).thenReturn(processQuery);
    when(processQuery.orderByProcessInstanceStartTime()).thenReturn(processQuery);
    when(processQuery.desc()).thenReturn(processQuery);
    when(processQuery.listPage(0, 20)).thenReturn(Arrays.asList(activeInstance, suspendedInstance));

    when(activeInstance.getId()).thenReturn("instance-active");
    when(activeInstance.getProcessDefinitionKey()).thenReturn("flow-a");
    when(activeInstance.getStartTime()).thenReturn(dateOf(2026, Month.MARCH, 18, 9, 0));
    when(activeInstance.getEndTime()).thenReturn(null);
    when(suspendedInstance.getId()).thenReturn("instance-suspended");
    when(suspendedInstance.getProcessDefinitionKey()).thenReturn("flow-a");
    when(suspendedInstance.getStartTime()).thenReturn(dateOf(2026, Month.MARCH, 18, 10, 0));
    when(suspendedInstance.getEndTime()).thenReturn(null);

    when(historyService.createHistoricTaskInstanceQuery()).thenReturn(taskQuery);
    when(taskQuery.processInstanceId(anyString())).thenReturn(taskQuery);
    when(taskQuery.orderByHistoricTaskInstanceStartTime()).thenReturn(taskQuery);
    when(taskQuery.desc()).thenReturn(taskQuery);
    when(taskQuery.listPage(0, 1)).thenReturn(Collections.emptyList());

    when(historyService.createHistoricVariableInstanceQuery()).thenReturn(variableQuery);
    when(variableQuery.processInstanceId(anyString())).thenReturn(variableQuery);
    when(variableQuery.variableName(anyString())).thenReturn(variableQuery);
    when(variableQuery.singleResult()).thenReturn(null);

    when(runtimeService.createProcessInstanceQuery()).thenReturn(runtimeQuery);
    when(runtimeQuery.processInstanceIds(any())).thenReturn(runtimeQuery);
    when(runtimeQuery.active()).thenReturn(activeRuntimeQuery);
    when(runtimeQuery.suspended()).thenReturn(suspendedRuntimeQuery);
    when(activeRuntimeQuery.list()).thenReturn(Collections.singletonList(activeRuntime));
    when(suspendedRuntimeQuery.list()).thenReturn(Collections.singletonList(suspendedRuntime));
    when(activeRuntime.getId()).thenReturn("instance-active");
    when(suspendedRuntime.getId()).thenReturn("instance-suspended");

    List<FlowInstanceDetail> details = service.getInstances("flow-a", null, 0, 20);

    assertEquals(2, details.size());
    assertEquals("running", details.get(0).getStatus());
    assertEquals("suspended", details.get(1).getStatus());
  }

  @Test
  void getInstancesPagesRuntimeStatusesWithoutLoadingAllHistoricInstances() {
    TaskService taskService = mock(TaskService.class);
    RuntimeService runtimeService = mock(RuntimeService.class);
    RepositoryService repositoryService = mock(RepositoryService.class);
    HistoryService historyService = mock(HistoryService.class);

    FlowableFlowInstanceService service = new FlowableFlowInstanceService(taskService, runtimeService, repositoryService, historyService, null, Collections.emptyList(), null);

    HistoricProcessInstanceQuery processQuery = mock(HistoricProcessInstanceQuery.class);
    HistoricProcessInstance firstRunning = mock(HistoricProcessInstance.class);
    HistoricProcessInstance suspendedInstance = mock(HistoricProcessInstance.class);
    HistoricProcessInstance secondRunning = mock(HistoricProcessInstance.class);
    HistoricTaskInstanceQuery taskQuery = mock(HistoricTaskInstanceQuery.class);
    HistoricVariableInstanceQuery variableQuery = mock(HistoricVariableInstanceQuery.class);
    ProcessInstanceQuery runtimeQuery = mock(ProcessInstanceQuery.class);
    ProcessInstanceQuery activeRuntimeQuery = mock(ProcessInstanceQuery.class);
    ProcessInstance firstRunningRuntime = mock(ProcessInstance.class);
    ProcessInstance secondRunningRuntime = mock(ProcessInstance.class);

    when(historyService.createHistoricProcessInstanceQuery()).thenReturn(processQuery);
    when(processQuery.processDefinitionKey("flow-a")).thenReturn(processQuery);
    when(processQuery.unfinished()).thenReturn(processQuery);
    when(processQuery.orderByProcessInstanceStartTime()).thenReturn(processQuery);
    when(processQuery.desc()).thenReturn(processQuery);
    when(processQuery.list()).thenThrow(new AssertionError("should page runtime-state history query instead of loading all unfinished instances"));
    when(processQuery.listPage(eq(0), anyInt())).thenReturn(Arrays.asList(firstRunning, suspendedInstance, secondRunning));

    when(firstRunning.getId()).thenReturn("instance-running-1");
    when(firstRunning.getProcessDefinitionKey()).thenReturn("flow-a");
    when(firstRunning.getStartTime()).thenReturn(dateOf(2026, Month.MARCH, 18, 9, 0));
    when(suspendedInstance.getId()).thenReturn("instance-suspended");
    when(secondRunning.getId()).thenReturn("instance-running-2");
    when(secondRunning.getProcessDefinitionKey()).thenReturn("flow-a");
    when(secondRunning.getStartTime()).thenReturn(dateOf(2026, Month.MARCH, 18, 11, 0));

    when(historyService.createHistoricTaskInstanceQuery()).thenReturn(taskQuery);
    when(taskQuery.processInstanceId(anyString())).thenReturn(taskQuery);
    when(taskQuery.orderByHistoricTaskInstanceStartTime()).thenReturn(taskQuery);
    when(taskQuery.desc()).thenReturn(taskQuery);
    when(taskQuery.listPage(0, 1)).thenReturn(Collections.emptyList());

    when(historyService.createHistoricVariableInstanceQuery()).thenReturn(variableQuery);
    when(variableQuery.processInstanceId(anyString())).thenReturn(variableQuery);
    when(variableQuery.variableName(anyString())).thenReturn(variableQuery);
    when(variableQuery.singleResult()).thenReturn(null);

    when(runtimeService.createProcessInstanceQuery()).thenReturn(runtimeQuery);
    when(runtimeQuery.processDefinitionKey("flow-a")).thenReturn(runtimeQuery);
    when(runtimeQuery.active()).thenReturn(activeRuntimeQuery);
    when(activeRuntimeQuery.processInstanceIds(any())).thenReturn(activeRuntimeQuery);
    when(activeRuntimeQuery.list()).thenReturn(Arrays.asList(firstRunningRuntime, secondRunningRuntime));
    when(firstRunningRuntime.getId()).thenReturn("instance-running-1");
    when(secondRunningRuntime.getId()).thenReturn("instance-running-2");

    List<FlowInstanceDetail> running = service.getInstances("flow-a", "running", 1, 1);

    assertEquals(1, running.size());
    assertEquals("instance-running-2", running.get(0).getInstanceId());
    assertEquals("running", running.get(0).getStatus());
    verify(processQuery, never()).list();
  }

  @Test
  void getInstancesCountUsesRuntimeStateForRunningAndSuspendedStatuses() {
    TaskService taskService = mock(TaskService.class);
    RuntimeService runtimeService = mock(RuntimeService.class);
    RepositoryService repositoryService = mock(RepositoryService.class);
    HistoryService historyService = mock(HistoryService.class);

    FlowableFlowInstanceService service = new FlowableFlowInstanceService(taskService, runtimeService, repositoryService, historyService, null, Collections.emptyList(), null);

    HistoricProcessInstanceQuery historicQuery = mock(HistoricProcessInstanceQuery.class);
    ProcessInstanceQuery runtimeQuery = mock(ProcessInstanceQuery.class);
    ProcessInstanceQuery activeRuntimeQuery = mock(ProcessInstanceQuery.class);
    ProcessInstanceQuery suspendedRuntimeQuery = mock(ProcessInstanceQuery.class);

    when(historyService.createHistoricProcessInstanceQuery()).thenReturn(historicQuery);
    when(historicQuery.processDefinitionKey("flow-a")).thenReturn(historicQuery);
    when(historicQuery.unfinished()).thenReturn(historicQuery);
    when(historicQuery.count()).thenReturn(2L);

    when(runtimeService.createProcessInstanceQuery()).thenReturn(runtimeQuery);
    when(runtimeQuery.processDefinitionKey("flow-a")).thenReturn(runtimeQuery);
    when(runtimeQuery.active()).thenReturn(activeRuntimeQuery);
    when(runtimeQuery.suspended()).thenReturn(suspendedRuntimeQuery);
    when(activeRuntimeQuery.count()).thenReturn(1L);
    when(suspendedRuntimeQuery.count()).thenReturn(1L);

    assertEquals(1L, service.getInstancesCount("flow-a", "running"));
    assertEquals(1L, service.getInstancesCount("flow-a", "suspended"));
  }

  @Test
  void getStatisticMapsDefinitionCountsAndStatus() {
    TaskService taskService = mock(TaskService.class);
    RuntimeService runtimeService = mock(RuntimeService.class);
    RepositoryService repositoryService = mock(RepositoryService.class);
    HistoryService historyService = mock(HistoryService.class);

    FlowableFlowInstanceService service = new FlowableFlowInstanceService(taskService, runtimeService, repositoryService, historyService, null, Collections.emptyList(), null);

    ProcessDefinitionQuery definitionQuery = mock(ProcessDefinitionQuery.class);
    ProcessDefinition definition = mock(ProcessDefinition.class);
    HistoricProcessInstanceQuery totalQuery = mock(HistoricProcessInstanceQuery.class);
    HistoricProcessInstanceQuery durationQuery = mock(HistoricProcessInstanceQuery.class);
    HistoricTaskInstanceQuery durationTaskQuery = mock(HistoricTaskInstanceQuery.class);
    HistoricProcessInstanceQuery unfinishedQuery = mock(HistoricProcessInstanceQuery.class);
    ProcessInstanceQuery runtimeQuery = mock(ProcessInstanceQuery.class);
    ProcessInstanceQuery activeRuntimeQuery = mock(ProcessInstanceQuery.class);
    ProcessInstanceQuery suspendedRuntimeQuery = mock(ProcessInstanceQuery.class);
    HistoricProcessInstance finishedInstance = mock(HistoricProcessInstance.class);
    HistoricTaskInstance approvedTask = mock(HistoricTaskInstance.class);
    HistoricTaskInstance systemTask = mock(HistoricTaskInstance.class);

    when(repositoryService.createProcessDefinitionQuery()).thenReturn(definitionQuery);
    when(definitionQuery.latestVersion()).thenReturn(definitionQuery);
    when(definitionQuery.processDefinitionCategoryLike(Constants.OUROBOROS_NAMESPACE + "/%")).thenReturn(definitionQuery);
    when(definitionQuery.active()).thenReturn(definitionQuery);
    when(definitionQuery.orderByProcessDefinitionKey()).thenReturn(definitionQuery);
    when(definitionQuery.asc()).thenReturn(definitionQuery);
    when(definitionQuery.listPage(0, 20)).thenReturn(Collections.singletonList(definition));
    when(definition.getCategory()).thenReturn(Constants.OUROBOROS_NAMESPACE + "/finance");
    when(definition.getKey()).thenReturn("flow-a");
    when(definition.getName()).thenReturn("采购审批");
    when(definition.getDescription()).thenReturn("采购流程");
    when(definition.isSuspended()).thenReturn(false);

    when(historyService.createHistoricProcessInstanceQuery()).thenReturn(totalQuery, durationQuery);
    when(totalQuery.processDefinitionKey("flow-a")).thenReturn(totalQuery);
    when(totalQuery.count()).thenReturn(5L);
    when(totalQuery.unfinished()).thenReturn(unfinishedQuery);
    when(unfinishedQuery.count()).thenReturn(2L);
    when(durationQuery.processDefinitionKey("flow-a")).thenReturn(durationQuery);
    when(durationQuery.finished()).thenReturn(durationQuery);
    when(durationQuery.list()).thenReturn(Collections.singletonList(finishedInstance));
    when(finishedInstance.getStartTime()).thenReturn(dateOf(2026, Month.JANUARY, 1, 9, 0));
    when(finishedInstance.getEndTime()).thenReturn(dateOf(2026, Month.JANUARY, 1, 11, 0));

    when(historyService.createHistoricTaskInstanceQuery()).thenReturn(durationTaskQuery);
    when(durationTaskQuery.processDefinitionKey("flow-a")).thenReturn(durationTaskQuery);
    when(durationTaskQuery.finished()).thenReturn(durationTaskQuery);
    when(durationTaskQuery.list()).thenReturn(Arrays.asList(approvedTask, systemTask));
    when(approvedTask.getAssignee()).thenReturn("approver-1");
    when(approvedTask.getStartTime()).thenReturn(dateOf(2026, Month.JANUARY, 1, 9, 10));
    when(approvedTask.getEndTime()).thenReturn(dateOf(2026, Month.JANUARY, 1, 9, 40));
    when(systemTask.getStartTime()).thenReturn(dateOf(2026, Month.JANUARY, 1, 9, 45));
    when(systemTask.getEndTime()).thenReturn(dateOf(2026, Month.JANUARY, 1, 10, 15));

    when(runtimeService.createProcessInstanceQuery()).thenReturn(runtimeQuery);
    when(runtimeQuery.processDefinitionKey("flow-a")).thenReturn(runtimeQuery);
    when(runtimeQuery.active()).thenReturn(activeRuntimeQuery);
    when(runtimeQuery.suspended()).thenReturn(suspendedRuntimeQuery);
    when(activeRuntimeQuery.count()).thenReturn(1L);
    when(suspendedRuntimeQuery.count()).thenReturn(1L);

    List<FlowInstanceStatistic> statistics = service.getStatistic(null, "deployed", 0, 20);

    assertEquals(1, statistics.size());
    FlowInstanceStatistic statistic = statistics.get(0);
    assertEquals("flow-a", statistic.getFlowKey());
    assertEquals("采购审批", statistic.getDisplayName());
    assertEquals("finance", statistic.getCategory());
    assertEquals("采购流程", statistic.getDescription());
    assertEquals(5L, statistic.getTotal());
    assertEquals(1L, statistic.getRunning());
    assertEquals(1L, statistic.getSuspended());
    assertEquals(3L, statistic.getFinished());
    assertEquals(7200000L, statistic.getAvgDurationMillis());
    assertEquals(2L, statistic.getNodeCount());
    assertEquals(1800000L, statistic.getAvgNodeDurationMillis());
    assertEquals(1L, statistic.getHandledCount());
    assertEquals(1800000L, statistic.getAvgHandleMillis());
    assertEquals("deployed", statistic.getStatus());
  }

  @Test
  void getDurationStatisticAggregatesFlowNodesAndApproversWithinRange() {
    TaskService taskService = mock(TaskService.class);
    RuntimeService runtimeService = mock(RuntimeService.class);
    RepositoryService repositoryService = buildActiveRepositoryService(v2FlowJson());
    HistoryService historyService = mock(HistoryService.class);

    FlowableFlowInstanceService service = new FlowableFlowInstanceService(taskService, runtimeService, repositoryService, historyService, null, Collections.emptyList(), null);

    HistoricProcessInstanceQuery processQuery = mock(HistoricProcessInstanceQuery.class);
    HistoricTaskInstanceQuery taskQuery = mock(HistoricTaskInstanceQuery.class);
    HistoricProcessInstance inRangeInstance = mock(HistoricProcessInstance.class);
    HistoricProcessInstance outOfRangeInstance = mock(HistoricProcessInstance.class);
    HistoricTaskInstance approveAFirst = mock(HistoricTaskInstance.class);
    HistoricTaskInstance approveASecond = mock(HistoricTaskInstance.class);
    HistoricTaskInstance approveB = mock(HistoricTaskInstance.class);
    HistoricTaskInstance ignoredTask = mock(HistoricTaskInstance.class);

    when(historyService.createHistoricProcessInstanceQuery()).thenReturn(processQuery);
    when(processQuery.processDefinitionKey("flow-a")).thenReturn(processQuery);
    when(processQuery.finished()).thenReturn(processQuery);
    when(processQuery.list()).thenReturn(Arrays.asList(inRangeInstance, outOfRangeInstance));
    when(inRangeInstance.getId()).thenReturn("instance-1");
    when(inRangeInstance.getStartTime()).thenReturn(dateOf(2026, Month.JANUARY, 1, 9, 0));
    when(inRangeInstance.getEndTime()).thenReturn(dateOf(2026, Month.JANUARY, 1, 11, 0));
    when(outOfRangeInstance.getId()).thenReturn("instance-2");
    when(outOfRangeInstance.getStartTime()).thenReturn(dateOf(2025, Month.DECEMBER, 31, 9, 0));
    when(outOfRangeInstance.getEndTime()).thenReturn(dateOf(2025, Month.DECEMBER, 31, 10, 0));

    when(historyService.createHistoricTaskInstanceQuery()).thenReturn(taskQuery);
    when(taskQuery.processDefinitionKey("flow-a")).thenReturn(taskQuery);
    when(taskQuery.finished()).thenReturn(taskQuery);
    when(taskQuery.list()).thenReturn(Arrays.asList(approveAFirst, approveASecond, approveB, ignoredTask));
    mockHistoricTask(approveAFirst, "instance-1", "approve-a", "经理审批", "approver-1",
        dateOf(2026, Month.JANUARY, 1, 9, 10), dateOf(2026, Month.JANUARY, 1, 9, 40));
    mockHistoricTask(approveASecond, "instance-1", "approve-a", "经理审批", "approver-2",
        dateOf(2026, Month.JANUARY, 1, 9, 40), dateOf(2026, Month.JANUARY, 1, 11, 10));
    mockHistoricTask(approveB, "instance-1", "approve-b", "财务审批", "approver-1",
        dateOf(2026, Month.JANUARY, 1, 10, 0), dateOf(2026, Month.JANUARY, 1, 11, 0));
    mockHistoricTask(ignoredTask, "instance-2", "approve-a", "经理审批", "approver-1",
        dateOf(2025, Month.DECEMBER, 31, 9, 0), dateOf(2025, Month.DECEMBER, 31, 10, 0));

    FlowDurationStatistic statistic = service.getDurationStatistic(
        "flow-a",
        dateOf(2026, Month.JANUARY, 1, 0, 0),
        dateOf(2026, Month.JANUARY, 2, 0, 0));

    assertEquals("flow-a", statistic.getFlowKey());
    assertEquals(1L, statistic.getInstanceCount());
    assertEquals(7200000L, statistic.getAvgDurationMillis());
    assertEquals(2, statistic.getNodes().size());
    assertEquals("approve-a", statistic.getNodes().get(0).getNodeId());
    assertEquals("经理审批", statistic.getNodes().get(0).getNodeName());
    assertEquals(2L, statistic.getNodes().get(0).getTaskCount());
    assertEquals(3600000L, statistic.getNodes().get(0).getAvgDurationMillis());
    assertEquals("approve-b", statistic.getNodes().get(1).getNodeId());
    assertEquals(1L, statistic.getNodes().get(1).getTaskCount());
    assertEquals(3600000L, statistic.getNodes().get(1).getAvgDurationMillis());
    assertEquals(2, statistic.getApprovers().size());
    assertEquals("approver-1", statistic.getApprovers().get(0).getUserId());
    assertEquals("approver-1", statistic.getApprovers().get(0).getUserName());
    assertEquals(2L, statistic.getApprovers().get(0).getHandledCount());
    assertEquals(2700000L, statistic.getApprovers().get(0).getAvgHandleMillis());
    assertEquals("approver-2", statistic.getApprovers().get(1).getUserId());
    assertEquals(1L, statistic.getApprovers().get(1).getHandledCount());
    assertEquals(5400000L, statistic.getApprovers().get(1).getAvgHandleMillis());
  }

  @Test
  void getStatisticOrdersDefinitionsByKeyBeforePaging() {
    TaskService taskService = mock(TaskService.class);
    RuntimeService runtimeService = mock(RuntimeService.class);
    RepositoryService repositoryService = mock(RepositoryService.class);
    HistoryService historyService = mock(HistoryService.class);

    FlowableFlowInstanceService service = new FlowableFlowInstanceService(taskService, runtimeService, repositoryService, historyService, null, Collections.emptyList(), null);

    ProcessDefinitionQuery definitionQuery = mock(ProcessDefinitionQuery.class);
    ProcessDefinitionQuery orderedQuery = mock(ProcessDefinitionQuery.class);

    when(repositoryService.createProcessDefinitionQuery()).thenReturn(definitionQuery);
    when(definitionQuery.latestVersion()).thenReturn(definitionQuery);
    when(definitionQuery.processDefinitionCategoryLike(Constants.OUROBOROS_NAMESPACE + "/%")).thenReturn(definitionQuery);
    when(definitionQuery.active()).thenReturn(definitionQuery);
    when(definitionQuery.listPage(0, 20)).thenThrow(new AssertionError("statistics query should order definitions before paging"));
    when(definitionQuery.orderByProcessDefinitionKey()).thenReturn(orderedQuery);
    when(orderedQuery.asc()).thenReturn(orderedQuery);
    when(orderedQuery.listPage(0, 20)).thenReturn(Collections.emptyList());

    assertEquals(Collections.emptyList(), service.getStatistic(null, "deployed", 0, 20));
  }

  @Test
  void cancelDeletesRuntimeInstance() {
    TaskService taskService = mock(TaskService.class);
    RuntimeService runtimeService = mock(RuntimeService.class);
    HistoryService historyService = mock(HistoryService.class);
    FlowableFlowInstanceService service = new FlowableFlowInstanceService(taskService, runtimeService, null, historyService, null, Collections.emptyList(), null);
    CollectingFlowInstanceListener listener = installInstanceEventCollector(service);

    ProcessInstanceQuery processInstanceQuery = mock(ProcessInstanceQuery.class);
    ProcessInstance processInstance = mock(ProcessInstance.class);
    HistoricTaskInstanceQuery historicTaskQuery = mock(HistoricTaskInstanceQuery.class);
    HistoricTaskInstance lastTask = mock(HistoricTaskInstance.class);
    when(runtimeService.createProcessInstanceQuery()).thenReturn(processInstanceQuery);
    when(processInstanceQuery.processInstanceId("instance-1")).thenReturn(processInstanceQuery);
    when(processInstanceQuery.singleResult()).thenReturn(processInstance);
    when(processInstance.getProcessDefinitionKey()).thenReturn("flow-a");
    when(runtimeService.getVariable("instance-1", Constants.Variable.PROCESS_FLOW_KEY, String.class)).thenReturn("flow-a");
    when(runtimeService.getVariable("instance-1", Constants.Variable.PROCESS_STARTER, String.class)).thenReturn("starter-1");
    AtomicBoolean deleted = new AtomicBoolean(false);
    when(runtimeService.getVariable("instance-1", Constants.Variable.APPROVAL_CASE_ID, String.class))
        .thenAnswer(invocation -> deleted.get() ? null : "case-1");
    when(runtimeService.getVariable("instance-1", Constants.Variable.SUBMISSION_ROUND, Integer.class))
        .thenAnswer(invocation -> deleted.get() ? null : 3);
    doAnswer(invocation -> {
      deleted.set(true);
      return null;
    }).when(runtimeService).deleteProcessInstance("instance-1", "主动取消");
    when(historyService.createHistoricTaskInstanceQuery()).thenReturn(historicTaskQuery);
    when(historicTaskQuery.processInstanceId("instance-1")).thenReturn(historicTaskQuery);
    when(historicTaskQuery.finished()).thenReturn(historicTaskQuery);
    when(historicTaskQuery.orderByHistoricTaskInstanceEndTime()).thenReturn(historicTaskQuery);
    when(historicTaskQuery.desc()).thenReturn(historicTaskQuery);
    when(historicTaskQuery.listPage(0, 1)).thenReturn(Collections.singletonList(lastTask));
    when(lastTask.getId()).thenReturn("task-last");

    Try<Void> result = service.cancel("instance-1", "user-a", "主动取消");

    assertTrue(result.isSuccess());
    ArgumentCaptor<Map<String, Object>> variablesCaptor = ArgumentCaptor.forClass(Map.class);
    verify(runtimeService).setVariables(eq("instance-1"), variablesCaptor.capture());
    Map<String, Object> variables = variablesCaptor.getValue();
    assertEquals("user-a", variables.get(Constants.PROCESS_CANCEL_USER_VARIABLE_NAME));
    assertEquals("主动取消", variables.get(Constants.PROCESS_CANCEL_REASON_VARIABLE_NAME));
    assertEquals(Boolean.FALSE, variables.get(Constants.Variable.PROCESS_APPROVED));
    assertEquals("task-last", variables.get(Constants.Variable.PROCESS_LAST_FINISHED_TASK_ID));
    assertTrue(variables.get(Constants.PROCESS_CANCEL_TIME_VARIABLE_NAME) instanceof LocalDateTime);
    verify(runtimeService).deleteProcessInstance("instance-1", "主动取消");
    assertEquals(1, listener.getEvents().size());
    assertEquals(FlowInstanceEvent.EventType.CANCELLED, listener.getEvents().get(0).getEventType());
    assertEquals("starter-1", listener.getEvents().get(0).getStarter());
    assertEquals("task-last", listener.getEvents().get(0).getLastTaskId());
    assertEquals("case-1", listener.getEvents().get(0).getApprovalCaseId());
    assertEquals(3, listener.getEvents().get(0).getSubmissionRound());
  }

  @Test
  void cancelPrefersRuntimeLastFinishedTaskIdOverHistoryFallback() {
    TaskService taskService = mock(TaskService.class);
    RuntimeService runtimeService = mock(RuntimeService.class);
    HistoryService historyService = mock(HistoryService.class);
    FlowableFlowInstanceService service = new FlowableFlowInstanceService(taskService, runtimeService, null, historyService, null, Collections.emptyList(), null);
    CollectingFlowInstanceListener listener = installInstanceEventCollector(service);

    ProcessInstanceQuery processInstanceQuery = mock(ProcessInstanceQuery.class);
    ProcessInstance processInstance = mock(ProcessInstance.class);
    HistoricTaskInstanceQuery historicTaskQuery = mock(HistoricTaskInstanceQuery.class);
    HistoricTaskInstance historyTask = mock(HistoricTaskInstance.class);
    when(runtimeService.createProcessInstanceQuery()).thenReturn(processInstanceQuery);
    when(processInstanceQuery.processInstanceId("instance-1")).thenReturn(processInstanceQuery);
    when(processInstanceQuery.singleResult()).thenReturn(processInstance);
    when(processInstance.getProcessDefinitionKey()).thenReturn("flow-a");
    when(runtimeService.getVariable("instance-1", Constants.Variable.PROCESS_LAST_FINISHED_TASK_ID, String.class)).thenReturn("task-runtime");
    when(runtimeService.getVariable("instance-1", Constants.Variable.PROCESS_FLOW_KEY, String.class)).thenReturn("flow-a");
    when(runtimeService.getVariable("instance-1", Constants.Variable.PROCESS_STARTER, String.class)).thenReturn("starter-1");
    when(historyService.createHistoricTaskInstanceQuery()).thenReturn(historicTaskQuery);
    when(historicTaskQuery.processInstanceId("instance-1")).thenReturn(historicTaskQuery);
    when(historicTaskQuery.finished()).thenReturn(historicTaskQuery);
    when(historicTaskQuery.orderByHistoricTaskInstanceEndTime()).thenReturn(historicTaskQuery);
    when(historicTaskQuery.desc()).thenReturn(historicTaskQuery);
    when(historicTaskQuery.listPage(0, 1)).thenReturn(Collections.singletonList(historyTask));
    when(historyTask.getId()).thenReturn("task-history");

    Try<Void> result = service.cancel("instance-1", "user-a", "主动取消");

    assertTrue(result.isSuccess());
    ArgumentCaptor<Map<String, Object>> variablesCaptor = ArgumentCaptor.forClass(Map.class);
    verify(runtimeService).setVariables(eq("instance-1"), variablesCaptor.capture());
    assertEquals("task-runtime", variablesCaptor.getValue().get(Constants.Variable.PROCESS_LAST_FINISHED_TASK_ID));
    assertEquals("task-runtime", listener.getEvents().get(0).getLastTaskId());
  }

  @Test
  void pauseStoresVariablesSuspendsInstanceAndDispatchesSuspendedEvent() {
    TaskService taskService = mock(TaskService.class);
    RuntimeService runtimeService = mock(RuntimeService.class);
    FlowableFlowInstanceService service = new FlowableFlowInstanceService(taskService, runtimeService, Collections.emptyList());
    CollectingFlowInstanceListener listener = installInstanceEventCollector(service);

    ProcessInstanceQuery processInstanceQuery = mock(ProcessInstanceQuery.class);
    ProcessInstance processInstance = mock(ProcessInstance.class);
    when(runtimeService.createProcessInstanceQuery()).thenReturn(processInstanceQuery);
    when(processInstanceQuery.processInstanceId("instance-1")).thenReturn(processInstanceQuery);
    when(processInstanceQuery.singleResult()).thenReturn(processInstance);
    when(processInstance.isSuspended()).thenReturn(false);
    when(processInstance.getProcessDefinitionKey()).thenReturn("flow-a");
    when(runtimeService.getVariable("instance-1", Constants.Variable.PROCESS_FLOW_KEY, String.class)).thenReturn("flow-a");
    when(runtimeService.getVariable("instance-1", Constants.Variable.PROCESS_STARTER, String.class)).thenReturn("starter-1");

    Try<Void> result = service.pause("instance-1", "user-a", "维护窗口");

    assertTrue(result.isSuccess());
    ArgumentCaptor<Map<String, Object>> variablesCaptor = ArgumentCaptor.forClass(Map.class);
    verify(runtimeService).setVariables(eq("instance-1"), variablesCaptor.capture());
    Map<String, Object> variables = variablesCaptor.getValue();
    assertEquals("user-a", variables.get(Constants.PROCESS_PAUSE_USER_VARIABLE_NAME));
    assertEquals("维护窗口", variables.get(Constants.PROCESS_PAUSE_REASON_VARIABLE_NAME));
    assertTrue(variables.get(Constants.PROCESS_PAUSE_TIME_VARIABLE_NAME) instanceof LocalDateTime);
    verify(runtimeService).suspendProcessInstanceById("instance-1");
    assertEquals(1, listener.getEvents().size());
    assertEquals(FlowInstanceEvent.EventType.SUSPENDED, listener.getEvents().get(0).getEventType());
    assertEquals("flow-a", listener.getEvents().get(0).getFlowKey());
  }

  @Test
  void pauseMarksTransactionRollbackOnlyWhenInstanceEventDispatchFails() {
    RuntimeService runtimeService = mock(RuntimeService.class);
    RecordingTransactionManager transactionManager = new RecordingTransactionManager();
    FlowableFlowInstanceService target = new FlowableFlowInstanceService(mock(TaskService.class), runtimeService, Collections.emptyList());
    installInstanceEventListener(target, new ThrowingFlowInstanceListener("dispatch failed"));
    FlowInstanceService service = transactionalProxy(target, transactionManager);

    ProcessInstanceQuery processInstanceQuery = mock(ProcessInstanceQuery.class);
    ProcessInstance processInstance = mock(ProcessInstance.class);
    when(runtimeService.createProcessInstanceQuery()).thenReturn(processInstanceQuery);
    when(processInstanceQuery.processInstanceId("instance-1")).thenReturn(processInstanceQuery);
    when(processInstanceQuery.singleResult()).thenReturn(processInstance);
    when(processInstance.isSuspended()).thenReturn(false);
    when(processInstance.getProcessDefinitionKey()).thenReturn("flow-a");
    when(runtimeService.getVariable("instance-1", Constants.Variable.PROCESS_FLOW_KEY, String.class)).thenReturn("flow-a");
    when(runtimeService.getVariable("instance-1", Constants.Variable.PROCESS_STARTER, String.class)).thenReturn("starter-1");

    Try<Void> result = service.pause("instance-1", "user-a", "维护窗口");

    assertTrue(result.isFailure());
    assertEquals("dispatch failed", result.getCause().getMessage());
    assertEquals(0, transactionManager.getCommitCount());
    assertEquals(1, transactionManager.getRollbackCount());
    verify(runtimeService).suspendProcessInstanceById("instance-1");
  }

  @Test
  void resumeStoresVariablesActivatesInstanceAndDispatchesResumedEvent() {
    TaskService taskService = mock(TaskService.class);
    RuntimeService runtimeService = mock(RuntimeService.class);
    FlowableFlowInstanceService service = new FlowableFlowInstanceService(taskService, runtimeService, Collections.emptyList());
    CollectingFlowInstanceListener listener = installInstanceEventCollector(service);

    ProcessInstanceQuery processInstanceQuery = mock(ProcessInstanceQuery.class);
    ProcessInstance processInstance = mock(ProcessInstance.class);
    when(runtimeService.createProcessInstanceQuery()).thenReturn(processInstanceQuery);
    when(processInstanceQuery.processInstanceId("instance-1")).thenReturn(processInstanceQuery);
    when(processInstanceQuery.singleResult()).thenReturn(processInstance);
    when(processInstance.isSuspended()).thenReturn(true);
    when(processInstance.getProcessDefinitionKey()).thenReturn("flow-a");
    when(runtimeService.getVariable("instance-1", Constants.Variable.PROCESS_FLOW_KEY, String.class)).thenReturn("flow-a");
    when(runtimeService.getVariable("instance-1", Constants.Variable.PROCESS_STARTER, String.class)).thenReturn("starter-1");

    Try<Void> result = service.resume("instance-1", "user-a");

    assertTrue(result.isSuccess());
    ArgumentCaptor<Map<String, Object>> variablesCaptor = ArgumentCaptor.forClass(Map.class);
    verify(runtimeService).setVariables(eq("instance-1"), variablesCaptor.capture());
    Map<String, Object> variables = variablesCaptor.getValue();
    assertEquals("user-a", variables.get(Constants.PROCESS_RESUME_USER_VARIABLE_NAME));
    assertTrue(variables.get(Constants.PROCESS_RESUME_TIME_VARIABLE_NAME) instanceof LocalDateTime);
    verify(runtimeService).activateProcessInstanceById("instance-1");
    assertEquals(1, listener.getEvents().size());
    assertEquals(FlowInstanceEvent.EventType.RESUMED, listener.getEvents().get(0).getEventType());
    assertEquals("flow-a", listener.getEvents().get(0).getFlowKey());
  }

  @Test
  void completeApprovedStoresVariablesDeletesInstanceAndDispatchesApprovedEvent() {
    TaskService taskService = mock(TaskService.class);
    RuntimeService runtimeService = mock(RuntimeService.class);
    HistoryService historyService = mock(HistoryService.class);
    FlowableFlowInstanceService service = new FlowableFlowInstanceService(taskService, runtimeService, null, historyService, null, Collections.emptyList(), null);
    CollectingFlowInstanceListener listener = installInstanceEventCollector(service);

    ProcessInstanceQuery processInstanceQuery = mock(ProcessInstanceQuery.class);
    ProcessInstance processInstance = mock(ProcessInstance.class);
    HistoricTaskInstanceQuery historicTaskQuery = mock(HistoricTaskInstanceQuery.class);
    HistoricTaskInstance lastTask = mock(HistoricTaskInstance.class);
    when(runtimeService.createProcessInstanceQuery()).thenReturn(processInstanceQuery);
    when(processInstanceQuery.processInstanceId("instance-1")).thenReturn(processInstanceQuery);
    when(processInstanceQuery.singleResult()).thenReturn(processInstance);
    when(processInstance.getProcessDefinitionKey()).thenReturn("flow-a");
    when(runtimeService.getVariable("instance-1", Constants.Variable.PROCESS_FLOW_KEY, String.class)).thenReturn("flow-a");
    when(runtimeService.getVariable("instance-1", Constants.Variable.PROCESS_STARTER, String.class)).thenReturn("starter-1");
    when(historyService.createHistoricTaskInstanceQuery()).thenReturn(historicTaskQuery);
    when(historicTaskQuery.processInstanceId("instance-1")).thenReturn(historicTaskQuery);
    when(historicTaskQuery.finished()).thenReturn(historicTaskQuery);
    when(historicTaskQuery.orderByHistoricTaskInstanceEndTime()).thenReturn(historicTaskQuery);
    when(historicTaskQuery.desc()).thenReturn(historicTaskQuery);
    when(historicTaskQuery.listPage(0, 1)).thenReturn(Collections.singletonList(lastTask));
    when(lastTask.getId()).thenReturn("task-last");

    Try<Void> result = service.complete("instance-1", "user-a", true, "人工通过");

    assertTrue(result.isSuccess());
    ArgumentCaptor<Map<String, Object>> variablesCaptor = ArgumentCaptor.forClass(Map.class);
    verify(runtimeService).setVariables(eq("instance-1"), variablesCaptor.capture());
    Map<String, Object> variables = variablesCaptor.getValue();
    assertEquals("user-a", variables.get(Constants.PROCESS_CANCEL_USER_VARIABLE_NAME));
    assertEquals("人工通过", variables.get(Constants.PROCESS_CANCEL_REASON_VARIABLE_NAME));
    assertEquals(Boolean.TRUE, variables.get(Constants.Variable.PROCESS_APPROVED));
    assertEquals("task-last", variables.get(Constants.Variable.PROCESS_LAST_FINISHED_TASK_ID));
    assertTrue(variables.get(Constants.PROCESS_CANCEL_TIME_VARIABLE_NAME) instanceof LocalDateTime);
    verify(runtimeService).deleteProcessInstance("instance-1", "人工通过");
    assertEquals(1, listener.getEvents().size());
    assertEquals(FlowInstanceEvent.EventType.APPROVED, listener.getEvents().get(0).getEventType());
    assertEquals("task-last", listener.getEvents().get(0).getLastTaskId());
  }

  @Test
  void completeRejectedDispatchesRejectedEvent() {
    TaskService taskService = mock(TaskService.class);
    RuntimeService runtimeService = mock(RuntimeService.class);
    HistoryService historyService = mock(HistoryService.class);
    FlowableFlowInstanceService service = new FlowableFlowInstanceService(taskService, runtimeService, null, historyService, null, Collections.emptyList(), null);
    CollectingFlowInstanceListener listener = installInstanceEventCollector(service);

    ProcessInstanceQuery processInstanceQuery = mock(ProcessInstanceQuery.class);
    ProcessInstance processInstance = mock(ProcessInstance.class);
    HistoricTaskInstanceQuery historicTaskQuery = mock(HistoricTaskInstanceQuery.class);
    when(runtimeService.createProcessInstanceQuery()).thenReturn(processInstanceQuery);
    when(processInstanceQuery.processInstanceId("instance-1")).thenReturn(processInstanceQuery);
    when(processInstanceQuery.singleResult()).thenReturn(processInstance);
    when(processInstance.getProcessDefinitionKey()).thenReturn("flow-a");
    when(runtimeService.getVariable("instance-1", Constants.Variable.PROCESS_FLOW_KEY, String.class)).thenReturn("flow-a");
    when(runtimeService.getVariable("instance-1", Constants.Variable.PROCESS_STARTER, String.class)).thenReturn("starter-1");
    when(historyService.createHistoricTaskInstanceQuery()).thenReturn(historicTaskQuery);
    when(historicTaskQuery.processInstanceId("instance-1")).thenReturn(historicTaskQuery);
    when(historicTaskQuery.finished()).thenReturn(historicTaskQuery);
    when(historicTaskQuery.orderByHistoricTaskInstanceEndTime()).thenReturn(historicTaskQuery);
    when(historicTaskQuery.desc()).thenReturn(historicTaskQuery);
    when(historicTaskQuery.listPage(0, 1)).thenReturn(Collections.emptyList());

    Try<Void> result = service.complete("instance-1", "user-a", false, "人工拒绝");

    assertTrue(result.isSuccess());
    ArgumentCaptor<Map<String, Object>> variablesCaptor = ArgumentCaptor.forClass(Map.class);
    verify(runtimeService).setVariables(eq("instance-1"), variablesCaptor.capture());
    assertEquals(Boolean.FALSE, variablesCaptor.getValue().get(Constants.Variable.PROCESS_APPROVED));
    verify(runtimeService).deleteProcessInstance("instance-1", "人工拒绝");
    assertEquals(1, listener.getEvents().size());
    assertEquals(FlowInstanceEvent.EventType.REJECTED, listener.getEvents().get(0).getEventType());
  }

  private CollectingFlowInstanceListener installInstanceEventCollector(FlowableFlowInstanceService service) {
    CollectingFlowInstanceListener listener = new CollectingFlowInstanceListener();
    installInstanceEventListener(service, listener);
    return listener;
  }

  private void installInstanceEventListener(FlowableFlowInstanceService service, FlowInstanceListener listener) {
    try {
    InstanceEventDispatcher dispatcher = new InstanceEventDispatcher();
      Field listenersField = InstanceEventDispatcher.class.getDeclaredField("listeners");
      listenersField.setAccessible(true);
      listenersField.set(dispatcher, Collections.singletonList(listener));
      setField(service.commandService, "instanceEventDispatcher", dispatcher);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private void setField(Object target, String fieldName, Object value) {
    try {
      Field field = target.getClass().getDeclaredField(fieldName);
      field.setAccessible(true);
      field.set(target, value);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static class CollectingFlowInstanceListener implements FlowInstanceListener {
    private final List<FlowInstanceEvent> events = new ArrayList<FlowInstanceEvent>();

    @Override
    public void onEvent(FlowInstanceEvent event) {
      events.add(event);
    }

    List<FlowInstanceEvent> getEvents() {
      return events;
    }
  }

  private static class ThrowingFlowInstanceListener implements FlowInstanceListener {
    private final String message;

    private ThrowingFlowInstanceListener(String message) {
      this.message = message;
    }

    @Override
    public void onEvent(FlowInstanceEvent event) {
      throw new RuntimeException(message);
    }
  }

  @Test
  void getTimeLineMapsHistoricTasksToTimelineEntries() {
    TaskService taskService = mock(TaskService.class);
    RuntimeService runtimeService = mock(RuntimeService.class);
    RepositoryService repositoryService = mock(RepositoryService.class);
    HistoryService historyService = mock(HistoryService.class);

    FlowableFlowInstanceService service = new FlowableFlowInstanceService(taskService, runtimeService, repositoryService, historyService, null, Collections.emptyList(), null);

    HistoricProcessInstanceQuery processQuery = mock(HistoricProcessInstanceQuery.class);
    HistoricProcessInstance historicProcessInstance = mock(HistoricProcessInstance.class);
    HistoricTaskInstanceQuery taskQuery = mock(HistoricTaskInstanceQuery.class);
    HistoricTaskInstance historicTask = mock(HistoricTaskInstance.class);
    HistoricVariableInstanceQuery variableQuery = mock(HistoricVariableInstanceQuery.class);
    HistoricIdentityLink identityLink = mock(HistoricIdentityLink.class);
    ProcessInstanceQuery runtimeQuery = mock(ProcessInstanceQuery.class);

    when(historyService.createHistoricProcessInstanceQuery()).thenReturn(processQuery);
    when(processQuery.processInstanceId("instance-1")).thenReturn(processQuery);
    when(processQuery.singleResult()).thenReturn(historicProcessInstance);
    when(historicProcessInstance.getProcessDefinitionId()).thenReturn("process-definition-1");
    when(repositoryService.getBpmnModel("process-definition-1")).thenReturn(buildBpmnModel(v2FlowJson()));
    when(runtimeService.createProcessInstanceQuery()).thenReturn(runtimeQuery);
    when(runtimeQuery.processInstanceId("instance-1")).thenReturn(runtimeQuery);
    when(runtimeQuery.suspended()).thenReturn(runtimeQuery);
    when(runtimeQuery.count()).thenReturn(0L);

    when(historyService.createHistoricTaskInstanceQuery()).thenReturn(taskQuery);
    when(taskQuery.processInstanceId("instance-1")).thenReturn(taskQuery);
    when(taskQuery.orderByHistoricTaskInstanceStartTime()).thenReturn(taskQuery);
    when(taskQuery.asc()).thenReturn(taskQuery);
    when(taskQuery.list()).thenReturn(Collections.singletonList(historicTask));

    when(historicTask.getId()).thenReturn("task-1");
    when(historicTask.getName()).thenReturn("经理审批");
    when(historicTask.getTaskDefinitionKey()).thenReturn("approve-a");
    when(historicTask.getProcessInstanceId()).thenReturn("instance-1");
    when(historicTask.getAssignee()).thenReturn("approver-1");
    when(historicTask.getStartTime()).thenReturn(dateOf(2026, Month.MARCH, 18, 10, 0));
    when(historicTask.getEndTime()).thenReturn(dateOf(2026, Month.MARCH, 18, 10, 30));

    when(historyService.createHistoricVariableInstanceQuery()).thenReturn(variableQuery);
    when(variableQuery.processInstanceId("instance-1")).thenReturn(variableQuery);
    when(variableQuery.taskId("task-1")).thenReturn(variableQuery);
    when(variableQuery.variableName(Constants.Variable.TASK_APPROVED)).thenReturn(variableQuery);
    when(variableQuery.variableName(Constants.Variable.LOCAL_APPROVAL_ACTION)).thenReturn(variableQuery);
    when(variableQuery.singleResult()).thenReturn(null);
    when(variableQuery.list()).thenReturn(Collections.emptyList());
    when(historyService.getHistoricIdentityLinksForTask("task-1")).thenReturn(Collections.singletonList(identityLink));
    when(identityLink.getType()).thenReturn("candidate");
    when(identityLink.getUserId()).thenReturn("candidate-1");
    when(taskService.getTaskComments("task-1")).thenReturn(Collections.emptyList());

    List<FlowTimeline> timeline = service.getTimeLine("instance-1");

    assertEquals(1, timeline.size());
    assertEquals("approve-a", timeline.get(0).getFlowNode().getId());
    assertEquals("approver-1", timeline.get(0).getTaskInstances().get(0).getAssignee());
    assertEquals(Arrays.asList("candidate-1", "approver-1"), timeline.get(0).getTaskInstances().get(0).getCandidateUsers());
  }

  @Test
  void getTimeLineUsesProcessStarterAsStartNodeParticipant() {
    TaskService taskService = mock(TaskService.class);
    RuntimeService runtimeService = mock(RuntimeService.class);
    RepositoryService repositoryService = mock(RepositoryService.class);
    HistoryService historyService = mock(HistoryService.class);

    FlowableFlowInstanceService service = new FlowableFlowInstanceService(taskService, runtimeService, repositoryService, historyService, null, Collections.emptyList(), null);

    HistoricProcessInstanceQuery processQuery = mock(HistoricProcessInstanceQuery.class);
    HistoricProcessInstance historicProcessInstance = mock(HistoricProcessInstance.class);
    HistoricTaskInstanceQuery taskQuery = mock(HistoricTaskInstanceQuery.class);
    HistoricTaskInstance startTask = mock(HistoricTaskInstance.class);
    HistoricVariableInstanceQuery variableQuery = mock(HistoricVariableInstanceQuery.class);
    HistoricVariableInstance starterVariable = mock(HistoricVariableInstance.class);
    ProcessInstanceQuery runtimeQuery = mock(ProcessInstanceQuery.class);

    when(historyService.createHistoricProcessInstanceQuery()).thenReturn(processQuery);
    when(processQuery.processInstanceId("instance-1")).thenReturn(processQuery);
    when(processQuery.singleResult()).thenReturn(historicProcessInstance);
    when(historicProcessInstance.getProcessDefinitionId()).thenReturn("process-definition-1");
    when(repositoryService.getBpmnModel("process-definition-1")).thenReturn(buildBpmnModel(v2FlowJson()));
    when(runtimeService.createProcessInstanceQuery()).thenReturn(runtimeQuery);
    when(runtimeQuery.processInstanceId("instance-1")).thenReturn(runtimeQuery);
    when(runtimeQuery.suspended()).thenReturn(runtimeQuery);
    when(runtimeQuery.count()).thenReturn(0L);

    when(historyService.createHistoricVariableInstanceQuery()).thenReturn(variableQuery);
    when(variableQuery.processInstanceId("instance-1")).thenReturn(variableQuery);
    when(variableQuery.list()).thenReturn(Collections.singletonList(starterVariable));
    when(starterVariable.getVariableName()).thenReturn(Constants.Variable.PROCESS_STARTER);
    when(starterVariable.getValue()).thenReturn("admin");

    when(historyService.createHistoricTaskInstanceQuery()).thenReturn(taskQuery);
    when(taskQuery.processInstanceId("instance-1")).thenReturn(taskQuery);
    when(taskQuery.orderByHistoricTaskInstanceStartTime()).thenReturn(taskQuery);
    when(taskQuery.asc()).thenReturn(taskQuery);
    when(taskQuery.list()).thenReturn(Collections.singletonList(startTask));
    when(startTask.getId()).thenReturn("task-start");
    mockHistoricTask(
        startTask,
        "instance-1",
        "start-a",
        "发起节点",
        "legacy-employee-id",
        dateOf(2026, Month.MARCH, 18, 9, 0),
        null);
    when(taskService.createTaskQuery()).thenReturn(null);
    HistoricIdentityLink legacyIdentityLink = mock(HistoricIdentityLink.class);
    when(historyService.getHistoricIdentityLinksForTask("task-start")).thenReturn(Collections.singletonList(legacyIdentityLink));
    when(legacyIdentityLink.getType()).thenReturn("candidate");
    when(legacyIdentityLink.getUserId()).thenReturn("legacy-candidate-id");
    when(taskService.getTaskComments("task-start")).thenReturn(Collections.emptyList());

    List<FlowTimeline> timeline = service.getTimeLine("instance-1");

    assertEquals(1, timeline.size());
    assertEquals("start-a", timeline.get(0).getFlowNode().getId());
    FlowTimeline.TaskInstance taskInstance = timeline.get(0).getTaskInstances().get(0);
    assertEquals("admin", taskInstance.getAssignee());
    assertEquals(Collections.singletonList("admin"), taskInstance.getCandidateUsers());
  }

  @Test
  void getTimeLineUsesRuntimeAssigneeWhenActiveHistoricTaskAssigneeIsEmpty() {
    TaskService taskService = mock(TaskService.class);
    RuntimeService runtimeService = mock(RuntimeService.class);
    RepositoryService repositoryService = mock(RepositoryService.class);
    HistoryService historyService = mock(HistoryService.class);

    FlowableFlowInstanceService service = new FlowableFlowInstanceService(taskService, runtimeService, repositoryService, historyService, null, Collections.emptyList(), null);

    HistoricProcessInstanceQuery processQuery = mock(HistoricProcessInstanceQuery.class);
    HistoricProcessInstance historicProcessInstance = mock(HistoricProcessInstance.class);
    HistoricTaskInstanceQuery taskQuery = mock(HistoricTaskInstanceQuery.class);
    HistoricTaskInstance historicTask = mock(HistoricTaskInstance.class);
    HistoricVariableInstanceQuery variableQuery = mock(HistoricVariableInstanceQuery.class);
    ProcessInstanceQuery runtimeQuery = mock(ProcessInstanceQuery.class);
    TaskQuery runtimeTaskQuery = mock(TaskQuery.class);
    Task runtimeTask = mock(Task.class);
    IdentityLink runtimeIdentityLink = mock(IdentityLink.class);

    when(historyService.createHistoricProcessInstanceQuery()).thenReturn(processQuery);
    when(processQuery.processInstanceId("instance-1")).thenReturn(processQuery);
    when(processQuery.singleResult()).thenReturn(historicProcessInstance);
    when(historicProcessInstance.getProcessDefinitionId()).thenReturn("process-definition-1");
    when(repositoryService.getBpmnModel("process-definition-1")).thenReturn(buildBpmnModel(v2FlowJson()));
    when(runtimeService.createProcessInstanceQuery()).thenReturn(runtimeQuery);
    when(runtimeQuery.processInstanceId("instance-1")).thenReturn(runtimeQuery);
    when(runtimeQuery.suspended()).thenReturn(runtimeQuery);
    when(runtimeQuery.count()).thenReturn(0L);

    when(historyService.createHistoricVariableInstanceQuery()).thenReturn(variableQuery);
    when(variableQuery.processInstanceId("instance-1")).thenReturn(variableQuery);
    when(variableQuery.list()).thenReturn(Collections.emptyList());

    when(historyService.createHistoricTaskInstanceQuery()).thenReturn(taskQuery);
    when(taskQuery.processInstanceId("instance-1")).thenReturn(taskQuery);
    when(taskQuery.orderByHistoricTaskInstanceStartTime()).thenReturn(taskQuery);
    when(taskQuery.asc()).thenReturn(taskQuery);
    when(taskQuery.list()).thenReturn(Collections.singletonList(historicTask));
    mockHistoricTask(
        historicTask,
        "instance-1",
        "approve-a",
        "经理审批",
        null,
        dateOf(2026, Month.MARCH, 18, 10, 0),
        null);
    when(historicTask.getId()).thenReturn("task-1");
    when(historyService.getHistoricIdentityLinksForTask("task-1")).thenReturn(Collections.emptyList());

    when(taskService.createTaskQuery()).thenReturn(runtimeTaskQuery);
    when(runtimeTaskQuery.processInstanceId("instance-1")).thenReturn(runtimeTaskQuery);
    when(runtimeTaskQuery.list()).thenReturn(Collections.singletonList(runtimeTask));
    when(runtimeTask.getId()).thenReturn("task-1");
    when(runtimeTask.getAssignee()).thenReturn("approver-1");
    when(taskService.getIdentityLinksForTask("task-1")).thenReturn(Collections.singletonList(runtimeIdentityLink));
    when(runtimeIdentityLink.getType()).thenReturn("candidate");
    when(runtimeIdentityLink.getUserId()).thenReturn("candidate-1");
    when(taskService.getTaskComments("task-1")).thenReturn(Collections.emptyList());

    List<FlowTimeline> timeline = service.getTimeLine("instance-1");

    FlowTimeline.TaskInstance taskInstance = timeline.get(0).getTaskInstances().get(0);
    assertEquals("approver-1", taskInstance.getAssignee());
    assertEquals(TaskState.PROCESSING, taskInstance.getState());
    assertEquals(Arrays.asList("candidate-1", "approver-1"), taskInstance.getCandidateUsers());
  }

  @Test
  void getTimeLineUsesLoadedVariablesForMultipleApprovalSessionStatus() {
    TaskService taskService = mock(TaskService.class);
    RuntimeService runtimeService = mock(RuntimeService.class);
    RepositoryService repositoryService = buildActiveRepositoryService(multipleApprovalFlowJson());
    HistoryService historyService = mock(HistoryService.class);

    FlowableFlowInstanceService service = new FlowableFlowInstanceService(taskService, runtimeService, repositoryService, historyService, null, Collections.emptyList(), null);

    HistoricProcessInstanceQuery processQuery = mock(HistoricProcessInstanceQuery.class);
    HistoricProcessInstance historicProcessInstance = mock(HistoricProcessInstance.class);
    HistoricTaskInstanceQuery taskQuery = mock(HistoricTaskInstanceQuery.class);
    HistoricTaskInstance historicTask = mock(HistoricTaskInstance.class);
    HistoricVariableInstanceQuery variableQuery = mock(HistoricVariableInstanceQuery.class);
    HistoricVariableInstance sessionVariable = mock(HistoricVariableInstance.class);
    HistoricVariableInstance sessionAssignees = mock(HistoricVariableInstance.class);
    HistoricVariableInstance completedVariable = mock(HistoricVariableInstance.class);
    HistoricVariableInstance approvedVariable = mock(HistoricVariableInstance.class);
    ProcessInstanceQuery runtimeQuery = mock(ProcessInstanceQuery.class);

    when(historyService.createHistoricProcessInstanceQuery()).thenReturn(processQuery);
    when(processQuery.processInstanceId("instance-1")).thenReturn(processQuery);
    when(processQuery.singleResult()).thenReturn(historicProcessInstance);
    when(historicProcessInstance.getProcessDefinitionId()).thenReturn("process-definition-1");
    when(runtimeService.createProcessInstanceQuery()).thenReturn(runtimeQuery);
    when(runtimeQuery.processInstanceId("instance-1")).thenReturn(runtimeQuery);
    when(runtimeQuery.suspended()).thenReturn(runtimeQuery);
    when(runtimeQuery.count()).thenReturn(0L);

    when(historyService.createHistoricTaskInstanceQuery()).thenReturn(taskQuery);
    when(taskQuery.processInstanceId("instance-1")).thenReturn(taskQuery);
    when(taskQuery.orderByHistoricTaskInstanceStartTime()).thenReturn(taskQuery);
    when(taskQuery.asc()).thenReturn(taskQuery);
    when(taskQuery.list()).thenReturn(Collections.singletonList(historicTask));

    when(historicTask.getId()).thenReturn("task-1");
    when(historicTask.getName()).thenReturn("会签审批");
    when(historicTask.getTaskDefinitionKey()).thenReturn("multi-a");
    when(historicTask.getProcessInstanceId()).thenReturn("instance-1");
    when(historicTask.getAssignee()).thenReturn("approver-1");
    when(historicTask.getStartTime()).thenReturn(dateOf(2026, Month.MARCH, 18, 11, 0));
    when(historicTask.getEndTime()).thenReturn(null);

    when(historyService.createHistoricVariableInstanceQuery()).thenReturn(variableQuery);
    when(variableQuery.processInstanceId("instance-1")).thenReturn(variableQuery);
    when(variableQuery.list()).thenReturn(Arrays.asList(sessionVariable, sessionAssignees, completedVariable, approvedVariable));

    when(sessionVariable.getTaskId()).thenReturn("task-1");
    when(sessionVariable.getVariableName()).thenReturn(Constants.Variable.MULTIPLE_APPROVAL_SESSION);
    when(sessionVariable.getValue()).thenReturn("session-1");
    when(sessionAssignees.getVariableName()).thenReturn(
        Constants.Variable.MULTIPLE_APPROVAL_SESSION + "session-1" + Constants.Variable.MULTIPLE_APPROVAL_ASSIGNEE_LIST);
    when(sessionAssignees.getValue()).thenReturn(Arrays.asList("approver-1", "approver-2"));
    when(completedVariable.getVariableName()).thenReturn(
        Constants.Variable.MULTIPLE_APPROVAL_SESSION + "session-1" + Constants.Variable.MULTIPLE_APPROVAL_COMPLETED_SUFFIX);
    when(completedVariable.getValue()).thenReturn(Boolean.FALSE);
    when(approvedVariable.getVariableName()).thenReturn(
        Constants.Variable.MULTIPLE_APPROVAL_SESSION + "session-1" + Constants.Variable.APPROVED_SUFFIX);
    when(approvedVariable.getValue()).thenReturn(Boolean.FALSE);

    when(historyService.getHistoricIdentityLinksForTask("task-1")).thenReturn(Collections.emptyList());
    when(taskService.getTaskComments("task-1")).thenReturn(Collections.emptyList());

    List<FlowTimeline> timeline = service.getTimeLine("instance-1");
    FlowTimeline multipleTimeline = timeline.stream()
        .filter(item -> "multi-a".equals(item.getFlowNode().getId()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("multi-a timeline not found"));

    assertEquals(TaskState.PROCESSING, multipleTimeline.getState());
    assertEquals(2, multipleTimeline.getTaskInstances().size());
    assertEquals("approver-2", multipleTimeline.getTaskInstances().get(1).getAssignee());
  }

  private RepositoryService buildActiveRepositoryService(String rawJson) {
    RepositoryService repositoryService = mock(RepositoryService.class);
    ProcessDefinitionQuery query = mock(ProcessDefinitionQuery.class);
    ProcessDefinition processDefinition = mock(ProcessDefinition.class);

    when(repositoryService.createProcessDefinitionQuery()).thenReturn(query);
    when(query.processDefinitionKey("flow-a")).thenReturn(query);
    when(query.latestVersion()).thenReturn(query);
    when(query.singleResult()).thenReturn(processDefinition);
    when(processDefinition.getId()).thenReturn("process-definition-1");
    when(processDefinition.getKey()).thenReturn("flow-a");
    when(processDefinition.isSuspended()).thenReturn(false);
    when(repositoryService.getBpmnModel("process-definition-1")).thenReturn(buildBpmnModel(rawJson));
    return repositoryService;
  }

  private BpmnModel buildBpmnModel(String rawJson) {
    BpmnModel model = new BpmnModel();
    Process process = new Process();
    model.addProcess(process);
    BpmnUtil.saveFlowDefinitionRawJson(process, rawJson);
    return model;
  }

  private Date dateOf(int year, Month month, int day, int hour, int minute) {
    return Date.from(LocalDateTime.of(year, month, day, hour, minute)
        .atZone(ZoneId.systemDefault())
        .toInstant());
  }

  private void mockHistoricTask(
      HistoricTaskInstance task,
      String processInstanceId,
      String nodeId,
      String nodeName,
      String assignee,
      Date startTime,
      Date endTime) {
    when(task.getProcessInstanceId()).thenReturn(processInstanceId);
    when(task.getTaskDefinitionKey()).thenReturn(nodeId);
    when(task.getName()).thenReturn(nodeName);
    when(task.getAssignee()).thenReturn(assignee);
    when(task.getStartTime()).thenReturn(startTime);
    when(task.getEndTime()).thenReturn(endTime);
  }

  private String v2FlowJson() {
    return "{\n"
        + "  \"formatVersion\": 2,\n"
        + "  \"id\": \"flow-a\",\n"
        + "  \"name\": \"采购审批\",\n"
        + "  \"category\": \"\",\n"
        + "  \"icon\": \"shenpi\",\n"
        + "  \"description\": \"\",\n"
        + "  \"formType\": \"default\",\n"
        + "  \"formKey\": \"purchase-form\",\n"
        + "  \"nodes\": [\n"
        + "    {\n"
        + "      \"type\": \"发起节点\",\n"
        + "      \"name\": \"发起节点\",\n"
        + "      \"id\": \"start-a\",\n"
        + "      \"permission\": [\n"
        + "        { \"name\": \"amount\", \"label\": \"金额\", \"required\": true, \"visible\": true, \"writable\": true }\n"
        + "      ]\n"
        + "    },\n"
        + "    {\n"
        + "      \"type\": \"审批节点\",\n"
        + "      \"name\": \"经理审批\",\n"
        + "      \"id\": \"approve-a\",\n"
        + "      \"subType\": \"单签\",\n"
        + "      \"candidates\": [{ \"type\": \"UserSelect\", \"mode\": \"single\" }],\n"
        + "      \"permission\": [],\n"
        + "      \"actions\": [\"reject\"]\n"
        + "    }\n"
        + "  ],\n"
        + "  \"edges\": [\n"
        + "    { \"id\": \"e1\", \"type\": \"SEQUENCE\", \"sourceId\": \"start-a\", \"targetId\": \"approve-a\" }\n"
        + "  ]\n"
        + "}";
  }

  private String v2FlowJsonWithStartNodeNotFirst() {
    return "{\n"
        + "  \"formatVersion\": 2,\n"
        + "  \"id\": \"flow-a\",\n"
        + "  \"name\": \"采购审批\",\n"
        + "  \"category\": \"\",\n"
        + "  \"icon\": \"shenpi\",\n"
        + "  \"description\": \"\",\n"
        + "  \"formType\": \"default\",\n"
        + "  \"formKey\": \"purchase-form\",\n"
        + "  \"nodes\": [\n"
        + "    {\n"
        + "      \"type\": \"审批节点\",\n"
        + "      \"name\": \"经理审批\",\n"
        + "      \"id\": \"approve-a\",\n"
        + "      \"subType\": \"单签\",\n"
        + "      \"candidates\": [],\n"
        + "      \"permission\": [\n"
        + "        { \"name\": \"approval-field\", \"label\": \"审批字段\", \"visible\": true, \"writable\": true }\n"
        + "      ]\n"
        + "    },\n"
        + "    {\n"
        + "      \"type\": \"发起节点\",\n"
        + "      \"name\": \"发起节点\",\n"
        + "      \"id\": \"start-a\",\n"
        + "      \"permission\": [\n"
        + "        { \"name\": \"start-field\", \"label\": \"发起字段\", \"visible\": true, \"writable\": true }\n"
        + "      ],\n"
        + "      \"candidates\": []\n"
        + "    }\n"
        + "  ],\n"
        + "  \"edges\": [\n"
        + "    { \"id\": \"e1\", \"type\": \"SEQUENCE\", \"sourceId\": \"start-a\", \"targetId\": \"approve-a\" }\n"
        + "  ]\n"
        + "}";
  }

  private String businessModelFlowJsonWithStartPermission() {
    return "{\n"
        + "  \"formatVersion\": 2,\n"
        + "  \"id\": \"flow-a\",\n"
        + "  \"name\": \"业务模型审批\",\n"
        + "  \"category\": \"\",\n"
        + "  \"icon\": \"shenpi\",\n"
        + "  \"description\": \"\",\n"
        + "  \"formType\": \"business-model\",\n"
        + "  \"formKey\": \"purchase-form\",\n"
        + "  \"nodes\": [\n"
        + "    {\n"
        + "      \"type\": \"发起节点\",\n"
        + "      \"name\": \"发起节点\",\n"
        + "      \"id\": \"start-a\",\n"
        + "      \"permission\": [\n"
        + "        { \"name\": \"start-field\", \"label\": \"发起字段\", \"visible\": true, \"writable\": true }\n"
        + "      ],\n"
        + "      \"candidates\": []\n"
        + "    },\n"
        + "    { \"type\": \"审批节点\", \"name\": \"经理审批\", \"id\": \"approve-a\", \"subType\": \"单签\", \"candidates\": [], \"permission\": [], \"actions\": [\"reject\"] }\n"
        + "  ],\n"
        + "  \"edges\": [\n"
        + "    { \"id\": \"e1\", \"type\": \"SEQUENCE\", \"sourceId\": \"start-a\", \"targetId\": \"approve-a\" }\n"
        + "  ]\n"
        + "}";
  }

  private String multipleApprovalFlowJson() {
    return "{\n"
        + "  \"formatVersion\": 2,\n"
        + "  \"id\": \"flow-multi-a\",\n"
        + "  \"name\": \"会签审批\",\n"
        + "  \"category\": \"\",\n"
        + "  \"icon\": \"shenpi\",\n"
        + "  \"description\": \"\",\n"
        + "  \"formType\": \"default\",\n"
        + "  \"formKey\": \"multi-form\",\n"
        + "  \"nodes\": [\n"
        + "    { \"type\": \"发起节点\", \"name\": \"发起节点\", \"id\": \"start-a\", \"permission\": [], \"candidates\": [] },\n"
        + "    {\n"
        + "      \"type\": \"审批节点\",\n"
        + "      \"name\": \"会签审批\",\n"
        + "      \"id\": \"multi-a\",\n"
        + "      \"subType\": \"会签\",\n"
        + "      \"candidates\": [],\n"
        + "      \"permission\": [],\n"
        + "      \"actions\": [\"reject\"],\n"
        + "      \"percentage\": 100,\n"
        + "      \"multipleMode\": \"并行\",\n"
        + "      \"rejectAction\": \"reject\"\n"
        + "    }\n"
        + "  ],\n"
        + "  \"edges\": [\n"
        + "    { \"id\": \"e1\", \"type\": \"SEQUENCE\", \"sourceId\": \"start-a\", \"targetId\": \"multi-a\" }\n"
        + "  ]\n"
        + "}";
  }

  private List<String> permissionNames(List permissions) {
    List<String> names = new ArrayList<String>();
    for (Object permission : permissions) {
      if (permission instanceof NodeFormFieldPermission) {
        NodeFormFieldPermission nodeFormFieldPermission = (NodeFormFieldPermission) permission;
        names.add(nodeFormFieldPermission.getName());
      }
    }
    return names;
  }

  private FlowInstanceService transactionalProxy(FlowableFlowInstanceService target, RecordingTransactionManager transactionManager) {
    ProxyFactory proxyFactory = new ProxyFactory(target);
    proxyFactory.addAdvice(new TransactionInterceptor(transactionManager, new AnnotationTransactionAttributeSource()));
    return (FlowInstanceService) proxyFactory.getProxy();
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
}
