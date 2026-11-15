package com.ouroboros.workflow.approval.event;

import com.ouroboros.workflow.approval.model.events.FlowTaskEvent;

/** Captures engine-owned business identity while the source task still exists. */
public interface FlowTaskFactContextProvider {
  void capture(FlowTaskEvent event);
}
