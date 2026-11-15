package com.ouroboros.workflow.approval.flowable.delegate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.CustomProperty;
import org.flowable.bpmn.model.Process;
import org.flowable.bpmn.model.ServiceTask;
import org.flowable.bpmn.model.UserTask;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.repository.ProcessDefinition;
import org.junit.jupiter.api.Test;

import com.ouroboros.workflow.approval.definition.Candidate;
import com.ouroboros.workflow.approval.definition.candidates.UserCandidate;
import com.ouroboros.workflow.approval.flowable.transpiler.Constants;
import com.ouroboros.workflow.approval.flowable.util.BpmnUtil;

class InitializeMultipleApprovalServiceTaskTest {
  @Test
  void executeUsesUserSelectedAssigneesAndInitializesControlVariables() {
    DelegateExecution execution = mock(DelegateExecution.class);
    ServiceTask serviceTask = new ServiceTask();
    serviceTask.setId("multi-a_reset_multiple_approval_service_task");
    serviceTask.setCustomProperties(Arrays.asList(
        customProperty(Constants.Property.MULTIPLE_APPROVAL_TASK_ID, "multi-a"),
        customProperty(Constants.Property.PERCENTAGE, "66"),
        customProperty(Constants.Property.IS_SEQUENTIAL, "false")
    ));
    BpmnUtil.saveRejectAction(serviceTask, Constants.Value.REJECTED);

    Map<String, List<String>> selected = new HashMap<String, List<String>>();
    selected.put("multi-a", Arrays.asList("user-a", "user-b", "user-c"));

    when(execution.getCurrentFlowElement()).thenReturn(serviceTask);
    when(execution.getVariable(Constants.Variable.USER_SELECTED_ASSIGNEES, Map.class)).thenReturn(selected);

    newDelegate(null).execute(execution);

    verify(execution).setVariable(
        eq(Constants.Variable.getMultipleApprovalAssigneeListVariableName("multi-a")),
        eq(Arrays.asList("user-a", "user-b", "user-c")));
    verify(execution).setVariable(eq(Constants.Variable.getMultipleApprovalSessionVariableName("multi-a")), anyString());
    verify(execution).setVariable("multi-a" + Constants.Variable.APPROVAL_ACTION_SUFFIX, Constants.Value.REJECTED);
    verify(execution).setVariable("multi-a" + Constants.Variable.MULTIPLE_APPROVED_PERCENTAGE_SUFFIX, 66);
    verify(execution).setVariable("multi-a" + Constants.Variable.MULTIPLE_APPROVAL_COMPLETED_SUFFIX, false);
    verify(execution).setVariable("multi-a" + Constants.Variable.MULTIPLE_APPROVAL_IS_SEQUENTIAL_SUFFIX, false);
    verify(execution).setVariable("multi-a" + Constants.Variable.APPROVED_SUFFIX, false);
    verify(execution).setVariable("multi-a" + Constants.Variable.NR_OF_APPROVED_SUFFIX, 0);
    verify(execution).setVariable("multi-a" + Constants.Variable.NR_OF_REJECTED_SUFFIX, 0);
  }

  @Test
  void executeApprovesImmediatelyWhenNoAssigneeAndRejectModeIsApproved() {
    DelegateExecution execution = mock(DelegateExecution.class);
    ServiceTask serviceTask = new ServiceTask();
    serviceTask.setId("multi-a_reset_multiple_approval_service_task");
    serviceTask.setCustomProperties(Arrays.asList(
        customProperty(Constants.Property.MULTIPLE_APPROVAL_TASK_ID, "multi-a"),
        customProperty(Constants.Property.PERCENTAGE, "100"),
        customProperty(Constants.Property.IS_SEQUENTIAL, "false")
    ));
    BpmnUtil.saveRejectAction(serviceTask, Constants.Value.REJECTED);

    when(execution.getCurrentFlowElement()).thenReturn(serviceTask);
    when(execution.getVariable(Constants.Variable.USER_SELECTED_ASSIGNEES, Map.class)).thenReturn(Collections.emptyMap());

    newDelegate(null).approvalMultiTasks(execution, "multi-a", Constants.Value.REJECTED);

    verify(execution).setVariable(Constants.Variable.getMultipleApprovalAssigneeListVariableName("multi-a"), Collections.emptyList());
    verify(execution).setVariable("multi-a" + Constants.Variable.MULTIPLE_APPROVAL_COMPLETED_SUFFIX, true);
    verify(execution).setVariable("multi-a" + Constants.Variable.APPROVED_SUFFIX, true);
  }

  @Test
  void executeNoCandidateModeDoesNotCrashWhenRepositoryServiceMissing() {
    DelegateExecution execution = mock(DelegateExecution.class);
    ServiceTask serviceTask = new ServiceTask();
    serviceTask.setId("multi-a_reset_multiple_approval_service_task");
    serviceTask.setCustomProperties(Arrays.asList(
        customProperty(Constants.Property.MULTIPLE_APPROVAL_TASK_ID, "multi-a"),
        customProperty(Constants.Property.PERCENTAGE, "100"),
        customProperty(Constants.Property.IS_SEQUENTIAL, "false")
    ));
    BpmnUtil.saveRejectAction(serviceTask, Constants.Value.REJECTED);

    when(execution.getCurrentFlowElement()).thenReturn(serviceTask);
    when(execution.getProcessDefinitionId()).thenReturn("process-definition-child");
    when(execution.getVariable(Constants.Variable.USER_SELECTED_ASSIGNEES, Map.class)).thenReturn(Collections.emptyMap());
    when(execution.getVariable(Constants.Variable.PROCESS_STARTER)).thenReturn("starter-1");

    assertDoesNotThrow(() -> newDelegate(null).execute(execution));
  }

  @Test
  void executeExplicitEmptySelectedAssigneesUsesNoCandidateModeInsteadOfConfiguredCandidates() {
    RepositoryService repositoryService = mockRepositoryService(
        "process-definition-child",
        "child-smoke-flow",
        bpmnModelWithConfiguredMultipleApproval("approved"));
    DelegateExecution execution = mock(DelegateExecution.class);
    ServiceTask serviceTask = new ServiceTask();
    HashMap<String, List<String>> selected = new HashMap<String, List<String>>();

    serviceTask.setId("multi-a_reset_multiple_approval_service_task");
    serviceTask.setCustomProperties(Arrays.asList(
        customProperty(Constants.Property.MULTIPLE_APPROVAL_TASK_ID, "multi-a"),
        customProperty(Constants.Property.PERCENTAGE, "100"),
        customProperty(Constants.Property.IS_SEQUENTIAL, "false")
    ));
    BpmnUtil.saveRejectAction(serviceTask, Constants.Value.REJECTED);
    selected.put("multi-a", Collections.<String>emptyList());

    when(execution.getCurrentFlowElement()).thenReturn(serviceTask);
    when(execution.getProcessDefinitionId()).thenReturn("process-definition-child");
    when(execution.getVariable(Constants.Variable.USER_SELECTED_ASSIGNEES, Map.class)).thenReturn(selected);
    when(execution.getVariable(Constants.Variable.PROCESS_STARTER)).thenReturn("starter-1");

    newDelegate(repositoryService).execute(execution);

    verify(execution).setVariable(Constants.Variable.getMultipleApprovalAssigneeListVariableName("multi-a"), Collections.emptyList());
    verify(execution).setVariable("multi-a" + Constants.Variable.MULTIPLE_APPROVAL_COMPLETED_SUFFIX, true);
    verify(execution).setVariable("multi-a" + Constants.Variable.APPROVED_SUFFIX, true);
  }

  private InitializeMultipleApprovalServiceTask newDelegate(RepositoryService repositoryService) {
    InitializeMultipleApprovalServiceTask delegate = new InitializeMultipleApprovalServiceTask();
    setField(delegate, "repositoryService", repositoryService);
    return delegate;
  }

  private RepositoryService mockRepositoryService(String processDefinitionId, String flowKey, BpmnModel bpmnModel) {
    RepositoryService repositoryService = mock(RepositoryService.class);
    ProcessDefinition processDefinition = mock(ProcessDefinition.class);

    when(processDefinition.getKey()).thenReturn(flowKey);
    when(repositoryService.getProcessDefinition(processDefinitionId)).thenReturn(processDefinition);
    when(repositoryService.getBpmnModel(processDefinitionId)).thenReturn(bpmnModel);
    return repositoryService;
  }

  private CustomProperty customProperty(String name, String value) {
    CustomProperty property = new CustomProperty();
    property.setName(name);
    property.setSimpleValue(value);
    return property;
  }

  private BpmnModel bpmnModelWithConfiguredMultipleApproval(String noCandidateMode) {
    BpmnModel model = new BpmnModel();
    Process process = new Process();
    UserTask userTask = new UserTask();
    UserCandidate candidate = new UserCandidate();

    candidate.setUsers(Collections.singletonList("fallback-user"));
    userTask.setId("multi-a");
    BpmnUtil.saveCandidates(userTask, Collections.<Candidate>singletonList(candidate));
    process.setId("child-smoke-flow");
    process.addFlowElement(userTask);
    BpmnUtil.saveFlowDefinitionRawJson(process, minimalMultipleApprovalJson(noCandidateMode));
    model.addProcess(process);
    return model;
  }

  private String minimalMultipleApprovalJson(String noCandidateMode) {
    return "{"
        + "\"formatVersion\":2,"
        + "\"id\":\"child-smoke-flow\","
        + "\"name\":\"多人审批\","
        + "\"formType\":\"default\","
        + "\"nodes\":["
        + "{\"type\":\"发起节点\",\"name\":\"发起节点\",\"id\":\"start-a\",\"permission\":[]},"
        + "{\"type\":\"审批节点\",\"name\":\"多人审批\",\"id\":\"multi-a\",\"subType\":\"会签\","
        + "\"candidates\":[],\"permission\":[],\"multipleMode\":\"并行\",\"percentage\":100,"
        + "\"rejectAction\":\"reject\",\"noCandidateMode\":\"" + noCandidateMode + "\"}"
        + "],"
        + "\"edges\":[{\"id\":\"e1\",\"type\":\"SEQUENCE\",\"sourceId\":\"start-a\",\"targetId\":\"multi-a\"}]"
        + "}";
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
}
