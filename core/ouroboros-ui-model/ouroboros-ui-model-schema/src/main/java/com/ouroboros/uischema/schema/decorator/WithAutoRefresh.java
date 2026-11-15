package com.ouroboros.uischema.schema.decorator;

import com.ouroboros.uischema.schema.Schema;

public interface WithAutoRefresh extends Schema {

  default Boolean getInitFetch() {
    return getProp(Boolean.class, "initFetch", null);
  }

  default void setInitFetch(Boolean initFetch) {
    setProp("initFetch", initFetch);
  }

  default String getInitFetchOn() {
    return getProp(String.class, "initFetchOn", null);
  }

  default void setInitFetchOn(String initFetchOn) {
    setProp("initFetchOn", initFetchOn);
  }

  default Integer getInterval() {
    return getProp(Integer.class, "interval", null);
  }

  default void setInterval(Integer interval) {
    setProp("interval", interval);
  }

  default Boolean getSilentPolling() {
    return getProp(Boolean.class, "silentPolling", null);
  }

  default void setSilentPolling(Boolean silentPolling) {
    setProp("silentPolling", silentPolling);
  }

  default String getStopAutoRefreshWhen() {
    return getProp(String.class, "stopAutoRefreshWhen", null);
  }

  default void setStopAutoRefreshWhen(String stopAutoRefreshWhen) {
    setProp("stopAutoRefreshWhen", stopAutoRefreshWhen);
  }
}
