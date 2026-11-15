package com.ouroboros.workflow.approval.flowable.action;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.vavr.control.Try;

import com.ouroboros.exception.PlatformException;
import com.ouroboros.util.Json;
import com.ouroboros.workflow.approval.FlowTaskAction;
import com.ouroboros.workflow.approval.flowable.service.FlowableAddSignTaskManager;
import com.ouroboros.workflow.approval.flowable.transpiler.Constants;

@Component
public class FlowableAddSignAction implements FlowTaskAction<Task> {
  private static final String KEY_TARGET_USER_IDS = "targetUserIds";
  private static final String KEY_POSITION = "position";
  private static final String KEY_MODE = "mode";

  @Autowired
  TaskService taskService;
  @Autowired
  FlowableAddSignTaskManager addSignTaskManager;

  public FlowableAddSignAction(RuntimeService runtimeService) {
    // keep constructor signature for existing test adapters
  }

  @Override
  public String actionName() {
    return Constants.Value.ADD_SIGN;
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
      if (Boolean.TRUE.equals(taskService.getVariableLocal(runtimeTask.getId(), Constants.Variable.ADD_SIGN_WAITING))) {
        throw new PlatformException("当前任务正在等待加签处理完成");
      }

      var payload = resolvePayloadMap(task.getId());
      var targetUsers = resolveTargetUsers(payload);
      if (targetUsers.isEmpty()) {
        throw new PlatformException("加签目标用户不能为空");
      }

      var position = resolvePosition(payload);
      if (Constants.Value.ADD_SIGN_POSITION_BEFORE_CURRENT.equals(position)) {
        addSignTaskManager.createBeforeCurrentGroup(runtimeTask, targetUsers);

        taskService.setVariableLocal(task.getId(), Constants.Variable.LOCAL_APPROVAL_ACTION, actionName());
        return;
      }
      if (Constants.Value.ADD_SIGN_POSITION_AFTER_CURRENT.equals(position)) {
        addSignTaskManager.prepareAfterCurrentGroup(runtimeTask, targetUsers);
        taskService.setVariableLocal(task.getId(), Constants.Variable.LOCAL_APPROVAL_ACTION, actionName());
        return;
      }
      throw new PlatformException("不支持的加签位置: " + position);
    });
  }

  private Map<String, Object> resolvePayloadMap(String taskId) {
    Object payload = taskService.getVariableLocal(taskId, Constants.TASK_PAYLOAD_KEY);
    if (payload == null) {
      return Collections.emptyMap();
    }
    if (payload instanceof Map) {
      return (Map<String, Object>) payload;
    }
    return Json.toMap(String.valueOf(payload));
  }

  private List<String> resolveTargetUsers(Map<String, Object> payload) {
    if (payload == null) {
      return Collections.emptyList();
    }
    Object rawTargetUsers = payload.get(KEY_TARGET_USER_IDS);
    if (rawTargetUsers == null) {
      return Collections.emptyList();
    }

    List<?> source;
    if (rawTargetUsers instanceof List) {
      source = (List<?>) rawTargetUsers;
    } else {
      source = Json.toList(String.valueOf(rawTargetUsers));
    }

    return source.stream()
        .filter(Objects::nonNull)
        .map(String::valueOf)
        .map(String::trim)
        .filter(StringUtils::isNotBlank)
        .collect(Collectors.toCollection(ArrayList::new));
  }

  private String resolvePosition(Map<String, Object> payload) {
    if (payload == null) {
      throw new PlatformException("加签参数缺失");
    }

    Object positionValue = payload.get(KEY_POSITION);
    if (positionValue == null) {
      positionValue = payload.get(KEY_MODE);
    }
    if (positionValue == null) {
      throw new PlatformException("加签位置不能为空");
    }

    return String.valueOf(positionValue).trim().toUpperCase();
  }
}
