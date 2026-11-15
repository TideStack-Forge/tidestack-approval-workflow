package com.ouroboros.workflow.approval.flowable.action;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.flowable.engine.TaskService;
import org.flowable.identitylink.api.IdentityLink;
import org.flowable.task.api.Task;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.vavr.control.Try;

import com.ouroboros.exception.PlatformException;
import com.ouroboros.util.Json;
import com.ouroboros.workflow.approval.FlowTaskAction;
import com.ouroboros.workflow.approval.flowable.transpiler.Constants;

@Component
public class FlowableTransferAction implements FlowTaskAction<Task> {
  @Autowired
  TaskService taskService;

  @Override
  public String actionName() {
    return "transfer";
  }

  @Override
  public Boolean supportTask(Object task) {
    return task instanceof Task && StringUtils.isNotBlank(((Task) task).getId());
  }

  @Override
  public Try<Void> doAction(Task task) {
    return Try.run(() -> {
      String taskId = task.getId();
      String currentAssignee = Optional.ofNullable(taskService.createTaskQuery().taskId(taskId).singleResult())
          .map(Task::getAssignee)
          .orElse(task.getAssignee());
      if (StringUtils.isBlank(currentAssignee)) {
        throw new PlatformException("任务未被认领，无法转办");
      }

      String targetUserId = resolveTargetUserId(taskId);
      if (StringUtils.isBlank(targetUserId)) {
        throw new PlatformException("转办目标用户不能为空");
      }
      if (StringUtils.equals(currentAssignee, targetUserId)) {
        throw new PlatformException("转办目标用户不能与当前处理人相同");
      }

      List<String> candidateUsers = taskService.getIdentityLinksForTask(taskId)
          .stream()
          .filter(Objects::nonNull)
          .filter(link -> "candidate".equalsIgnoreCase(link.getType()) || "assignee".equalsIgnoreCase(link.getType()))
          .map(IdentityLink::getUserId)
          .filter(StringUtils::isNotBlank)
          .distinct()
          .collect(Collectors.toList());

      taskService.setAssignee(taskId, targetUserId);
      if (candidateUsers.contains(currentAssignee)) {
        taskService.deleteCandidateUser(taskId, currentAssignee);
      }
      if (!candidateUsers.contains(targetUserId)) {
        taskService.addCandidateUser(taskId, targetUserId);
      }

      taskService.setVariableLocal(taskId, Constants.Variable.LOCAL_APPROVAL_ACTION, actionName());
      taskService.addComment(
          taskId,
          task.getProcessInstanceId(),
          "transfer: " + currentAssignee + " -> " + targetUserId);
    });
  }

  private String resolveTargetUserId(String taskId) {
    Object payload = taskService.getVariableLocal(taskId, Constants.TASK_PAYLOAD_KEY);
    if (payload == null) {
      return null;
    }
    String payloadText = payload instanceof Map
        ? Json.toJsonString(payload)
        : String.valueOf(payload);
    Map<String, Object> payloadMap = Json.toMap(payloadText);
    return payloadMap.get("targetUserId") == null ? null : String.valueOf(payloadMap.get("targetUserId"));
  }
}
