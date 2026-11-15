package com.ouroboros.workflow.approval.flowable.listener;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;

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
import com.ouroboros.workflow.approval.flowable.transpiler.Constants;
import com.ouroboros.workflow.approval.flowable.util.BpmnUtil;
import com.ouroboros.workflow.approval.model.TaskState;
import com.ouroboros.workflow.approval.model.events.FlowTaskEvent;

@Component(MultipleApprovalCompletedTaskListener.BEAN_NAME)
public class MultipleApprovalCompletedTaskListener implements TaskListener {
  public static final String BEAN_NAME = "multipleApprovalCompletedTaskListener";
  @Autowired
  private TaskService taskService;
  @Autowired
  private RepositoryService repositoryService;
  @Autowired
  private TaskEventDispatcher taskEventDispatcher;

  @Override
  public void notify(DelegateTask task) {
    var taskDefinitionKey = task.getTaskDefinitionKey();
    if (StringUtils.isBlank(taskDefinitionKey)) {
      return;
    }

    var session = Optional.ofNullable(task.getVariableLocal(Constants.Variable.MULTIPLE_APPROVAL_SESSION, String.class))
        .orElseGet(() -> task.getVariable(
            Constants.Variable.getMultipleApprovalSessionVariableName(taskDefinitionKey),
            String.class));
    var nrOfApproved = readInt(task, taskDefinitionKey + Constants.Variable.NR_OF_APPROVED_SUFFIX);
    var nrOfRejected = readInt(task, taskDefinitionKey + Constants.Variable.NR_OF_REJECTED_SUFFIX);
    var percentage = readInt(task, taskDefinitionKey + Constants.Variable.MULTIPLE_APPROVED_PERCENTAGE_SUFFIX);
    var nrOfInstances = Math.max(readInt(task, "nrOfInstances"), 1);
    var isSequential = readBoolean(task, taskDefinitionKey + Constants.Variable.MULTIPLE_APPROVAL_IS_SEQUENTIAL_SUFFIX);

    var action = task.getVariableLocal(Constants.Variable.LOCAL_APPROVAL_ACTION, String.class);
    var currentTaskApproved = Constants.Value.APPROVED.equalsIgnoreCase(action);
    task.setVariableLocal(Constants.Variable.TASK_APPROVED, currentTaskApproved);

    if (currentTaskApproved) {
      nrOfApproved++;
    } else {
      nrOfRejected++;
    }

    var requiredApprovals = (int) Math.ceil((double) nrOfInstances * percentage / 100D);
    var isFinalApproved = nrOfApproved >= requiredApprovals;
    var remained = nrOfInstances - nrOfApproved - nrOfRejected;
    var requiredLeft = requiredApprovals - nrOfApproved;
    var canComplete = nrOfInstances == nrOfApproved + nrOfRejected
        || (isSequential && !currentTaskApproved)
        || isFinalApproved
        || requiredLeft > remained;

    task.setVariable(taskDefinitionKey + Constants.Variable.NR_OF_APPROVED_SUFFIX, nrOfApproved);
    task.setVariable(taskDefinitionKey + Constants.Variable.NR_OF_REJECTED_SUFFIX, nrOfRejected);
    task.setVariable(taskDefinitionKey + Constants.Variable.APPROVED_SUFFIX, isFinalApproved);
    task.setVariable(taskDefinitionKey + Constants.Variable.MULTIPLE_APPROVAL_COMPLETED_SUFFIX, canComplete);
    task.setVariable(Constants.Variable.PROCESS_LAST_FINISHED_TASK_ID, task.getId());
    if (canComplete) {
      task.setVariable(taskDefinitionKey + Constants.Variable.MULTIPLE_APPROVAL_FINAL_TASK_ID_SUFFIX, task.getId());
    }

    if (StringUtils.isNotBlank(session)) {
      task.setVariable(
          Constants.Variable.MULTIPLE_APPROVAL_SESSION + session + Constants.Variable.MULTIPLE_APPROVAL_COMPLETED_SUFFIX,
          canComplete
      );
      task.setVariable(
          Constants.Variable.MULTIPLE_APPROVAL_SESSION + session + Constants.Variable.APPROVED_SUFFIX,
          isFinalApproved
      );
    }

    if (canComplete) {
      return;
    }
    dispatchProcessingEvent(task, action, currentTaskApproved);
  }

  private int readInt(DelegateTask task, String variableName) {
    var value = task.getVariable(variableName, Integer.class);
    if (value instanceof Number) {
      return ((Number) value).intValue();
    }
    return 0;
  }

  private boolean readBoolean(DelegateTask task, String variableName) {
    return Boolean.TRUE.equals(task.getVariable(variableName, Boolean.class));
  }

  private List<String> getCandidateUserList(DelegateTask task) {
    var candidateUsers = BpmnUtil.readStringListVariable(
        task.getVariable(Constants.Variable.getMultipleApprovalAssigneeListVariableName(
            task.getTaskDefinitionKey())),
        Constants.Variable.getMultipleApprovalAssigneeListVariableName(task.getTaskDefinitionKey()));
    return candidateUsers == null ? Collections.<String>emptyList() : candidateUsers;
  }

  private void dispatchProcessingEvent(DelegateTask task, String actionName, boolean approved) {
    var event = new FlowTaskEvent();
    event.setTaskId(task.getId());
    event.setTaskName(task.getName());
    event.setCandidateUsers(getCandidateUserList(task));
    event.setAssignee(task.getAssignee());
    event.setComment(readComment(task, approved));
    event.setFlowKey(BpmnUtil.resolveFlowKey(repositoryService, task));
    event.setState(TaskState.PROCESSING);
    event.setNodeId(task.getTaskDefinitionKey());
    event.setInstanceId(task.getProcessInstanceId());
    event.setEventTime(Dates.now());
    event.setActionName(actionName);
    event.setNotification(BpmnUtil.readNotification(repositoryService, task.getProcessDefinitionId()));
    event.setFlowNodeSupplier(BpmnUtil.getFlowNodeSupplier(
        repositoryService,
        task.getProcessDefinitionId(),
        task.getTaskDefinitionKey()));
    taskEventDispatcher.dispatchEvent(event);
  }

  private String readComment(DelegateTask task, boolean approved) {
    if (approved) {
      return null;
    }
    if (taskService == null) {
      return null;
    }

    return taskService.getTaskComments(task.getId()).stream()
        .filter(comment -> comment != null)
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
