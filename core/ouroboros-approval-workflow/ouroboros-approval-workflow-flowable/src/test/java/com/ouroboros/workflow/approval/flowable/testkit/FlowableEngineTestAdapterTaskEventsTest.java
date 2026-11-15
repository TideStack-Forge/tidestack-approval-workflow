package com.ouroboros.workflow.approval.flowable.testkit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.ouroboros.workflow.approval.testkit.fixture.SharedSemanticScenarioFixtures;
import com.ouroboros.workflow.approval.testkit.scenario.SharedSemanticScenario;
import com.ouroboros.workflow.approval.testkit.snapshot.RuntimeInstance;
import com.ouroboros.workflow.approval.testkit.snapshot.TaskEventView;
import com.ouroboros.workflow.approval.testkit.snapshot.TaskView;
import com.ouroboros.workflow.approval.flowable.service.FlowableFlowTaskService;
import com.ouroboros.workflow.approval.model.TaskState;

class FlowableEngineTestAdapterTaskEventsTest {
  private final FlowableEngineTestAdapter adapter = new FlowableEngineTestAdapter();
  private final SharedSemanticScenarioFixtures fixtures = new SharedSemanticScenarioFixtures();

  @AfterEach
  void resetAdapter() {
    adapter.reset();
  }

  @Test
  void taskEventsDoesNotSynthesizeSingleApprovalHistoryWhenCollectorIsEmpty() {
    String json = fixtures.json(SharedSemanticScenario.SIMPLE_SINGLE);
    String flowKey = "flow-smoke-single";
    adapter.deploy(flowKey, json);

    RuntimeInstance instance = adapter.start(flowKey, fixtures.startRequest(SharedSemanticScenario.SIMPLE_SINGLE));
    adapter.completeStartTask(instance.getInstanceId());

    TaskView task = adapter.activeTasks(instance.getInstanceId(), "approve-a").get(0);
    adapter.doAction(
        task.getTaskId(),
        "approve",
        "user-a",
        null,
        Collections.<String, Object>emptyMap(),
        null
    );

    assertFalse(adapter.taskEvents(instance.getInstanceId()).isEmpty());

    setField(adapter, "taskEventsByInstance", Collections.<String, List<TaskEventView>>emptyMap());

    assertTrue(adapter.taskEvents(instance.getInstanceId()).isEmpty());
  }

  @Test
  void resetClearsInstalledDispatcherReferences() {
    String json = fixtures.json(SharedSemanticScenario.SIMPLE_SINGLE);
    adapter.deploy("flow-reset-dispatchers", json);

    assertTrue(readField(adapter, "taskEventDispatcher") != null);
    assertTrue(readField(adapter, "instanceEventDispatcher") != null);

    adapter.reset();

    assertTrue(readField(adapter, "taskEventDispatcher") == null);
    assertTrue(readField(adapter, "instanceEventDispatcher") == null);
  }

  @Test
  void trustedActionUsesRealFlowableEngineAndPublishesOneCanonicalTaskEvent() {
    String json = fixtures.json(SharedSemanticScenario.SIMPLE_SINGLE);
    adapter.deploy("flow-smoke-single", json);
    RuntimeInstance instance = adapter.start(
        "flow-smoke-single", fixtures.startRequest(SharedSemanticScenario.SIMPLE_SINGLE));
    adapter.completeStartTask(instance.getInstanceId());

    TaskView task = adapter.activeTasks(instance.getInstanceId(), "approve-a").get(0);
    adapter.executeTrustedAction(task.getTaskId(), "approve", "external callback", null, null,
        "callback-fact-1");

    assertTrue(adapter.isFinished(instance.getInstanceId()));
    assertEquals(2, adapter.taskEvents(instance.getInstanceId()).size());
    assertEquals(TaskState.APPROVED, adapter.taskEvents(instance.getInstanceId()).get(1).getState());
    assertEquals(1, adapter.taskActionFacts().size());
    assertEquals("approve", adapter.taskActionFacts().getFirst().getActionName());
    assertEquals(task.getTaskId(), adapter.taskActionFacts().getFirst().getTaskId());
    assertTrue(adapter.taskActionFacts().getFirst().getState() == null);
  }

  @Test
  void ordinaryActionEntryRejectsSystemActor() {
    String json = fixtures.json(SharedSemanticScenario.SIMPLE_SINGLE);
    adapter.deploy("flow-smoke-single", json);
    RuntimeInstance instance = adapter.start(
        "flow-smoke-single", fixtures.startRequest(SharedSemanticScenario.SIMPLE_SINGLE));
    adapter.completeStartTask(instance.getInstanceId());

    TaskView task = adapter.activeTasks(instance.getInstanceId(), "approve-a").get(0);
    IllegalStateException failure = assertThrows(
        IllegalStateException.class,
        () -> adapter.doAction(task.getTaskId(), "approve",
            FlowableFlowTaskService.SYSTEM_ACTOR_USER_ID, null,
            Collections.<String, Object>emptyMap(), null));

    assertEquals("系统动作必须通过受信任任务动作端口执行", failure.getCause().getMessage());
    assertFalse(adapter.isFinished(instance.getInstanceId()));
  }

  private void setField(Object target, String fieldName, Object value) {
    try {
      Field field = target.getClass().getDeclaredField(fieldName);
      field.setAccessible(true);
      field.set(target, value);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("Failed to set field " + fieldName, e);
    }
  }

  private Object readField(Object target, String fieldName) {
    try {
      Field field = target.getClass().getDeclaredField(fieldName);
      field.setAccessible(true);
      return field.get(target);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("Failed to read field " + fieldName, e);
    }
  }
}
