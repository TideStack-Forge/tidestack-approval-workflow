package com.ouroboros.workflow.approval.event;

import java.util.Optional;

import com.ouroboros.workflow.approval.model.events.FlowInstanceEvent;
import com.ouroboros.workflow.approval.model.events.FlowTaskEvent;

/** Optional notification handoff at canonical source persistence and the existing business projection boundary. */
public interface FlowBusinessNotificationOwnership {
  Optional<FlowBusinessNotificationOwner> capture(FlowInstanceEvent event);
  Optional<FlowBusinessNotificationOwner> capture(FlowTaskEvent event);
  void requireHandoff(FlowBusinessNotificationOwner owner);
}
