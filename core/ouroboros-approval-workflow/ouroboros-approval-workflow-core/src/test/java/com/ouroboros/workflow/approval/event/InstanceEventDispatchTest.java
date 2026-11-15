package com.ouroboros.workflow.approval.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

import com.ouroboros.workflow.approval.ApprovalWorkflowAutoConfiguration;
import com.ouroboros.workflow.approval.listener.FlowInstanceListener;
import com.ouroboros.workflow.approval.model.events.FlowInstanceEvent;
import com.ouroboros.workflow.approval.model.events.FlowInstanceEvent.EventType;

/**
 * M4 统一结果事件底座测试：
 * 1) 兼容测试路径——反射注入监听器可在无 Spring 场景记录事件；
 * 2) Spring 事件总线——@EventListener 订阅方收到统一结果事件。
 */
class InstanceEventDispatchTest {

  @Test
  void legacyListenerFieldInjectionStillDelivers() throws Exception {
    InstanceEventDispatcher dispatcher = new InstanceEventDispatcher();
    var recorder = new RecordingFlowInstanceListener();
    Field field = InstanceEventDispatcher.class.getDeclaredField("listeners");
    field.setAccessible(true);
    field.set(dispatcher, Collections.singletonList(recorder));

    dispatcher.dispatchEvent(event(EventType.APPROVED));

    assertThat(recorder.events).hasSize(1);
    assertThat(recorder.events.get(0).getEventType()).isEqualTo(EventType.APPROVED);
  }

  @Test
  void failedEventUsesDedicatedListenerCallback() {
    var listener = new FailedOnlyListener();

    listener.onEvent(event(EventType.FAILED));

    assertThat(listener.failedEvents).hasSize(1);
    assertThat(listener.failedEvents.get(0).getEventType()).isEqualTo(EventType.FAILED);
  }

  @SpringBootTest(classes = {ApprovalWorkflowAutoConfiguration.class, SpringEventTestConfig.class})
  static class SpringEventTest {

    @Autowired
    InstanceEventDispatcher dispatcher;

    @Autowired
    SpringSubscriber subscriber;

    @BeforeEach
    void clearRecordedEvents() {
      subscriber.events.clear();
    }

    @Test
    void publishesUnifiedResultEventAndDeliversListenerOnce() {
      dispatcher.dispatchEvent(event(EventType.REJECTED));

      assertThat(subscriber.events).hasSize(1);
      assertThat(subscriber.events.get(0).getEventType()).isEqualTo(EventType.REJECTED);
    }

    @Test
    void unifiedEventPayloadFieldsSurviveDispatch() {
      var e = event(EventType.STARTED);
      e.setBusinessContext(new FlowInstanceEvent.BusinessContext("supplier", "sup-1"));
      e.setCallbackFlow("supplier.processCallback");
      e.setTrackingId("tracking-1");
      e.setExternalUrl("https://external.example/inst-1");

      dispatcher.dispatchEvent(e);

      assertThat(subscriber.events.get(0).getBusinessContext()).isEqualTo(new FlowInstanceEvent.BusinessContext("supplier", "sup-1"));
      assertThat(subscriber.events.get(0).getCallbackFlow()).isEqualTo("supplier.processCallback");
      assertThat(subscriber.events.get(0).getTrackingId()).isEqualTo("tracking-1");
      assertThat(subscriber.events.get(0).getExternalUrl()).isEqualTo("https://external.example/inst-1");
    }
  }

  @Configuration
  static class SpringEventTestConfig {

    @Bean
    SpringSubscriber springSubscriber() {
      return new SpringSubscriber();
    }

  }

  /** @EventListener 订阅方（新机制消费者形态）。 */
  static class SpringSubscriber {
    final List<FlowInstanceEvent> events = new CopyOnWriteArrayList<>();

    @EventListener
    public void onFlowInstanceEvent(FlowInstanceEvent event) {
      events.add(event);
    }
  }

  /** 无 Spring 场景的测试记录器，不作为生产 Bean 装配。 */
  static class RecordingFlowInstanceListener implements FlowInstanceListener {
    final List<FlowInstanceEvent> events = new CopyOnWriteArrayList<>();

    @Override
    public void onEvent(FlowInstanceEvent event) {
      events.add(event);
    }
  }

  static class FailedOnlyListener implements FlowInstanceListener {
    final List<FlowInstanceEvent> failedEvents = new CopyOnWriteArrayList<>();

    @Override
    public void onFailed(FlowInstanceEvent event) {
      failedEvents.add(event);
    }
  }

  static FlowInstanceEvent event(EventType eventType) {
    var event = new FlowInstanceEvent();
    event.setFlowKey("flow-1");
    event.setInstanceId("inst-1");
    event.setEventType(eventType);
    event.setStarter("user-1");
    return event;
  }
}
