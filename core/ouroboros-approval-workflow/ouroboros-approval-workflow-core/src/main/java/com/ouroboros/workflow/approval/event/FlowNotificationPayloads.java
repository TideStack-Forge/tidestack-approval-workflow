package com.ouroboros.workflow.approval.event;

import java.util.LinkedHashMap;
import java.util.Map;

import com.ouroboros.workflow.approval.model.events.FlowInstanceEvent;
import com.ouroboros.workflow.approval.model.events.FlowTaskEvent;

/** Original approval template contract shared by the legacy sender and the authorized business Event source. */
public final class FlowNotificationPayloads {
  private FlowNotificationPayloads() { }

  public static Map<String, Object> of(FlowTaskEvent event) {
    var payload = new LinkedHashMap<String, Object>();
    payload.put("eventFactId", event.getEventFactId());
    payload.put("flowKey", event.getFlowKey());
    payload.put("instanceId", event.getInstanceId());
    payload.put("taskId", event.getTaskId());
    payload.put("nodeId", event.getNodeId());
    payload.put("taskName", event.getTaskName());
    payload.put("actionName", event.getActionName());
    payload.put("state", event.getState() == null ? null : event.getState().name());
    payload.put("eventTime", event.getEventTime());
    return payload;
  }

  public static Map<String, Object> of(FlowInstanceEvent event) {
    var payload = new LinkedHashMap<String, Object>();
    payload.put("eventFactId", event.getEventFactId());
    payload.put("flowKey", event.getFlowKey());
    payload.put("instanceId", event.getInstanceId());
    payload.put("eventType", event.getEventType() == null ? null : event.getEventType().name());
    payload.put("eventTime", event.getEventTime());
    payload.put("starter", event.getStarter());
    payload.put("lastTaskId", event.getLastTaskId());
    payload.put("outcome", event.getOutcome());
    return payload;
  }
}
