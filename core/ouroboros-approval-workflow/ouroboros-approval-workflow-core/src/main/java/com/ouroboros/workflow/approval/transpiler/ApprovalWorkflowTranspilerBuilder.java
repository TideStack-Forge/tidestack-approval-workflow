package com.ouroboros.workflow.approval.transpiler;

import org.apache.commons.lang3.NotImplementedException;

import com.ouroboros.util.SPI;

public interface ApprovalWorkflowTranspilerBuilder<T> {
  static <T> ApprovalWorkflowTranspilerBuilder<T> get(Class<T> clazz) {
    return SPI
        .getCachedReversedServiceStream(ApprovalWorkflowTranspilerBuilder.class, clazz)
        .findFirst()
        .map(builder -> (ApprovalWorkflowTranspilerBuilder<T>) builder)
        .orElseGet(() -> unsupportedBuilder(clazz));
  }

  static <T> ApprovalWorkflowTranspilerBuilder<T> unsupportedBuilder(Class<T> clazz) {
    return () -> {
      throw new NotImplementedException("No implementation found for " + clazz);
    };
  }

  ApprovalWorkflowTranspiler<T> build();
}
