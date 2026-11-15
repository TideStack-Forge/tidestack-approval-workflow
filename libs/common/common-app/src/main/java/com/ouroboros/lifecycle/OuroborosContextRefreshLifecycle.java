package com.ouroboros.lifecycle;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import com.ouroboros.lifecycle.event.*;

public class OuroborosContextRefreshLifecycle {
  public static final class PublishedContextUnavailableException extends IllegalStateException {
    private static final long serialVersionUID = 1L;

    private PublishedContextUnavailableException() {
      super("应用上下文刷新失败，须完成一次成功的完整刷新后再执行业务行为");
    }
  }

  private static final ReentrantReadWriteLock publication = new ReentrantReadWriteLock(true);
  private static boolean publishedContextFailed;

  /** Holds the existing published context stable for one synchronous business execution. */
  public static <T> T withStableContext(Supplier<T> execution) {
    Objects.requireNonNull(execution);
    publication.readLock().lock();
    try {
      if (publishedContextFailed || publication.isWriteLockedByCurrentThread()) {
        throw new PublishedContextUnavailableException();
      }
      return execution.get();
    } finally {
      publication.readLock().unlock();
    }
  }

  public static void publish(Object source, Consumer<OuroborosEvent> publisher) {
    Objects.requireNonNull(publisher);
    if (publication.getReadHoldCount() > 0 || publication.isWriteLockedByCurrentThread()) {
      throw new IllegalStateException("同步业务执行或刷新期间不能再次刷新应用上下文");
    }
    publication.writeLock().lock();
    try {
      publishPhases(source, publisher);
      publishedContextFailed = false;
    } catch (RuntimeException | Error failure) {
      publishedContextFailed = true;
      throw failure;
    } finally {
      publication.writeLock().unlock();
    }
  }

  private static void publishPhases(Object source, Consumer<OuroborosEvent> publisher) {
    publishPhase(source, publisher, "context-refreshing", "正在刷新应用上下文", OuroborosContextRefreshingEvent::new);
    publishPhase(source, publisher, "metadata-refreshing", "正在刷新元数据模型", OuroborosMetadataRefreshingEvent::new);
    publishPhase(source, publisher, "metadata-refreshed", "元数据模型刷新完成", OuroborosMetadataRefreshedEvent::new);
    publishPhase(source, publisher, "model-refreshing", "正在刷新业务模型", OuroborosModelRefreshingEvent::new);
    publishPhase(source, publisher, "model-refreshed", "业务模型刷新完成", OuroborosModelRefreshedEvent::new);
    publishPhase(source, publisher, "context-refreshed", "应用上下文刷新完成", OuroborosContextRefreshedEvent::new);
  }

  private static void publishPhase(Object source,
                                   Consumer<OuroborosEvent> publisher,
                                   String phase,
                                   String message,
                                   Function<Object, OuroborosEvent> eventFactory) {
    publisher.accept(OuroborosBootstrapProgressEvent.phaseSwitch(source, phase, message));
    try {
      publisher.accept(eventFactory.apply(source));
    } catch (RuntimeException e) {
      publishFallbackFailedEvent(source, publisher, phase, e);
      throw e;
    }
  }

  private static void publishFallbackFailedEvent(Object source,
                                                 Consumer<OuroborosEvent> publisher,
                                                 String phase,
                                                 RuntimeException failure) {
    try {
      publisher.accept(OuroborosBootstrapFailedEvent.fallback(source, phase, null, failure));
    } catch (RuntimeException failedEventPublishFailure) {
      failure.addSuppressed(failedEventPublishFailure);
    }
  }
}
