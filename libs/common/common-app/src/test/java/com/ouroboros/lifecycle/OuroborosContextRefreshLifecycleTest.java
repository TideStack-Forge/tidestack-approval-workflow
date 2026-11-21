package com.ouroboros.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.ouroboros.lifecycle.event.OuroborosBootstrapFailedEvent;
import com.ouroboros.lifecycle.event.OuroborosBootstrapProgressEvent;
import com.ouroboros.lifecycle.event.OuroborosContextRefreshedEvent;
import com.ouroboros.lifecycle.event.OuroborosContextRefreshingEvent;
import com.ouroboros.lifecycle.event.OuroborosEvent;
import com.ouroboros.lifecycle.event.OuroborosMetadataRefreshedEvent;
import com.ouroboros.lifecycle.event.OuroborosMetadataRefreshingEvent;
import com.ouroboros.lifecycle.event.OuroborosModelRefreshedEvent;
import com.ouroboros.lifecycle.event.OuroborosModelRefreshingEvent;

class OuroborosContextRefreshLifecycleTest {
  @Test
  void refreshCannotReplaceAnyPartOfTheContextWhileAnExecutionIsUsingIt() throws Exception {
    OuroborosContextRefreshLifecycle.publish(this, event -> { });
    var versions = new java.util.concurrent.atomic.AtomicReference<>(List.of("old-contract", "old-flow"));
    var entered = new java.util.concurrent.CountDownLatch(1);
    var release = new java.util.concurrent.CountDownLatch(1);
    try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
      var execution = executor.submit(() -> OuroborosContextRefreshLifecycle.withStableContext(() -> {
        entered.countDown();
        try {
          assertTrue(release.await(3, java.util.concurrent.TimeUnit.SECONDS));
        } catch (InterruptedException failure) {
          Thread.currentThread().interrupt();
          throw new IllegalStateException(failure);
        }
        return versions.get();
      }));
      assertTrue(entered.await(2, java.util.concurrent.TimeUnit.SECONDS));
      var refresh = executor.submit(() -> OuroborosContextRefreshLifecycle.publish(this, event -> {
        if (event instanceof OuroborosMetadataRefreshedEvent) {
          versions.set(List.of("new-contract", "old-flow"));
        }
        if (event instanceof OuroborosModelRefreshedEvent) {
          versions.set(List.of("new-contract", "new-flow"));
        }
      }));
      try {
        assertThrows(java.util.concurrent.TimeoutException.class,
            () -> refresh.get(150, java.util.concurrent.TimeUnit.MILLISECONDS));
      } finally {
        release.countDown();
      }
      assertEquals(List.of("old-contract", "old-flow"), execution.get(2, java.util.concurrent.TimeUnit.SECONDS));
      refresh.get(2, java.util.concurrent.TimeUnit.SECONDS);
      assertEquals(List.of("new-contract", "new-flow"), OuroborosContextRefreshLifecycle.withStableContext(versions::get));
    }
  }

  @Test
  void aFailedRefreshRejectsExecutionUntilACompleteRefreshSucceeds() {
    try {
      assertThrows(IllegalStateException.class, () -> OuroborosContextRefreshLifecycle.publish(this, event -> {
        if (event instanceof OuroborosModelRefreshingEvent) {
          throw new IllegalStateException("flow initialization failed");
        }
      }));
      assertThrows(IllegalStateException.class, () -> OuroborosContextRefreshLifecycle.withStableContext(() -> "mixed"));
      OuroborosContextRefreshLifecycle.publish(this, event -> { });
      assertEquals("ready", OuroborosContextRefreshLifecycle.withStableContext(() -> "ready"));
    } finally {
      OuroborosContextRefreshLifecycle.publish(this, event -> { });
    }
  }

  @Test
  void anExecutionCannotDeadlockItselfByUpgradingToARefresh() {
    OuroborosContextRefreshLifecycle.publish(this, event -> { });
    assertThrows(IllegalStateException.class, () -> OuroborosContextRefreshLifecycle.withStableContext(() -> {
      OuroborosContextRefreshLifecycle.publish(this, event -> { });
      return null;
    }));
    assertEquals("ready", OuroborosContextRefreshLifecycle.withStableContext(() -> "ready"));
  }


  @Test
  void publishesPhaseSwitchProgressBeforeEveryLifecycleEvent() {
    Object source = new Object();
    List<OuroborosEvent> events = new ArrayList<>();

    OuroborosContextRefreshLifecycle.publish(source, events::add);

    assertEquals(12, events.size());
    assertProgress(events.get(0), "context-refreshing", "正在刷新应用上下文");
    assertSame(OuroborosContextRefreshingEvent.class, events.get(1).getClass());
    assertProgress(events.get(2), "metadata-refreshing", "正在刷新元数据模型");
    assertSame(OuroborosMetadataRefreshingEvent.class, events.get(3).getClass());
    assertProgress(events.get(4), "metadata-refreshed", "元数据模型刷新完成");
    assertSame(OuroborosMetadataRefreshedEvent.class, events.get(5).getClass());
    assertProgress(events.get(6), "model-refreshing", "正在刷新业务模型");
    assertSame(OuroborosModelRefreshingEvent.class, events.get(7).getClass());
    assertProgress(events.get(8), "model-refreshed", "业务模型刷新完成");
    assertSame(OuroborosModelRefreshedEvent.class, events.get(9).getClass());
    assertProgress(events.get(10), "context-refreshed", "应用上下文刷新完成");
    assertSame(OuroborosContextRefreshedEvent.class, events.get(11).getClass());
  }

  @Test
  void publishesFallbackFailedEventWhenLifecyclePhaseThrows() {
    RuntimeException failure = new IllegalStateException("模型刷新失败");
    List<OuroborosEvent> events = new ArrayList<>();

    RuntimeException thrown = assertThrows(
        RuntimeException.class,
        () -> OuroborosContextRefreshLifecycle.publish(this, event -> {
          events.add(event);
          if (event instanceof OuroborosModelRefreshingEvent) {
            throw failure;
          }
        })
    );

    assertSame(failure, thrown);
    OuroborosBootstrapFailedEvent failedEvent = events.stream()
        .filter(OuroborosBootstrapFailedEvent.class::isInstance)
        .map(OuroborosBootstrapFailedEvent.class::cast)
        .findFirst()
        .orElseThrow(AssertionError::new);
    assertEquals("model-refreshing", failedEvent.getPhase());
    assertEquals(null, failedEvent.getModule());
    assertSame(failure, failedEvent.getThrowable());
    assertEquals(true, failedEvent.isFallback());
  }

  @Test
  void preservesOriginalFailureWhenFallbackFailedEventPublishingThrows() {
    RuntimeException phaseFailure = new IllegalStateException("模型刷新失败");
    RuntimeException fallbackFailure = new IllegalStateException("失败事件发布失败");

    RuntimeException thrown = assertThrows(
        RuntimeException.class,
        () -> OuroborosContextRefreshLifecycle.publish(this, event -> {
          if (event instanceof OuroborosModelRefreshingEvent) {
            throw phaseFailure;
          }
          if (event instanceof OuroborosBootstrapFailedEvent) {
            throw fallbackFailure;
          }
        })
    );

    assertSame(phaseFailure, thrown);
    assertEquals(1, thrown.getSuppressed().length);
    assertSame(fallbackFailure, thrown.getSuppressed()[0]);
  }

  @Test
  void conditionalPublicationRejectsStaleContextAndNeverReplaysEventsOrPoisonsContext() {
    var events = new ArrayList<OuroborosEvent>();
    OuroborosContextRefreshLifecycle.publish(this, events::add);
    long epoch = OuroborosContextRefreshLifecycle.currentEpoch();
    var value = new java.util.concurrent.atomic.AtomicReference<>("old");
    assertEquals(OuroborosContextRefreshLifecycle.PublicationResult.PUBLISHED,
        OuroborosContextRefreshLifecycle.publishIfCurrent(epoch, java.time.Duration.ZERO,
            () -> true, () -> value.set("new")));
    assertEquals(12, events.size());
    assertEquals("new", value.get());
    assertEquals(OuroborosContextRefreshLifecycle.PublicationResult.STALE,
        OuroborosContextRefreshLifecycle.publishIfCurrent(epoch, java.time.Duration.ZERO,
            () -> false, () -> value.set("wrong")));
    assertThrows(IllegalArgumentException.class, () -> OuroborosContextRefreshLifecycle.publishIfCurrent(
        epoch, java.time.Duration.ZERO, () -> true, () -> { throw new IllegalArgumentException("component"); }));
    assertEquals("new", OuroborosContextRefreshLifecycle.withStableContext(value::get));
    OuroborosContextRefreshLifecycle.publish(this, event -> { });
    assertEquals(OuroborosContextRefreshLifecycle.PublicationResult.STALE,
        OuroborosContextRefreshLifecycle.publishIfCurrent(epoch, java.time.Duration.ZERO,
            () -> true, () -> value.set("wrong")));
    assertEquals("new", value.get());
  }

  @Test
  void conditionalPublicationTimesOutWhileBusinessHoldsTheStableContext() throws Exception {
    OuroborosContextRefreshLifecycle.publish(this, event -> { });
    long epoch = OuroborosContextRefreshLifecycle.currentEpoch();
    var entered = new java.util.concurrent.CountDownLatch(1);
    var release = new java.util.concurrent.CountDownLatch(1);
    try (var executor = java.util.concurrent.Executors.newSingleThreadExecutor()) {
      var reader = executor.submit(() -> OuroborosContextRefreshLifecycle.withStableContext(() -> {
        entered.countDown();
        try {
          assertTrue(release.await(3, java.util.concurrent.TimeUnit.SECONDS));
        } catch (InterruptedException failure) {
          Thread.currentThread().interrupt();
          throw new IllegalStateException(failure);
        }
        return "old";
      }));
      assertTrue(entered.await(2, java.util.concurrent.TimeUnit.SECONDS));
      try {
        assertEquals(OuroborosContextRefreshLifecycle.PublicationResult.TIMED_OUT,
            OuroborosContextRefreshLifecycle.publishIfCurrent(epoch, java.time.Duration.ofMillis(50),
                () -> true, () -> { throw new AssertionError("must not switch"); }));
      } finally {
        release.countDown();
      }
      assertEquals("old", reader.get(2, java.util.concurrent.TimeUnit.SECONDS));
    }
  }

  @Test
  void componentCannotUpgradeReadLockAndCannotRecoverFailedFullContext() {
    OuroborosContextRefreshLifecycle.publish(this, event -> { });
    long epoch = OuroborosContextRefreshLifecycle.currentEpoch();
    assertThrows(IllegalStateException.class, () -> OuroborosContextRefreshLifecycle.withStableContext(() ->
        OuroborosContextRefreshLifecycle.publishIfCurrent(epoch, java.time.Duration.ZERO, () -> true, () -> { })));
    try {
      assertThrows(IllegalStateException.class, () -> OuroborosContextRefreshLifecycle.publish(this, event -> {
        throw new IllegalStateException("full context failed");
      }));
      assertEquals(OuroborosContextRefreshLifecycle.PublicationResult.UNAVAILABLE,
          OuroborosContextRefreshLifecycle.publishIfCurrent(epoch, java.time.Duration.ZERO, () -> true,
              () -> { throw new AssertionError("must not recover"); }));
    } finally {
      OuroborosContextRefreshLifecycle.publish(this, event -> { });
    }
  }

  private static void assertProgress(OuroborosEvent event, String phase, String message) {
    assertSame(OuroborosBootstrapProgressEvent.class, event.getClass());
    OuroborosBootstrapProgressEvent progressEvent = (OuroborosBootstrapProgressEvent) event;
    assertEquals(phase, progressEvent.getPhase());
    assertEquals(null, progressEvent.getModule());
    assertEquals(message, progressEvent.getMessage());
  }
}
