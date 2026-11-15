package com.ouroboros.workflow.approval.flowable.action;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

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
import com.ouroboros.workflow.approval.flowable.transpiler.Constants;

@Component
public class FlowableFreeRecoverAction implements FlowTaskAction<Task> {
  @Autowired
  TaskService taskService;
  @Autowired
  RuntimeService runtimeService;

  @Override
  public String actionName() {
    return "freeRecover";
  }

  @Override
  public Boolean supportTask(Object task) {
    return task instanceof Task && StringUtils.isNotBlank(((Task) task).getId());
  }

  @Override
  public Try<Void> doAction(Task task) {
    return Try.run(() -> {
      taskService.setVariableLocal(task.getId(), Constants.Variable.LOCAL_APPROVAL_ACTION, actionName());
      runtimeService.createChangeActivityStateBuilder()
          .processInstanceId(task.getProcessInstanceId())
          .moveExecutionToActivityId(task.getExecutionId(), resolveTargetNodeId(task.getId(), "自由恢复目标节点不能为空"))
          .changeState();
    });
  }

  private String resolveTargetNodeId(String taskId, String emptyMessage) {
    return Optional.ofNullable(resolvePayloadMap(taskId).get("targetNodeId"))
        .map(String::valueOf)
        .map(String::trim)
        .filter(StringUtils::isNotBlank)
        .orElseThrow(() -> new PlatformException(emptyMessage));
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
