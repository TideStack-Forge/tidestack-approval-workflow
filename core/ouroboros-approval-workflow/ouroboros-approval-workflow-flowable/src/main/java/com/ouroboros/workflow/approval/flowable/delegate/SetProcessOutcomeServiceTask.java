package com.ouroboros.workflow.approval.flowable.delegate;

import java.util.Optional;

import org.flowable.bpmn.model.FieldExtension;
import org.flowable.bpmn.model.ServiceTask;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import com.ouroboros.workflow.approval.definition.nodes.EndNode;
import com.ouroboros.workflow.approval.flowable.transpiler.Constants;

@Component(SetProcessOutcomeServiceTask.BEAN_NAME)
public class SetProcessOutcomeServiceTask implements JavaDelegate {
  public static final String BEAN_NAME = "setProcessOutcomeServiceTask";

  @Override
  public void execute(DelegateExecution execution) {
    execution.setVariable(Constants.Variable.PROCESS_OUTCOME, readOutcome(execution));
  }

  private String readOutcome(DelegateExecution execution) {
    if (!(execution.getCurrentFlowElement() instanceof ServiceTask)) {
      return EndNode.OUTCOME_APPROVED;
    }
    ServiceTask serviceTask = (ServiceTask) execution.getCurrentFlowElement();
    return Optional.ofNullable(readField(serviceTask, Constants.Property.PROCESS_OUTCOME))
        .filter(value -> !value.trim().isEmpty())
        .orElse(EndNode.OUTCOME_APPROVED);
  }

  private String readField(ServiceTask serviceTask, String name) {
    if (serviceTask.getFieldExtensions() == null) {
      return null;
    }
    for (FieldExtension fieldExtension : serviceTask.getFieldExtensions()) {
      if (name.equals(fieldExtension.getFieldName())) {
        return fieldExtension.getStringValue();
      }
    }
    return null;
  }
}
