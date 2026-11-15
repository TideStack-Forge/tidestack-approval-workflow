package com.ouroboros.workflow.approval.flowable.listener;

import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.TaskService;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.ExecutionListener;
import org.flowable.engine.task.Comment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ouroboros.util.Dates;
import com.ouroboros.workflow.approval.event.TaskEventDispatcher;
import com.ouroboros.workflow.approval.flowable.transpiler.Constants;
import com.ouroboros.workflow.approval.flowable.util.BpmnUtil;
import com.ouroboros.workflow.approval.model.TaskState;
import com.ouroboros.workflow.approval.model.events.FlowTaskEvent;

@Component(MultipleApprovalEndExecutionListener.BEAN_NAME)
public class MultipleApprovalEndExecutionListener implements ExecutionListener {
  public static final String BEAN_NAME = "multipleApprovalEndExecutionListener";
  @Autowired
  private HistoryService historyService;
  @Autowired
  private TaskService taskService;
  @Autowired
  private RepositoryService repositoryService;
  @Autowired
  private TaskEventDispatcher taskEventDispatcher;

  @Override
  public void notify(DelegateExecution execution) {
    if (!execution.isMultiInstanceRoot()) {
      return;
    }

    var flowElement = execution.getCurrentFlowElement();
    var nodeId = flowElement == null ? null : flowElement.getId();
    if (historyService == null || StringUtils.isBlank(nodeId)) {
      return;
    }

    var finalTaskId = execution.getVariable(nodeId + Constants.Variable.MULTIPLE_APPROVAL_FINAL_TASK_ID_SUFFIX, String.class);
    var lastTask = StringUtils.isNotBlank(finalTaskId)
        ? historyService.createHistoricTaskInstanceQuery()
            .taskId(finalTaskId)
            .singleResult()
        : historyService.createHistoricTaskInstanceQuery()
            .processInstanceId(execution.getProcessInstanceId())
            .taskDefinitionKey(nodeId)
            .finished()
            .orderByHistoricTaskInstanceEndTime()
            .desc()
            .listPage(0, 1)
            .stream()
            .findFirst()
            .orElse(null);
    if (lastTask == null) {
      return;
    }

    var actionVariable = historyService.createHistoricVariableInstanceQuery()
        .processInstanceId(execution.getProcessInstanceId())
        .taskId(lastTask.getId())
        .variableName(Constants.Variable.LOCAL_APPROVAL_ACTION)
        .singleResult();
    var assignees = BpmnUtil.readStringListVariable(
        execution.getVariable(Constants.Variable.getMultipleApprovalAssigneeListVariableName(nodeId)),
        Constants.Variable.getMultipleApprovalAssigneeListVariableName(nodeId));
    var approved = Boolean.TRUE.equals(execution.getVariable(nodeId + Constants.Variable.APPROVED_SUFFIX, Boolean.class));

    var event = new FlowTaskEvent();
    event.setTaskId(lastTask.getId());
    event.setTaskName(lastTask.getName());
    event.setCandidateUsers(assignees);
    event.setAssignee(lastTask.getAssignee());
    event.setComment(readComment(lastTask.getId(), approved));
    event.setFlowKey(BpmnUtil.resolveFlowKey(repositoryService, execution));
    event.setState(approved ? TaskState.APPROVED : TaskState.REJECTED);
    event.setNodeId(nodeId);
    event.setInstanceId(execution.getProcessInstanceId());
    event.setEventTime(Dates.now());
    event.setActionName(actionVariable == null ? null : String.valueOf(actionVariable.getValue()));
    event.setFlowNodeSupplier(BpmnUtil.getFlowNodeSupplier(repositoryService, execution.getProcessDefinitionId(), nodeId));
    taskEventDispatcher.dispatchEvent(event);
  }

  private String readComment(String taskId, boolean approved) {
    if (approved) {
      return null;
    }

    if (taskService == null) {
      return null;
    }

    return taskService.getTaskComments(taskId).stream()
        .filter(comment -> comment != null)
        .max((left, right) -> compareCommentTime(left, right))
        .map(Comment::getFullMessage)
        .orElse(null);
  }

  private int compareCommentTime(Comment left, Comment right) {
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
  }
}
