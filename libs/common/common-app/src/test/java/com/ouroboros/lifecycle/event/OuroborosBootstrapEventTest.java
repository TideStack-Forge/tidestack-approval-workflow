package com.ouroboros.lifecycle.event;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.ouroboros.lifecycle.StartupLifecycleState;

class OuroborosBootstrapEventTest {

  @Test
  void constructsProgressEventWithLifecycleFields() {
    Object source = new Object();
    OuroborosBootstrapProgressEvent event = new OuroborosBootstrapProgressEvent(
        source,
        "metadata-refreshing",
        "metadata",
        "正在刷新元数据模型"
    );

    assertSame(source, event.getSource());
    assertEquals("metadata-refreshing", event.getPhase());
    assertEquals("metadata", event.getModule());
    assertEquals("正在刷新元数据模型", event.getMessage());
  }

  @Test
  void constructsPhaseSwitchProgressEventWithoutModuleOverride() {
    Object source = new Object();
    OuroborosBootstrapProgressEvent event = OuroborosBootstrapProgressEvent.phaseSwitch(
        source,
        "model-refreshing",
        "正在刷新业务模型"
    );

    assertSame(source, event.getSource());
    assertEquals("model-refreshing", event.getPhase());
    assertEquals(null, event.getModule());
    assertEquals("正在刷新业务模型", event.getMessage());
  }

  @Test
  void constructsModuleProgressEventWithoutPhaseOverride() {
    Object source = new Object();
    OuroborosBootstrapProgressEvent event = OuroborosBootstrapProgressEvent.moduleProgress(
        source,
        "metadata",
        "正在加载元数据扩展"
    );

    assertSame(source, event.getSource());
    assertEquals(null, event.getPhase());
    assertEquals("metadata", event.getModule());
    assertEquals("正在加载元数据扩展", event.getMessage());
  }

  @Test
  void constructsFailedEventWithLifecycleFields() {
    Object source = new Object();
    IllegalStateException failure = new IllegalStateException("模型刷新失败");
    OuroborosBootstrapFailedEvent event = new OuroborosBootstrapFailedEvent(
        source,
        "model-refreshing",
        "model",
        failure
    );

    assertSame(source, event.getSource());
    assertEquals("model-refreshing", event.getPhase());
    assertEquals("model", event.getModule());
    assertSame(failure, event.getThrowable());
    assertEquals(false, event.isFallback());
  }

  @Test
  void constructsFallbackFailedEvent() {
    Object source = new Object();
    IllegalStateException failure = new IllegalStateException("模型刷新失败");
    OuroborosBootstrapFailedEvent event = OuroborosBootstrapFailedEvent.fallback(
        source,
        "model-refreshing",
        "model",
        failure
    );

    assertSame(source, event.getSource());
    assertEquals("model-refreshing", event.getPhase());
    assertEquals("model", event.getModule());
    assertSame(failure, event.getThrowable());
    assertEquals(true, event.isFallback());
  }

  @Test
  void startupLifecycleStateDefinesStableProtocolValues() {
    assertEquals("BOOTSTRAPPING", StartupLifecycleState.BOOTSTRAPPING.name());
    assertEquals("READY", StartupLifecycleState.READY.name());
    assertEquals("FAILED", StartupLifecycleState.FAILED.name());
  }
}
