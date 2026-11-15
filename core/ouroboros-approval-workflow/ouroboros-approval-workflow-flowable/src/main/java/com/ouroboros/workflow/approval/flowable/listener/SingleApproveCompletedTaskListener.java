package com.ouroboros.workflow.approval.flowable.listener;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.TaskService;
import org.flowable.engine.task.Comment;
import org.flowable.task.service.delegate.DelegateTask;
import org.flowable.task.service.delegate.TaskListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ouroboros.util.Dates;
import com.ouroboros.workflow.approval.event.TaskEventDispatcher;
import com.ouroboros.workflow.approval.flowable.service.FlowableAddSignTaskManager;
import com.ouroboros.workflow.approval.flowable.transpiler.Constants;
import com.ouroboros.workflow.approval.flowable.util.BpmnUtil;
import com.ouroboros.workflow.approval.model.TaskState;
import com.ouroboros.workflow.approval.model.events.FlowTaskEvent;

@Component(SingleApproveCompletedTaskListener.BEAN_NAME)
public class SingleApproveCompletedTaskListener implements TaskListener {
  public static final String BEAN_NAME = "singleApproveCompletedTaskListener";
  @Autowired
  private TaskService taskService;
  @Autowired
  private RepositoryService repositoryService;
  @Autowired
  private TaskEventDispatcher taskEventDispatcher;
  @Autowired
  private FlowableAddSignTaskManager addSignTaskManager;

  @Override
  public void notify(DelegateTask task) {
    var taskDefinitionKey = task.getTaskDefinitionKey();
    if (StringUtils.isBlank(taskDefinitionKey)) {
      return;
    }

    var action = task.getVariableLocal(Constants.Variable.LOCAL_APPROVAL_ACTION, String.class);
    var approved = Constants.Value.APPROVED.equalsIgnoreCase(action);

    task.setVariableLocal(Constants.Variable.TASK_APPROVED, approved);
    task.setVariable(Constants.Variable.PROCESS_LAST_FINISHED_TASK_ID, task.getId());
    task.setVariable(taskDefinitionKey + Constants.Variable.APPROVAL_ACTION_SUFFIX, action);
    task.setVariable(taskDefinitionKey + Constants.Variable.APPROVED_SUFFIX, approved);
    activateAfterCurrentAddSign(task);
    dispatchEvent(task, action);
  }

  private void activateAfterCurrentAddSign(DelegateTask task) {
    if (addSignTaskManager == null) {
      return;
    }
    if (!Boolean.TRUE.equals(task.getVariableLocal(Constants.Variable.ADD_SIGN_AFTER_CURRENT_PENDING, Boolean.class))) {
      return;
    }

    var groupId = task.getVariableLocal(Constants.Variable.ADD_SIGN_GROUP_ID, String.class);
    if (StringUtils.isBlank(groupId)) {
      return;
    }

    addSignTaskManager.activateAfterCurrentGroup(task.getProcessInstanceId(), groupId);
    task.removeVariableLocal(Constants.Variable.ADD_SIGN_AFTER_CURRENT_PENDING);
  }

  void dispatchEvent(DelegateTask task, String actionName) {
    var approved = Constants.Value.APPROVED.equalsIgnoreCase(actionName);
    var event = new FlowTaskEvent();

    event.setTaskId(task.getId());
    event.setTaskName(task.getName());
    event.setCandidateUsers(getCandidateUsers(task));
    event.setAssignee(task.getAssignee());
    event.setComment(readComment(task, approved));
    event.setFlowKey(BpmnUtil.resolveFlowKey(repositoryService, task));
    event.setState(approved ? TaskState.APPROVED : TaskState.REJECTED);
    event.setNodeId(task.getTaskDefinitionKey());
    event.setInstanceId(task.getProcessInstanceId());
    event.setEventTime(Dates.now());
    event.setActionName(actionName);
    event.setNotification(BpmnUtil.readNotification(repositoryService, task.getProcessDefinitionId()));
    String trustedSourceFactId = task.getVariableLocal(Constants.TRUSTED_SOURCE_FACT_ID_KEY, String.class);
    if (trustedSourceFactId != null && !trustedSourceFactId.isBlank()) {
      event.setEventFactId(trustedSourceFactId);
    }
    event.setFlowNodeSupplier(BpmnUtil.getFlowNodeSupplier(
        repositoryService,
        task.getProcessDefinitionId(),
        task.getTaskDefinitionKey()));

    taskEventDispatcher.dispatchEvent(event);
  }

  private List<String> getCandidateUsers(DelegateTask task) {
    return task.getCandidates() == null
        ? Collections.<String>emptyList()
        : task.getCandidates().stream()
            .filter(Objects::nonNull)
            .map(link -> link.getUserId())
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
  }

  private String readComment(DelegateTask task, boolean approved) {
    if (approved) {
      return null;
    }

    if (taskService == null) {
      return null;
    }

    return taskService.getTaskComments(task.getId()).stream()
        .filter(Objects::nonNull)
        .max((left, right) -> {
          Date leftTime = left.getTime();
          Date rightTime = right.getTime();
          if (leftTime == null && rightTime == null) {
            return 0;
          }
          if (leftTime == null) {
            return -1;
          }
          if (rightTime == null) {
            return 1;
          }
          return leftTime.compareTo(rightTime);
        })
        .map(Comment::getFullMessage)
        .orElse(null);
  }
}
