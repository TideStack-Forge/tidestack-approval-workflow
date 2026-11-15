package com.ouroboros.workflow.approval.flowable.action;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.vavr.control.Try;

import com.ouroboros.exception.PlatformException;
import com.ouroboros.util.Json;
import com.ouroboros.workflow.approval.FlowTaskAction;
import com.ouroboros.workflow.approval.flowable.action.vo.FreeApprovalTaskOptionsVO;
import com.ouroboros.workflow.approval.flowable.transpiler.Constants;

@Component
public class FlowableFreeApprovalAction implements FlowTaskAction<Task> {
  @Autowired
  TaskService taskService;

  @Override
  public String actionName() {
    return Constants.Value.FREE_APPROVE;
  }

  @Override
  public Boolean supportTask(Object task) {
    return task instanceof Task && StringUtils.isNotBlank(((Task) task).getId());
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

      var currentAssignee = Optional.ofNullable(runtimeTask.getAssignee())
          .filter(StringUtils::isNotBlank)
          .orElseThrow(() -> new PlatformException("任务未被认领，无法自由审批"));
      var sourceNodeId = Optional.ofNullable(runtimeTask.getTaskDefinitionKey())
          .filter(StringUtils::isNotBlank)
          .orElseThrow(() -> new PlatformException("任务节点信息缺失，无法自由审批"));

      var consultUsers = resolveConsultUsers(task.getId(), currentAssignee);
      if (consultUsers.isEmpty()) {
        throw new PlatformException("自由审批协商人不能为空");
      }

      taskService.setVariable(task.getId(), Constants.Variable.FREE_APPROVAL_DETOUR_ACTIVE, Boolean.TRUE);
      taskService.setVariable(task.getId(), Constants.Variable.FREE_APPROVAL_CONSULT_USERS, consultUsers);
      taskService.setVariable(task.getId(), Constants.Variable.FREE_APPROVAL_SOURCE_NODE, sourceNodeId);

      taskService.setVariableLocal(task.getId(), Constants.Variable.FREE_APPROVAL_SOURCE_ASSIGNEE, currentAssignee);
      taskService.setVariableLocal(
          task.getId(),
          Constants.Variable.ADD_SIGN_PENDING_USERS,
          new ArrayList<String>(consultUsers));
      taskService.setVariableLocal(task.getId(), Constants.Variable.ADD_SIGN_AFTER_CURRENT_PENDING, Boolean.FALSE);
      taskService.setVariableLocal(task.getId(), Constants.Variable.LOCAL_APPROVAL_ACTION, actionName());

      consultUsers.forEach(userId -> taskService.addCandidateUser(task.getId(), userId));
      taskService.setAssignee(task.getId(), consultUsers.get(0));
    });
  }

  private List<String> resolveConsultUsers(String taskId, String currentAssignee) {
    Object payload = taskService.getVariableLocal(taskId, Constants.TASK_PAYLOAD_KEY);
    if (payload == null) {
      return Collections.emptyList();
    }

    FreeApprovalTaskOptionsVO options;
    if (payload instanceof FreeApprovalTaskOptionsVO) {
      options = (FreeApprovalTaskOptionsVO) payload;
    } else {
      options = Json.toBean(FreeApprovalTaskOptionsVO.class, String.valueOf(payload));
    }

    return Optional.ofNullable(options)
        .map(FreeApprovalTaskOptionsVO::getConsultUserIds)
        .orElse(Collections.emptyList())
        .stream()
        .filter(Objects::nonNull)
        .map(String::valueOf)
        .map(String::trim)
        .filter(StringUtils::isNotBlank)
        .filter(userId -> !StringUtils.equals(userId, currentAssignee))
        .collect(Collectors.collectingAndThen(Collectors.toCollection(LinkedHashSet::new), ArrayList::new));
  }
}
