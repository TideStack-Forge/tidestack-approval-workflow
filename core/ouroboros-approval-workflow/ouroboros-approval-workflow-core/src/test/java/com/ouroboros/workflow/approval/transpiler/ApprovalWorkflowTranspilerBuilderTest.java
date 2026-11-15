package com.ouroboros.workflow.approval.transpiler;

import static org.junit.jupiter.api.Assertions.assertEquals;

import jakarta.annotation.Priority;

import org.junit.jupiter.api.Test;

import com.ouroboros.workflow.approval.definition.FlowDefinition;
import com.ouroboros.workflow.approval.definition.graph.StructuredFlowDefinition;

class ApprovalWorkflowTranspilerBuilderTest {

  @Test
  void shouldSelectBuilderByRequestedModelTypeInsteadOfPriorityOnly() {
    ApprovalWorkflowTranspilerBuilder<Long> builder = ApprovalWorkflowTranspilerBuilder.get(Long.class);

    assertEquals(Long.valueOf(1L), builder.build().transpile((StructuredFlowDefinition) null));
  }

  @Priority(100)
  public static final class StringBuilder implements ApprovalWorkflowTranspilerBuilder<String> {
    @Override
    public ApprovalWorkflowTranspiler<String> build() {
      return new MarkerTranspiler<String>("string");
    }
  }

  @Priority(0)
  public static final class LongBuilder implements ApprovalWorkflowTranspilerBuilder<Long> {
    @Override
    public ApprovalWorkflowTranspiler<Long> build() {
      return new MarkerTranspiler<Long>(Long.valueOf(1L));
    }
  }

  private static final class MarkerTranspiler<T> implements ApprovalWorkflowTranspiler<T> {
    private final T marker;

    private MarkerTranspiler(T marker) {
      this.marker = marker;
    }

    @Override
    public T transpile(StructuredFlowDefinition flowDefinition) {
      return marker;
    }

    @Override
    public T transpile(FlowDefinition flowDefinition) {
      return marker;
    }
  }
}
