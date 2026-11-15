package com.ouroboros.workflow.approval.definition.nodes;

import com.fasterxml.jackson.databind.JsonNode;
import com.ouroboros.workflow.approval.definition.FlowNode;

/**
 * Legacy compatibility shape for persisted definitions created before NoticeNode left the stable node contract.
 */
@Deprecated
public class NoticeNode extends FlowNode {
  JsonNode notice;
  JsonNode channel;
  String content;

  @Override
  public String getType() {
    return BuiltInFlowNodeTypes.NOTICE;
  }

  public JsonNode getNotice() {
    return notice;
  }

  public void setNotice(JsonNode notice) {
    this.notice = notice;
  }

  public JsonNode getChannel() {
    return channel;
  }

  public void setChannel(JsonNode channel) {
    this.channel = channel;
  }

  public String getContent() {
    return content;
  }

  public void setContent(String content) {
    this.content = content;
  }
}
