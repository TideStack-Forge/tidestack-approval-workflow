package com.ouroboros.data.typed.runtime;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import com.ouroboros.data.model.DeferredModelRequestRegistry;
import com.ouroboros.data.model.TypedDataModelCenter;

/**
 * Connects the typed-core deferred model request registry to the Spring runtime lifecycle.
 */
public class DeferredModelRequestRegistryLifecycle {
  private final DeferredModelRequestRegistry registry;

  public DeferredModelRequestRegistryLifecycle(DeferredModelRequestRegistry registry) {
    this.registry = registry;
  }

  @PostConstruct
  void registerAsDefault() {
    TypedDataModelCenter.setDeferredModelRequestRegistry(registry);
  }

  @PreDestroy
  void clearAsDefault() {
    TypedDataModelCenter.clearDeferredModelRequestRegistry(registry);
  }
}
