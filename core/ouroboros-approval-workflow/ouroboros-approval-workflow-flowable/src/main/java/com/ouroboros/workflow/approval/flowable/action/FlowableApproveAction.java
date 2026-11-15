package com.ouroboros.workflow.approval.flowable.action;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.vavr.control.Try;

import com.ouroboros.exception.PlatformException;
import com.ouroboros.workflow.approval.FlowTaskAction;
import com.ouroboros.workflow.approval.flowable.listener.AddSignTaskCompletionListener;
import com.ouroboros.workflow.approval.flowable.service.FlowableAddSignTaskManager;
import com.ouroboros.workflow.approval.flowable.transpiler.Constants;

@Component
public class FlowableApproveAction implements FlowTaskAction<Task> {
  @Autowired
  TaskService taskService;
  @Autowired
  AddSignTaskCompletionListener addSignTaskCompletionListener;
  @Autowired
  FlowableAddSignTaskManager addSignTaskManager;

  @Override
  public String actionName() {
    return Constants.Value.APPROVED;
  }

  @Override
  public Boolean supportTask(Object task) {
    return FlowableTaskKinds.isApprovalTask(taskService, task);
  }

  @Override
  public Try<Void> doAction(Task task) {
    return Try.run(() -> {
      var runtimeTask = taskService.createTaskQuery()
          .taskId(task.getId())
          .active()
          .singleResult();
      if (runtimeTask == null) {
        throw new PlatformException("任务不存在或已结束: " + task.getId());
      }

      if (Boolean.TRUE.equals(taskService.getVariableLocal(task.getId(), Constants.Variable.ADD_SIGN_WAITING))) {
        throw new PlatformException("当前任务正在等待加签处理完成");
      }

      if (addSignTaskCompletionListener.onTaskApproved(runtimeTask)) {
        return;
      }

      var afterCurrentGroupId = taskService.getVariableLocal(
          task.getId(),
          Constants.Variable.ADD_SIGN_GROUP_ID,
          String.class);

      var pendingUsers = readPendingUsers(task.getId());
      var hasPending = !pendingUsers.isEmpty();
      if (hasPending) {
        var currentAssignee = runtimeTask.getAssignee();
        var remained = pendingUsers.stream()
            .filter(user -> !Objects.equals(user, currentAssignee))
            .collect(Collectors.toList());

        if (!remained.isEmpty()) {
          var nextAssignee = remained.get(0);
          taskService.setVariableLocal(task.getId(), Constants.Variable.ADD_SIGN_PENDING_USERS, remained);
          taskService.setVariableLocal(
              task.getId(),
              Constants.Variable.LOCAL_APPROVAL_ACTION,
              isFreeApprovalDetourActive(task.getId())
                  ? Constants.Value.FREE_APPROVE
                  : Constants.Value.ADD_SIGN);
          taskService.addCandidateUser(task.getId(), nextAssignee);
          taskService.setAssignee(task.getId(), nextAssignee);
          return;
        }

        if (isFreeApprovalDetourActive(task.getId())) {
          var sourceAssignee = taskService.getVariableLocal(
              task.getId(),
              Constants.Variable.FREE_APPROVAL_SOURCE_ASSIGNEE,
              String.class);
          if (StringUtils.isBlank(sourceAssignee)) {
            throw new PlatformException("自由审批来源审批人缺失");
          }

          clearFreeApprovalContext(task.getId());
          taskService.setVariableLocal(
              task.getId(),
              Constants.Variable.LOCAL_APPROVAL_ACTION,
              Constants.Value.FREE_APPROVE);
          taskService.addCandidateUser(task.getId(), sourceAssignee);
          taskService.setAssignee(task.getId(), sourceAssignee);
          return;
        }
      }

      taskService.removeVariableLocal(task.getId(), Constants.Variable.ADD_SIGN_PENDING_USERS);
      taskService.removeVariableLocal(task.getId(), Constants.Variable.ADD_SIGN_AFTER_CURRENT_PENDING);
      taskService.setVariableLocal(task.getId(), Constants.Variable.LOCAL_APPROVAL_ACTION, actionName());
      taskService.complete(task.getId());

      if (StringUtils.isNotBlank(afterCurrentGroupId)) {
        addSignTaskManager.activateAfterCurrentGroup(runtimeTask.getProcessInstanceId(), afterCurrentGroupId);
      }
    });
  }

  private List<String> readPendingUsers(String taskId) {
    var pendingUsers = taskService.getVariableLocal(taskId, Constants.Variable.ADD_SIGN_PENDING_USERS);
    if (!(pendingUsers instanceof List)) {
      return Collections.emptyList();
    }

    return ((List<?>) pendingUsers).stream()
        .filter(Objects::nonNull)
        .map(String::valueOf)
        .collect(Collectors.toList());
  }

  private boolean isFreeApprovalDetourActive(String taskId) {
    return Boolean.TRUE.equals(taskService.getVariable(
        taskId,
        Constants.Variable.FREE_APPROVAL_DETOUR_ACTIVE,
        Boolean.class));
  }

  private void clearFreeApprovalContext(String taskId) {
    taskService.setVariable(taskId, Constants.Variable.FREE_APPROVAL_DETOUR_ACTIVE, Boolean.FALSE);
    taskService.removeVariable(taskId, Constants.Variable.FREE_APPROVAL_CONSULT_USERS);
    taskService.removeVariable(taskId, Constants.Variable.FREE_APPROVAL_SOURCE_NODE);
    taskService.removeVariableLocal(taskId, Constants.Variable.FREE_APPROVAL_SOURCE_ASSIGNEE);
    taskService.removeVariableLocal(taskId, Constants.Variable.ADD_SIGN_PENDING_USERS);
    taskService.removeVariableLocal(taskId, Constants.Variable.ADD_SIGN_AFTER_CURRENT_PENDING);
  }
}
