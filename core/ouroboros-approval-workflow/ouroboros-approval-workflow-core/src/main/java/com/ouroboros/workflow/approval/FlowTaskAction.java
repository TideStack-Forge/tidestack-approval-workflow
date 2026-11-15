package com.ouroboros.workflow.approval;

import io.vavr.control.Try;

public interface FlowTaskAction<T> {
  String actionName();

  Boolean supportTask(Object task);

  Try<Void> doAction(T task);
}
