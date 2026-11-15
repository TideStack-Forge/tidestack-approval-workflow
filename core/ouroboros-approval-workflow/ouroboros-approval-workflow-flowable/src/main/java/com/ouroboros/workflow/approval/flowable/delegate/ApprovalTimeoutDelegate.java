package com.ouroboros.workflow.approval.flowable.delegate;

import static com.ouroboros.workflow.approval.flowable.util.BpmnUtil.getCandidateUsers;
import static com.ouroboros.workflow.approval.flowable.util.BpmnUtil.getFlowNodeSupplier;
import static com.ouroboros.workflow.approval.flowable.util.BpmnUtil.readCandidates;
import static com.ouroboros.workflow.approval.flowable.util.BpmnUtil.readNotification;
import static com.ouroboros.workflow.approval.flowable.util.BpmnUtil.resolveFlowKey;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.flowable.bpmn.model.FieldExtension;
import org.flowable.bpmn.model.ServiceTask;
import org.flowable.bpmn.model.Task;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.TaskService;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ouroboros.exception.PlatformException;
import com.ouroboros.workflow.approval.TrustedFlowTaskAction;
import com.ouroboros.workflow.approval.TrustedFlowTaskActionService;
import com.ouroboros.workflow.approval.event.TaskEventDispatcher;
import com.ouroboros.workflow.approval.flowable.transpiler.Constants;
import com.ouroboros.workflow.approval.model.TaskState;
import com.ouroboros.workflow.approval.model.events.FlowTaskEvent;

@Component(ApprovalTimeoutDelegate.BEAN_NAME)
public class ApprovalTimeoutDelegate implements JavaDelegate {
  public static final String BEAN_NAME = "approvalTimeoutDelegate";

  @Autowired
  TaskService taskService;
  @Autowired
  RepositoryService repositoryService;
  @Autowired
  TrustedFlowTaskActionService trustedTaskActionService;
  @Autowired
  TaskEventDispatcher taskEventDispatcher;

  @Override
  public void execute(DelegateExecution execution) {
    if (!(execution.getCurrentFlowElement() instanceof ServiceTask)) {
      return;
    }

    ServiceTask serviceTask = (ServiceTask) execution.getCurrentFlowElement();
    String approvalTaskId = readField(serviceTask, Constants.Property.APPROVAL_TASK_ID);
    String timeoutAction = readField(serviceTask, Constants.Property.TIMEOUT_ACTION);
    String transferTo = readField(serviceTask, Constants.Property.TIMEOUT_TRANSFER_TO);
    if (StringUtils.isAnyBlank(approvalTaskId, timeoutAction)) {
      return;
    }

    if (Constants.Value.TIMEOUT_ACTION_REMIND.equals(timeoutAction)) {
      dispatchRemind(execution, approvalTaskId);
      return;
    }

    List<org.flowable.task.api.Task> activeTasks = activeTasks(execution, approvalTaskId);
    if (activeTasks.isEmpty()) {
      return;
    }

    String action = mapAction(timeoutAction);
    Object payload = Constants.Value.TIMEOUT_ACTION_TRANSFER.equals(timeoutAction)
        ? Collections.singletonMap("targetUserId", transferTo)
        : null;
    for (org.flowable.task.api.Task activeTask : activeTasks) {
      var result = trustedTaskActionService.execute(new TrustedFlowTaskAction(
          activeTask.getId(),
          action,
          "timeout: " + timeoutAction,
          null,
          payload,
          "timeout:" + execution.getProcessInstanceId() + ":" + activeTask.getId() + ":" + timeoutAction,
          TrustedFlowTaskAction.Origin.TIMEOUT));
      if (result.isFailure()) {
        throw new PlatformException("执行超时策略失败: " + result.getCause().getMessage(), result.getCause());
      }
    }
  }

  private void dispatchRemind(DelegateExecution execution, String approvalTaskId) {
    List<org.flowable.task.api.Task> activeTasks = activeTasks(execution, approvalTaskId);
    if (activeTasks.isEmpty()) {
      return;
    }

    Task taskDefinition = Optional.ofNullable(repositoryService.getBpmnModel(execution.getProcessDefinitionId()))
        .map(model -> model.getFlowElement(approvalTaskId))
        .filter(Task.class::isInstance)
        .map(Task.class::cast)
        .orElse(null);

    for (org.flowable.task.api.Task activeTask : activeTasks) {
      FlowTaskEvent event = new FlowTaskEvent();
      event.setFlowKey(resolveFlowKey(repositoryService, execution));
      event.setNodeId(approvalTaskId);
      event.setTaskName(activeTask.getName());
      event.setAssignee(activeTask.getAssignee());
      event.setCandidateUsers(getCandidateUsers(
          taskDefinition == null ? Collections.emptyList() : readCandidates(taskDefinition),
          execution.getProcessInstanceId(),
          activeTask.getId(),
          activeTask.getName()));
      event.setInstanceId(execution.getProcessInstanceId());
      event.setTaskId(activeTask.getId());
      event.setState(TaskState.TIMEOUT_REMIND);
      event.setEventTime(LocalDateTime.now());
      event.setNotification(readNotification(repositoryService, execution.getProcessDefinitionId()));
      event.setFlowNodeSupplier(getFlowNodeSupplier(repositoryService, execution.getProcessDefinitionId(), approvalTaskId));
      taskEventDispatcher.dispatchEvent(event);
    }
  }

  private List<org.flowable.task.api.Task> activeTasks(DelegateExecution execution, String approvalTaskId) {
    List<org.flowable.task.api.Task> tasks = taskService.createTaskQuery()
        .processInstanceId(execution.getProcessInstanceId())
        .taskDefinitionKey(approvalTaskId)
        .active()
        .list();
    return tasks == null ? Collections.emptyList() : tasks;
  }

  private String mapAction(String timeoutAction) {
    if (Constants.Value.TIMEOUT_ACTION_AUTO_APPROVE.equals(timeoutAction)) {
      return Constants.Value.APPROVED;
    }
    if (Constants.Value.TIMEOUT_ACTION_AUTO_REJECT.equals(timeoutAction)) {
      return Constants.Value.REJECTED;
    }
    if (Constants.Value.TIMEOUT_ACTION_TRANSFER.equals(timeoutAction)) {
      return Constants.Value.TIMEOUT_ACTION_TRANSFER;
    }
    throw new PlatformException("不支持的超时策略: " + timeoutAction);
  }

  private String readField(ServiceTask serviceTask, String name) {
    if (serviceTask.getFieldExtensions() == null) {
      return null;
    }
    for (FieldExtension fieldExtension : serviceTask.getFieldExtensions()) {
      if (fieldExtension != null && StringUtils.equals(name, fieldExtension.getFieldName())) {
        return fieldExtension.getStringValue();
      }
    }
    return null;
  }
}
