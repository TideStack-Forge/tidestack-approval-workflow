package com.ouroboros.workflow.approval.flowable.action;

import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.vavr.control.Try;

import com.ouroboros.workflow.approval.FlowTaskAction;
import com.ouroboros.workflow.approval.flowable.transpiler.Constants;

@Component
public class FlowableRollbackAction implements FlowTaskAction<Task> {
  @Autowired
  TaskService taskService;

  @Override
  public String actionName() {
    return Constants.Value.ROLLBACK;
  }

  @Override
  public Boolean supportTask(Object task) {
    return FlowableTaskKinds.isApprovalTask(taskService, task);
  }

  @Override
  public Try<Void> doAction(Task task) {
    return Try.run(() -> {
      taskService.setVariableLocal(task.getId(), Constants.Variable.LOCAL_APPROVAL_ACTION, actionName());
      taskService.complete(task.getId());
    });
  }
}
