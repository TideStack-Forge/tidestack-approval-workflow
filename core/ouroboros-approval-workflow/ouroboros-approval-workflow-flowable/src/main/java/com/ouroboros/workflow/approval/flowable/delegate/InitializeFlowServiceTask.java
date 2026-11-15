package com.ouroboros.workflow.approval.flowable.delegate;

import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import com.ouroboros.workflow.approval.flowable.transpiler.Constants;

@Component(InitializeFlowServiceTask.BEAN_NAME)
public class InitializeFlowServiceTask implements JavaDelegate {
  public static final String BEAN_NAME = "initializeFlowServiceTask";

  @Override
  public void execute(DelegateExecution execution) {
    execution.setVariable(Constants.Variable.PROCESS_APPROVED, true);
  }
}
