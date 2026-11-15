package com.ouroboros.workflow.approval.flowable.action;

import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;

import com.ouroboros.workflow.approval.flowable.transpiler.Constants;

final class FlowableTaskKinds {
  private FlowableTaskKinds() {
  }

  static boolean isApprovalTask(TaskService taskService, Object task) {
    return task instanceof Task && !isHandleTask(taskService, (Task) task);
  }

  static boolean isHandleTask(TaskService taskService, Object task) {
    if (!(task instanceof Task)) {
      return false;
    }
    Task flowableTask = (Task) task;
    Object kind = taskService == null
        ? null
        : taskService.getVariableLocal(flowableTask.getId(), Constants.Variable.TASK_KIND);
    return Constants.Value.TASK_KIND_HANDLE.equals(kind);
  }
}
