package com.ouroboros.data.runtime.lifecycle;

import com.ouroboros.util.SPI;

public final class DataModelLifecycleBridges {
  private static final DataModelLifecycleBridge DEFAULT_BRIDGE = new DefaultDataModelLifecycleBridge();

  private DataModelLifecycleBridges() {
  }

  public static DataModelLifecycleBridge primary() {
    return SPI.getCachedReversedServiceStream(DataModelLifecycleBridge.class)
        .findFirst()
        .orElse(DEFAULT_BRIDGE);
  }

  public static void refresh() {
    primary().refresh();
  }
}
