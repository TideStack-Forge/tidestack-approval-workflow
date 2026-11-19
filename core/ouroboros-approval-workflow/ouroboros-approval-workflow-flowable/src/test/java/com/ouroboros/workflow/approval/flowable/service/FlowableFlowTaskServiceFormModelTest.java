package com.ouroboros.workflow.approval.flowable.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
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

import io.vavr.control.Try;

import com.ouroboros.uischema.schema.SchemaNode;
import com.ouroboros.util.Json;
import com.ouroboros.workflow.approval.FlowFormService;
import com.ouroboros.workflow.approval.FlowFormServiceUnavailableException;
import com.ouroboros.workflow.approval.FlowTaskAction;
import com.ouroboros.workflow.approval.definition.FlowNode;
import com.ouroboros.workflow.approval.definition.NodeFormFieldPermission;
import com.ouroboros.workflow.approval.definition.nodes.ApprovalNode;
import com.ouroboros.workflow.approval.definition.nodes.HandleNode;
import com.ouroboros.workflow.approval.definition.nodes.SingleApprovalNode;
import com.ouroboros.workflow.approval.flowable.transpiler.Constants;
import com.ouroboros.workflow.approval.flowable.util.BpmnUtil;
import com.ouroboros.workflow.approval.event.TaskEventDispatcher;

class FlowableFlowTaskServiceFormModelTest {
  private abstract static class TestTaskAction implements FlowTaskAction<Task> {
  }

  @Test
  void doActionSubmitsTaskFormThroughMatchingFlowFormService() {
    TaskService taskService = mock(TaskService.class);
    RepositoryService repositoryService = mock(RepositoryService.class);
    FlowFormService flowFormService = mock(FlowFormService.class);
    TestTaskAction action = mock(TestTaskAction.class);
    FlowableFlowTaskService service = new FlowableFlowTaskService(taskService, Collections.singletonList(action));
    service.repositoryService = repositoryService;
    service.taskEventDispatcher = new TaskEventDispatcher();
    setFieldIfPresent(service, "flowFormServices", Collections.singletonList(flowFormService));

    TaskQuery taskQuery = mock(TaskQuery.class);
    Task task = mock(Task.class);
    IdentityLink candidate = mock(IdentityLink.class);
    Map<String, Object> formData = Collections.singletonMap("amount", 1200);
    List<NodeFormFieldPermission> permissions = Collections.singletonList(permission("amount", "金额"));

    when(taskService.createTaskQuery()).thenReturn(taskQuery);
    when(taskQuery.taskId("task-1")).thenReturn(taskQuery);
    when(taskQuery.singleResult()).thenReturn(task);
    when(taskService.getIdentityLinksForTask("task-1")).thenReturn(Collections.singletonList(candidate));
    when(candidate.getUserId()).thenReturn("user-a");
    when(task.getId()).thenReturn("task-1");
    when(task.getAssignee()).thenReturn("user-a");
    when(task.getProcessInstanceId()).thenReturn("instance-1");
    when(task.getProcessDefinitionId()).thenReturn("process-definition-1");
    when(task.getTaskDefinitionKey()).thenReturn("approve-a");
    when(task.getFormKey()).thenReturn("purchase-form");
    configureDeployedNode(repositoryService, approvalNode("approve-a", permissions, Arrays.asList("reject", "rollback")));
    when(taskService.getVariable("task-1", Constants.TASK_FORM_TYPE_KEY, String.class)).thenReturn("default");
    when(taskService.getVariable("task-1", Constants.TASK_FORM_ID_KEY, String.class)).thenReturn("form-1");
    when(flowFormService.getType()).thenReturn("default");
    when(flowFormService.submitTaskForm(
        eq("instance-1"),
        eq("task-1"),
        eq("purchase-form"),
        eq("form-1"),
        eq("user-a"),
        anyList(),
        eq(formData)))
        .thenReturn(Try.success(null));
    when(action.supportTask(task)).thenReturn(true);
    when(action.actionName()).thenReturn("approve");
    when(action.doAction(task)).thenReturn(Try.success(null));

    var result = service.doAction("task-1", "approve", "user-a", null, formData, null);

    assertTrue(result.isSuccess());
    verify(flowFormService).submitTaskForm(
        eq("instance-1"),
        eq("task-1"),
        eq("purchase-form"),
        eq("form-1"),
        eq("user-a"),
        anyList(),
        eq(formData));
    verify(taskService).setVariableLocal("task-1", Constants.TASK_FORM_DATA_KEY, formData);
  }

  @Test
  void getTaskFormDelegatesToMatchingFlowFormService() {
    TaskService taskService = mock(TaskService.class);
    RepositoryService repositoryService = mock(RepositoryService.class);
    FlowFormService flowFormService = mock(FlowFormService.class);
    FlowableFlowTaskService service = new FlowableFlowTaskService(taskService, Collections.emptyList());
    service.repositoryService = repositoryService;
    service.taskEventDispatcher = new TaskEventDispatcher();
    setFieldIfPresent(service, "flowFormServices", Collections.singletonList(flowFormService));

    TaskQuery taskQuery = mock(TaskQuery.class);
    Task task = mock(Task.class);
    SchemaNode schemaNode = new SchemaNode();
    schemaNode.setType("form");
    List<NodeFormFieldPermission> permissions = Collections.singletonList(permission("amount", "金额"));
    List<String> actions = Arrays.asList("reject", "rollback");

    when(taskService.createTaskQuery()).thenReturn(taskQuery);
    when(taskQuery.taskId("task-1")).thenReturn(taskQuery);
    when(taskQuery.singleResult()).thenReturn(task);
    when(task.getProcessInstanceId()).thenReturn("instance-1");
    when(task.getProcessDefinitionId()).thenReturn("process-definition-1");
    when(task.getTaskDefinitionKey()).thenReturn("approve-a");
    when(task.getFormKey()).thenReturn("purchase-form");
    configureDeployedNode(repositoryService, approvalNode("approve-a", permissions, actions));
    when(taskService.getVariable("task-1", Constants.TASK_FORM_TYPE_KEY, String.class)).thenReturn("default");
    when(taskService.getVariable("task-1", Constants.TASK_FORM_ID_KEY, String.class)).thenReturn("form-1");
    when(flowFormService.getType()).thenReturn("default");
    when(flowFormService.getTaskFormSchema(
        eq("instance-1"),
        eq("task-1"),
        eq("purchase-form"),
        eq("form-1"),
        anyList(),
        eq(actions)))
        .thenReturn(Try.success(schemaNode));

    var result = service.getTaskForm("task-1");

    assertTrue(result.isSuccess());
    assertEquals(schemaNode, result.get());
  }

  @Test
  void getTaskFormExposesCompleteActionForHandleNode() {
    TaskService taskService = mock(TaskService.class);
    RepositoryService repositoryService = mock(RepositoryService.class);
    FlowFormService flowFormService = mock(FlowFormService.class);
    FlowableFlowTaskService service = new FlowableFlowTaskService(taskService, Collections.emptyList());
    service.repositoryService = repositoryService;
    service.taskEventDispatcher = new TaskEventDispatcher();
    setFieldIfPresent(service, "flowFormServices", Collections.singletonList(flowFormService));

    TaskQuery taskQuery = mock(TaskQuery.class);
    Task task = mock(Task.class);
    SchemaNode schemaNode = new SchemaNode();
    schemaNode.setType("form");
    List<NodeFormFieldPermission> permissions = Collections.singletonList(permission("amount", "金额"));

    when(taskService.createTaskQuery()).thenReturn(taskQuery);
    when(taskQuery.taskId("task-1")).thenReturn(taskQuery);
    when(taskQuery.singleResult()).thenReturn(task);
    when(task.getProcessInstanceId()).thenReturn("instance-1");
    when(task.getProcessDefinitionId()).thenReturn("process-definition-1");
    when(task.getTaskDefinitionKey()).thenReturn("handle-a");
    when(task.getFormKey()).thenReturn("purchase-form");
    configureDeployedNode(repositoryService, handleNode("handle-a", permissions));
    when(taskService.getVariable("task-1", Constants.TASK_FORM_TYPE_KEY, String.class)).thenReturn("default");
    when(taskService.getVariable("task-1", Constants.TASK_FORM_ID_KEY, String.class)).thenReturn("form-1");
    when(flowFormService.getType()).thenReturn("default");
    when(flowFormService.getTaskFormSchema(
        eq("instance-1"),
        eq("task-1"),
        eq("purchase-form"),
        eq("form-1"),
        anyList(),
        eq(Collections.singletonList(Constants.Value.COMPLETE))))
        .thenReturn(Try.success(schemaNode));

    var result = service.getTaskForm("task-1");

    assertTrue(result.isSuccess());
    assertEquals(schemaNode, result.get());
    verify(flowFormService).getTaskFormSchema(
        eq("instance-1"),
        eq("task-1"),
        eq("purchase-form"),
        eq("form-1"),
        anyList(),
        eq(Collections.singletonList(Constants.Value.COMPLETE)));
  }

  @Test
  void getTaskFormDefaultsBlankFormTypeToDefaultService() {
    TaskService taskService = mock(TaskService.class);
    RepositoryService repositoryService = mock(RepositoryService.class);
    FlowFormService flowFormService = mock(FlowFormService.class);
    FlowableFlowTaskService service = new FlowableFlowTaskService(taskService, Collections.emptyList());
    service.repositoryService = repositoryService;
    service.taskEventDispatcher = new TaskEventDispatcher();
    setFieldIfPresent(service, "flowFormServices", Collections.singletonList(flowFormService));

    TaskQuery taskQuery = mock(TaskQuery.class);
    Task task = mock(Task.class);
    SchemaNode schemaNode = new SchemaNode();
    schemaNode.setType("form");
    List<NodeFormFieldPermission> permissions = Collections.singletonList(permission("amount", "金额"));
    List<String> actions = Arrays.asList("reject", "rollback");

    when(taskService.createTaskQuery()).thenReturn(taskQuery);
    when(taskQuery.taskId("task-1")).thenReturn(taskQuery);
    when(taskQuery.singleResult()).thenReturn(task);
    when(task.getProcessInstanceId()).thenReturn("instance-1");
    when(task.getProcessDefinitionId()).thenReturn("process-definition-1");
    when(task.getTaskDefinitionKey()).thenReturn("approve-a");
    when(task.getFormKey()).thenReturn("purchase-form");
    configureDeployedNode(repositoryService, approvalNode("approve-a", permissions, actions));
    when(taskService.getVariable("task-1", Constants.TASK_FORM_TYPE_KEY, String.class)).thenReturn("");
    when(taskService.getVariable("task-1", Constants.TASK_FORM_ID_KEY, String.class)).thenReturn("form-1");
    when(flowFormService.getType()).thenReturn("default");
    when(flowFormService.getTaskFormSchema(
        eq("instance-1"),
        eq("task-1"),
        eq("purchase-form"),
        eq("form-1"),
        anyList(),
        eq(actions)))
        .thenReturn(Try.success(schemaNode));

    var result = service.getTaskForm("task-1");

    assertTrue(result.isSuccess());
    assertEquals(schemaNode, result.get());
  }

  @Test
  void getTaskFormMatchesFlowFormServiceIgnoringCase() {
    TaskService taskService = mock(TaskService.class);
    RepositoryService repositoryService = mock(RepositoryService.class);
    FlowFormService flowFormService = mock(FlowFormService.class);
    FlowableFlowTaskService service = new FlowableFlowTaskService(taskService, Collections.emptyList());
    service.repositoryService = repositoryService;
    service.taskEventDispatcher = new TaskEventDispatcher();
    setFieldIfPresent(service, "flowFormServices", Collections.singletonList(flowFormService));

    TaskQuery taskQuery = mock(TaskQuery.class);
    Task task = mock(Task.class);
    SchemaNode schemaNode = new SchemaNode();
    schemaNode.setType("form");
    List<NodeFormFieldPermission> permissions = Collections.singletonList(permission("amount", "金额"));
    List<String> actions = Arrays.asList("reject", "rollback");

    when(taskService.createTaskQuery()).thenReturn(taskQuery);
    when(taskQuery.taskId("task-1")).thenReturn(taskQuery);
    when(taskQuery.singleResult()).thenReturn(task);
    when(task.getProcessInstanceId()).thenReturn("instance-1");
    when(task.getProcessDefinitionId()).thenReturn("process-definition-1");
    when(task.getTaskDefinitionKey()).thenReturn("approve-a");
    when(task.getFormKey()).thenReturn("purchase-form");
    configureDeployedNode(repositoryService, approvalNode("approve-a", permissions, actions));
    when(taskService.getVariable("task-1", Constants.TASK_FORM_TYPE_KEY, String.class)).thenReturn("default");
    when(taskService.getVariable("task-1", Constants.TASK_FORM_ID_KEY, String.class)).thenReturn("form-1");
    when(flowFormService.getType()).thenReturn("DEFAULT");
    when(flowFormService.getTaskFormSchema(
        eq("instance-1"),
        eq("task-1"),
        eq("purchase-form"),
        eq("form-1"),
        anyList(),
        eq(actions)))
        .thenReturn(Try.success(schemaNode));

    var result = service.getTaskForm("task-1");

    assertTrue(result.isSuccess());
    assertEquals(schemaNode, result.get());
  }

  @Test
  void getTaskFormFailsWhenTaskMissing() {
    TaskService taskService = mock(TaskService.class);
    FlowableFlowTaskService service = new FlowableFlowTaskService(taskService, Collections.emptyList());

    TaskQuery taskQuery = mock(TaskQuery.class);
    when(taskService.createTaskQuery()).thenReturn(taskQuery);
    when(taskQuery.taskId("task-missing")).thenReturn(taskQuery);
    when(taskQuery.singleResult()).thenReturn(null);

    var result = service.getTaskForm("task-missing");

    assertTrue(result.isFailure());
    assertEquals("任务不存在", result.getCause().getMessage());
  }

  @Test
  void getTaskFormFailsWhenTaskDoesNotDefineForm() {
    TaskService taskService = mock(TaskService.class);
    FlowableFlowTaskService service = new FlowableFlowTaskService(taskService, Collections.emptyList());

    TaskQuery taskQuery = mock(TaskQuery.class);
    Task task = mock(Task.class);
    when(taskService.createTaskQuery()).thenReturn(taskQuery);
    when(taskQuery.taskId("task-1")).thenReturn(taskQuery);
    when(taskQuery.singleResult()).thenReturn(task);
    when(task.getFormKey()).thenReturn(null);

    var result = service.getTaskForm("task-1");

    assertTrue(result.isFailure());
    assertEquals("任务表单不存在", result.getCause().getMessage());
  }

  @Test
  void getTaskFormFailsWhenNoMatchingFormServiceExists() {
    TaskService taskService = mock(TaskService.class);
    RepositoryService repositoryService = mock(RepositoryService.class);
    FlowableFlowTaskService service = new FlowableFlowTaskService(taskService, Collections.emptyList());
    service.repositoryService = repositoryService;
    service.taskEventDispatcher = new TaskEventDispatcher();
    setFieldIfPresent(service, "flowFormServices", Collections.emptyList());

    TaskQuery taskQuery = mock(TaskQuery.class);
    Task task = mock(Task.class);

    when(taskService.createTaskQuery()).thenReturn(taskQuery);
    when(taskQuery.taskId("task-1")).thenReturn(taskQuery);
    when(taskQuery.singleResult()).thenReturn(task);
    when(task.getProcessDefinitionId()).thenReturn("process-definition-1");
    when(task.getTaskDefinitionKey()).thenReturn("approve-a");
    when(task.getProcessInstanceId()).thenReturn("instance-1");
    when(task.getFormKey()).thenReturn("purchase-form");
    when(taskService.getVariable("task-1", Constants.TASK_FORM_TYPE_KEY, String.class)).thenReturn("default");
    when(taskService.getVariable("task-1", Constants.TASK_FORM_ID_KEY, String.class)).thenReturn("form-1");
    configureDeployedNode(repositoryService, approvalNode("approve-a", Collections.emptyList(), Collections.emptyList()));

    var result = service.getTaskForm("task-1");

    assertTrue(result.isFailure());
    assertInstanceOf(FlowFormServiceUnavailableException.class, result.getCause());
    assertEquals("当前表单未接入通用审批页面，请通过业务页面操作（default）", result.getCause().getMessage());
  }

  @Test
  void getTaskFormFailsWhenSchemaIsEmpty() {
    TaskService taskService = mock(TaskService.class);
    RepositoryService repositoryService = mock(RepositoryService.class);
    FlowFormService flowFormService = mock(FlowFormService.class);
    FlowableFlowTaskService service = new FlowableFlowTaskService(taskService, Collections.emptyList());
    service.repositoryService = repositoryService;
    service.taskEventDispatcher = new TaskEventDispatcher();
    setFieldIfPresent(service, "flowFormServices", Collections.singletonList(flowFormService));

    TaskQuery taskQuery = mock(TaskQuery.class);
    Task task = mock(Task.class);

    when(taskService.createTaskQuery()).thenReturn(taskQuery);
    when(taskQuery.taskId("task-1")).thenReturn(taskQuery);
    when(taskQuery.singleResult()).thenReturn(task);
    when(task.getProcessDefinitionId()).thenReturn("process-definition-1");
    when(task.getTaskDefinitionKey()).thenReturn("approve-a");
    when(task.getProcessInstanceId()).thenReturn("instance-1");
    when(task.getFormKey()).thenReturn("purchase-form");
    when(taskService.getVariable("task-1", Constants.TASK_FORM_TYPE_KEY, String.class)).thenReturn("default");
    when(taskService.getVariable("task-1", Constants.TASK_FORM_ID_KEY, String.class)).thenReturn("form-1");
    configureDeployedNode(repositoryService, approvalNode("approve-a", Collections.emptyList(), Collections.emptyList()));
    when(flowFormService.getType()).thenReturn("default");
    when(flowFormService.getTaskFormSchema(eq("instance-1"), eq("task-1"), eq("purchase-form"), eq("form-1"), eq(Collections.emptyList()), eq(Collections.emptyList())))
        .thenReturn(Try.success(null));

    var result = service.getTaskForm("task-1");

    assertTrue(result.isFailure());
    assertEquals("表单不存在", result.getCause().getMessage());
  }

  private NodeFormFieldPermission permission(String name, String label) {
    NodeFormFieldPermission permission = new NodeFormFieldPermission();
    permission.setName(name);
    permission.setLabel(label);
    permission.setVisible(true);
    permission.setWritable(true);
    permission.setRequired(true);
    return permission;
  }

  private SingleApprovalNode approvalNode(String id, List<NodeFormFieldPermission> permissions, List<String> actions) {
    SingleApprovalNode approvalNode = new SingleApprovalNode();
    approvalNode.setId(id);
    approvalNode.setPermission(permissions);
    approvalNode.setActions(actions);
    return approvalNode;
  }

  private HandleNode handleNode(String id, List<NodeFormFieldPermission> permissions) {
    HandleNode handleNode = new HandleNode();
    handleNode.setId(id);
    handleNode.setPermission(permissions);
    return handleNode;
  }

  private void configureDeployedNode(RepositoryService repositoryService, FlowNode flowNode) {
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
    return "{"
        + "\"type\":\"审批节点\","
        + "\"id\":" + Json.toJsonString(flowNode.getId()) + ","
        + "\"name\":\"审批节点\","
        + "\"subType\":\"单签\","
        + "\"candidates\":[],"
        + "\"permission\":" + permissionJson + ","
        + "\"actions\":" + actionsJson
        + "}";
  }

  private void setFieldIfPresent(Object target, String fieldName, Object value) {
    try {
      Field field = target.getClass().getDeclaredField(fieldName);
      field.setAccessible(true);
      field.set(target, value);
    } catch (ReflectiveOperationException ignored) {
    }
  }
}
