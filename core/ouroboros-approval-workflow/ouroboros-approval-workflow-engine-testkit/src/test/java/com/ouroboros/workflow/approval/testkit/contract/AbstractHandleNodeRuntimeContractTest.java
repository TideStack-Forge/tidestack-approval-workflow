package com.ouroboros.workflow.approval.testkit.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.Collections;

import org.junit.jupiter.api.Test;

import com.ouroboros.workflow.approval.model.TaskState;
import com.ouroboros.workflow.approval.testkit.scenario.SharedSemanticScenario;
import com.ouroboros.workflow.approval.testkit.snapshot.RuntimeInstance;
import com.ouroboros.workflow.approval.testkit.snapshot.TaskView;
import com.ouroboros.workflow.approval.testkit.snapshot.TimelineView;

public abstract class AbstractHandleNodeRuntimeContractTest extends AbstractEngineContractTest {
  @Test
  void completeHandleNodeFinishesTaskAndMarksTimelineHandled() {
    assumeTrue(capabilities().supportsTaskServiceRuntime());

    RuntimeInstance instance = deployAndStart(SharedSemanticScenario.SIMPLE_HANDLE);
    TaskView handleTask = onlyTask(instance.getInstanceId(), "handle-a");

    adapter().doAction(
        handleTask.getTaskId(),
        "complete",
        taskActor(handleTask),
        "done",
        Collections.<String, Object>emptyMap(),
        null
    );

    assertTrue(adapter().isFinished(instance.getInstanceId()));

    TimelineView.NodeTimelineView node = timelineNode(adapter().timeline(instance.getInstanceId()), "handle-a");
    assertEquals(TaskState.HANDLED, node.getState());
    assertEquals(1, node.getTaskInstances().size());
    assertEquals(TaskState.HANDLED, node.getTaskInstances().get(0).getState());
  }

  private String taskActor(TaskView task) {
    if (task.getAssignee() != null) {
      return task.getAssignee();
    }
    if (!task.getCandidateUsers().isEmpty()) {
      return task.getCandidateUsers().get(0);
    }
    throw new AssertionError("No assignee or candidate user found for task " + task.getTaskId());
  }
}
