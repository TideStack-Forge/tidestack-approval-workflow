package com.ouroboros.workflow.approval.flowable.listener;

import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.ExecutionListener;
import org.flowable.engine.impl.persistence.entity.ExecutionEntity;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.ProcessDefinition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ouroboros.util.Dates;
import com.ouroboros.workflow.approval.event.InstanceEventDispatcher;
import com.ouroboros.workflow.approval.flowable.transpiler.Constants;
import com.ouroboros.workflow.approval.model.events.FlowInstanceEvent;

@Component(FlowStartExecutionListener.BEAN_NAME)
public class FlowStartExecutionListener implements ExecutionListener {
  public static final String BEAN_NAME = "flowStartExecutionListener";
  @Autowired
  private RepositoryService repositoryService;
  @Autowired
  private InstanceEventDispatcher instanceEventDispatcher;

  @Override
  public void notify(DelegateExecution execution) {
    ExecutionEntity executionEntity = (ExecutionEntity) execution;
    ExecutionEntity processInstance = executionEntity.getProcessInstance();
    ExecutionEntity superExecution = processInstance.getSuperExecution();

    execution.setVariable(Constants.Variable.HAS_PARENT_FLOW, superExecution != null);
    if (superExecution != null) {
      execution.setVariable(Constants.Variable.PARENT_FLOW_ID, superExecution.getProcessInstanceId());
    }

    dispatchEvent(execution);
  }

  private void dispatchEvent(DelegateExecution execution) {
    var event = new FlowInstanceEvent();
    event.setInstanceId(execution.getProcessInstanceId());
    event.setFlowKey(resolveFlowKey(execution));
    event.setEventType(FlowInstanceEvent.EventType.STARTED);
    event.setEventTime(Dates.now());
    event.setStarter(execution.getVariable(Constants.Variable.PROCESS_STARTER, String.class));
    event.setApprovalCaseId(execution.getVariable(Constants.Variable.APPROVAL_CASE_ID, String.class));
    event.setSubmissionRound(execution.getVariable(Constants.Variable.SUBMISSION_ROUND, Integer.class));
    event.setBusinessContext(businessContextOf(execution));
    event.setCallbackFlow(execution.getVariable(Constants.Variable.CALLBACK_FLOW, String.class));
    instanceEventDispatcher.dispatchEvent(event);
  }

  private FlowInstanceEvent.BusinessContext businessContextOf(DelegateExecution execution) {
    var recordType = execution.getVariable(Constants.Variable.BUSINESS_RECORD_TYPE, String.class);
    var recordId = execution.getVariable(Constants.Variable.BUSINESS_RECORD_ID, String.class);
    if (recordType == null || recordId == null) {
      return null;
    }
    return new FlowInstanceEvent.BusinessContext(recordType, recordId);
  }

  private String resolveFlowKey(DelegateExecution execution) {
    var flowKey = execution.getVariable(Constants.Variable.PROCESS_FLOW_KEY, String.class);
    if (flowKey != null) {
      return flowKey;
    }

    if (repositoryService == null || execution.getProcessDefinitionId() == null) {
      return null;
    }

    ProcessDefinition processDefinition = repositoryService.getProcessDefinition(execution.getProcessDefinitionId());
    return processDefinition == null ? null : processDefinition.getKey();
  }
}
