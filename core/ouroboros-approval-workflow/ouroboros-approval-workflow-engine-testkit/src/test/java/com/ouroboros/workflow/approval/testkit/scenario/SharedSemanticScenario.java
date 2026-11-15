package com.ouroboros.workflow.approval.testkit.scenario;

// Shared semantic scenarios are the single source of truth for cross-engine regression inputs.
public enum SharedSemanticScenario {
  SIMPLE_SINGLE("scenarios/json/simple-single.json"),
  SIMPLE_HANDLE("scenarios/json/simple-handle.json"),
  SIMPLE_MULTIPLE_PARALLEL("scenarios/json/simple-multiple-parallel.json"),
  SIMPLE_MULTIPLE_SEQUENTIAL("scenarios/json/simple-multiple-sequential.json"),
  PARALLEL_DOUBLE_MULTIPLE("scenarios/json/parallel-double-multiple.json"),
  FORK_BRANCH("scenarios/json/fork-branch.json"),
  SUBFLOW_PARENT("scenarios/json/subflow-parent.json"),
  SUBFLOW_CHILD("scenarios/json/subflow-child.json"),
  SUBFLOW_PARENT_NO_TASK_CHILD("scenarios/json/subflow-parent-no-task-child.json"),
  HYPHEN_NODE_ID("scenarios/json/hyphen-node-id.json"),
  SINGLE_NO_CANDIDATE_REJECT("scenarios/json/single-no-candidate-reject.json"),
  SINGLE_NO_CANDIDATE_APPROVE("scenarios/json/single-no-candidate-approve.json"),
  SINGLE_NO_CANDIDATE_REASSIGN("scenarios/json/single-no-candidate-reassign.json"),
  NO_CANDIDATE_REJECT("scenarios/json/no-candidate-reject.json"),
  NO_CANDIDATE_APPROVE("scenarios/json/no-candidate-approve.json"),
  NO_CANDIDATE_REASSIGN("scenarios/json/no-candidate-reassign.json");

  private final String resourcePath;

  SharedSemanticScenario(String resourcePath) {
    this.resourcePath = resourcePath;
  }

  public String getResourcePath() {
    return resourcePath;
  }
}
