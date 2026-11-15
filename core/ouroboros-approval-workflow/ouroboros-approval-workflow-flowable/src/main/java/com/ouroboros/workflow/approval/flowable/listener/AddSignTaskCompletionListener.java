package com.ouroboros.workflow.approval.flowable.listener;

import org.apache.commons.lang3.StringUtils;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.flowable.task.service.delegate.DelegateTask;
import org.flowable.task.service.delegate.TaskListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ouroboros.workflow.approval.flowable.service.FlowableAddSignTaskManager;
import com.ouroboros.workflow.approval.flowable.transpiler.Constants;

@Component
public class AddSignTaskCompletionListener implements TaskListener {
  @Autowired
  private TaskService taskService;
  @Autowired
  private FlowableAddSignTaskManager addSignTaskManager;

  @Override
  public void notify(DelegateTask delegateTask) {
    if (delegateTask == null || StringUtils.isBlank(delegateTask.getId())) {
      return;
    }
    if (!TaskListener.EVENTNAME_COMPLETE.equals(delegateTask.getEventName())) {
      return;
    }

    Task task = taskService.createTaskQuery().taskId(delegateTask.getId()).singleResult();
    if (task != null) {
      onTaskApproved(task);
    }
  }

  public boolean onTaskApproved(Task task) {
    if (task == null || StringUtils.isBlank(task.getId())) {
      return false;
    }

    Object kindValue = taskService.getVariableLocal(task.getId(), Constants.Variable.ADD_SIGN_TASK_KIND);
    if (kindValue == null) {
      return false;
    }

    String taskKind = String.valueOf(kindValue);
    if (Constants.Value.ADD_SIGN_TASK_KIND_ADDED.equals(taskKind)) {
      addSignTaskManager.onAddSignTaskApproved(task);
      return true;
    }
    if (Constants.Value.ADD_SIGN_TASK_KIND_RESUME.equals(taskKind)) {
      addSignTaskManager.onResumeTaskApproved(task);
      return true;
    }
    return false;
  }
}
