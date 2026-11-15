package com.ouroboros.workflow.approval.flowable.action;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.vavr.control.Try;

import com.ouroboros.exception.PlatformException;
import com.ouroboros.util.Json;
import com.ouroboros.workflow.approval.FlowTaskAction;
import com.ouroboros.workflow.approval.flowable.transpiler.Constants;

@Component
public class FlowableRejectAction implements FlowTaskAction<Task> {
  @Autowired
  TaskService taskService;

  @Override
  public String actionName() {
    return Constants.Value.REJECTED;
  }

  @Override
  public Boolean supportTask(Object task) {
    return FlowableTaskKinds.isApprovalTask(taskService, task);
  }

  @Override
  public Try<Void> doAction(Task task) {
    return Try.run(() -> {
      if (isRejectToCurrentNode(task)) {
        taskService.setVariableLocal(task.getId(), Constants.Variable.LOCAL_APPROVAL_ACTION, actionName());
        return;
      }
      taskService.setVariableLocal(task.getId(), Constants.Variable.LOCAL_APPROVAL_ACTION, actionName());
      taskService.complete(task.getId());
    });
  }

  private boolean isRejectToCurrentNode(Task task) {
    var targetNodeId = Optional.ofNullable(resolvePayloadMap(task.getId()).get("targetNodeId"))
        .map(String::valueOf)
        .map(String::trim)
        .orElse(null);
    if (StringUtils.isBlank(targetNodeId)) {
      return false;
    }

    var currentNodeId = Optional.ofNullable(task.getTaskDefinitionKey())
        .map(String::trim)
        .filter(StringUtils::isNotBlank)
        .orElseThrow(() -> new PlatformException("任务节点信息缺失，无法执行拒绝操作"));
    return StringUtils.equals(currentNodeId, targetNodeId);
  }

  private Map<String, Object> resolvePayloadMap(String taskId) {
    Object payload = taskService.getVariableLocal(taskId, Constants.TASK_PAYLOAD_KEY);
    if (payload == null) {
      return Collections.emptyMap();
    }
    return payload instanceof Map
        // 只读使用；显式转 Map 不产生未检查告警（已用 instanceof 校验过）
        ? (Map<String, Object>) payload
        : Json.toMap(String.valueOf(payload));
  }
}
