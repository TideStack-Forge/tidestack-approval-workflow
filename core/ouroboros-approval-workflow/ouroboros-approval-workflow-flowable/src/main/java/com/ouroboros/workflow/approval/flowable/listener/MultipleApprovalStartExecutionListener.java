package com.ouroboros.workflow.approval.flowable.listener;

import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.ExecutionListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ouroboros.util.Dates;
import com.ouroboros.workflow.approval.event.TaskEventDispatcher;
import com.ouroboros.workflow.approval.flowable.transpiler.Constants;
import com.ouroboros.workflow.approval.flowable.util.BpmnUtil;
import com.ouroboros.workflow.approval.model.TaskState;
import com.ouroboros.workflow.approval.model.events.FlowTaskEvent;

@Component(MultipleApprovalStartExecutionListener.BEAN_NAME)
public class MultipleApprovalStartExecutionListener implements ExecutionListener {
  public static final String BEAN_NAME = "multipleApprovalStartExecutionListener";
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
    if (StringUtils.isBlank(nodeId)) {
      return;
    }

    var candidates = BpmnUtil.readStringListVariable(
        execution.getVariable(Constants.Variable.getMultipleApprovalAssigneeListVariableName(nodeId)),
        Constants.Variable.getMultipleApprovalAssigneeListVariableName(nodeId));
    if (candidates.isEmpty()) {
      return;
    }

    var event = new FlowTaskEvent();
    event.setInstanceId(execution.getProcessInstanceId());
    event.setTaskId(null);
    event.setTaskName(flowElement == null ? null : flowElement.getName());
    event.setCandidateUsers(candidates);
    event.setAssignee(null);
    event.setFlowKey(BpmnUtil.resolveFlowKey(repositoryService, execution));
    event.setNodeId(nodeId);
    event.setState(TaskState.UNCREATED);
    event.setEventTime(Dates.now());
    event.setFlowNodeSupplier(BpmnUtil.getFlowNodeSupplier(repositoryService, execution.getProcessDefinitionId(), nodeId));
    taskEventDispatcher.dispatchEvent(event);
  }
}
