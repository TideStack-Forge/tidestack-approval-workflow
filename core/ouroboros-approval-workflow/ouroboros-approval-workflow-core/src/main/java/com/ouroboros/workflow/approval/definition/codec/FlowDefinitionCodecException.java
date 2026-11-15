package com.ouroboros.workflow.approval.definition.codec;

public class FlowDefinitionCodecException extends RuntimeException {
  public FlowDefinitionCodecException(String message) {
    super(message);
  }

  public FlowDefinitionCodecException(String message, Throwable cause) {
    super(message, cause);
  }
}
