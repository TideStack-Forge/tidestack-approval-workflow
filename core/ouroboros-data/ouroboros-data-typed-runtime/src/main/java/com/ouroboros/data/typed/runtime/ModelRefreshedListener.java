package com.ouroboros.data.typed.runtime;

import com.ouroboros.data.model.DataModelCenter;
import com.ouroboros.data.model.DeferredModelRequestRegistry;

public class ModelRefreshedListener {
  private final DeferredModelRequestRegistry registry;

  public ModelRefreshedListener(DeferredModelRequestRegistry registry) {
    this.registry = registry;
  }

  public void onModelRefreshed() {
    registry.completePending(DataModelCenter::getDataModel);
  }
}
