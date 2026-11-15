package com.ouroboros.workflow.approval.flowable.delegate;

import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component(AddSignGateDelegate.BEAN_NAME)
public class AddSignGateDelegate implements JavaDelegate {
  public static final String BEAN_NAME = "addSignGateDelegate";

  @Override
  public void execute(DelegateExecution execution) {
    // no-op, reserved for future add-sign gate extensions
  }
}
