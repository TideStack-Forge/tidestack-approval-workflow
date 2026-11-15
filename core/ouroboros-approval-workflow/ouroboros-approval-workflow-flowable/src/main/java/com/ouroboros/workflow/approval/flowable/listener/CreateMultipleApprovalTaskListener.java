package com.ouroboros.workflow.approval.flowable.listener;

import java.util.Collections;
import java.util.List;

import org.flowable.engine.RepositoryService;
import org.flowable.task.service.delegate.DelegateTask;
import org.flowable.task.service.delegate.TaskListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ouroboros.util.Dates;
import com.ouroboros.workflow.approval.event.TaskEventDispatcher;
import com.ouroboros.workflow.approval.flowable.transpiler.Constants;
import com.ouroboros.workflow.approval.flowable.util.BpmnUtil;
import com.ouroboros.workflow.approval.model.TaskState;
import com.ouroboros.workflow.approval.model.events.FlowTaskEvent;

@Component(CreateMultipleApprovalTaskListener.BEAN_NAME)
public class CreateMultipleApprovalTaskListener implements TaskListener {
  public static final String BEAN_NAME = "createMultipleApprovalTaskListener";
  @Autowired
  private RepositoryService repositoryService;
  @Autowired
  private TaskEventDispatcher taskEventDispatcher;

  @Override
  public void notify(DelegateTask delegateTask) {
    var taskDefinitionKey = delegateTask.getTaskDefinitionKey();
    var session = delegateTask.getVariable(Constants.Variable.getMultipleApprovalSessionVariableName(taskDefinitionKey));
    delegateTask.setVariableLocal(Constants.Variable.MULTIPLE_APPROVAL_SESSION, session);
    dispatchEvent(delegateTask);
  }

  private void dispatchEvent(DelegateTask task) {
    var candidates = BpmnUtil.readStringListVariable(
        task.getVariable(Constants.Variable.getMultipleApprovalAssigneeListVariableName(
            task.getTaskDefinitionKey())),
        Constants.Variable.getMultipleApprovalAssigneeListVariableName(task.getTaskDefinitionKey()));
    var event = new FlowTaskEvent();
    event.setInstanceId(task.getProcessInstanceId());
    event.setTaskId(task.getId());
    event.setTaskName(task.getName());
    event.setCandidateUsers(candidates);
    event.setAssignee(task.getAssignee());
    event.setFlowKey(BpmnUtil.resolveFlowKey(repositoryService, task));
    event.setNodeId(task.getTaskDefinitionKey());
    event.setState(TaskState.CREATED);
    event.setNotification(BpmnUtil.readNotification(repositoryService, task.getProcessDefinitionId()));
    if (task.getCreateTime() == null) {
      event.setEventTime(Dates.now());
    } else {
      event.setEventTime(task.getCreateTime());
    }
    event.setFlowNodeSupplier(BpmnUtil.getFlowNodeSupplier(
        repositoryService,
        task.getProcessDefinitionId(),
        task.getTaskDefinitionKey()));
    taskEventDispatcher.dispatchEvent(event);
  }
}
