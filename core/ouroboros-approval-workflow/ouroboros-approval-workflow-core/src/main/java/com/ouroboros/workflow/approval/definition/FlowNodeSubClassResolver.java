package com.ouroboros.workflow.approval.definition;

public interface FlowNodeSubClassResolver {
  Class<?> resolve(String type);
}
