package com.ouroboros.workflow.approval.testkit.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.ouroboros.workflow.approval.testkit.scenario.SharedSemanticScenario;
import com.ouroboros.workflow.approval.testkit.snapshot.RuntimeInstance;
import com.ouroboros.workflow.approval.testkit.snapshot.TaskView;

public abstract class AbstractRuntimeSmokeContractTest extends AbstractEngineContractTest {
  @Test
  void simpleSingleScenarioDeploysStartsAndCreatesApprovalTask() {
    assumeTrue(capabilities().supportsRuntimeSmoke());

    RuntimeInstance instance = deployAndStart(SharedSemanticScenario.SIMPLE_SINGLE);

    List<TaskView> tasks = adapter().activeTasks(instance.getInstanceId(), "approve-a");
    assertEquals(1, tasks.size());
    assertEquals(Arrays.asList("user-a"), tasks.get(0).getCandidateUsers());
    assertFalse(adapter().isFinished(instance.getInstanceId()));
  }

  @Test
  void simpleMultipleParallelScenarioDeploysStartsAndCreatesAssignedTasks() {
    assumeTrue(capabilities().supportsRuntimeSmoke());

    RuntimeInstance instance = deployAndStart(SharedSemanticScenario.SIMPLE_MULTIPLE_PARALLEL);

    assertEquals(
        Arrays.asList("user-a", "user-b", "user-c"),
        taskAssignees(adapter().activeTasks(instance.getInstanceId(), "multiapproval"))
    );
    assertFalse(adapter().isFinished(instance.getInstanceId()));
  }

  @Test
  void subFlowScenarioDeploysParentAndChildAndCreatesRootBranchTasks() {
    assumeTrue(capabilities().supportsRuntimeSmoke());
    assumeTrue(capabilities().supportsSubFlowInMemory());

    adapter().deploy("child-smoke-flow", json(SharedSemanticScenario.SUBFLOW_CHILD));
    adapter().deploy("flow-smoke-complex", json(SharedSemanticScenario.SUBFLOW_PARENT));
    RuntimeInstance instance = adapter().start("flow-smoke-complex", startRequest(SharedSemanticScenario.SUBFLOW_PARENT));
    adapter().completeStartTask(instance.getInstanceId());

    List<TaskView> singleTasks = adapter().activeTasks(instance.getInstanceId(), "approve-a");
    List<TaskView> multipleTasks = adapter().activeTasks(instance.getInstanceId(), "multi-a");
    List<TaskView> childTasks = adapter().activeTasks(instance.getInstanceId(), "child-approve");

    assertEquals(1, singleTasks.size());
    assertEquals(Arrays.asList("user-a"), singleTasks.get(0).getCandidateUsers());
    assertEquals(Arrays.asList("user-b", "user-c"), taskAssignees(multipleTasks));
    assertTrue(childTasks.isEmpty());
    assertFalse(adapter().isFinished(instance.getInstanceId()));
  }
}
