package com.ouroboros.workflow.approval.definition.v2;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ouroboros.workflow.approval.definition.condition.Condition;

@JsonIgnoreProperties(ignoreUnknown = true)
public class FlowEdgeV2 {
  String id;
  FlowEdgeType type;
  String sourceId;
  String targetId;
  /**
   * V1 分支名（{@code branches[].name}）在转换时落到这里。
   *
   * <p>不是稳定 V2 contract（见 gateway-join-semantics §边语义）：V2 语义只由
   * {@code type}/{@code sourceId}/{@code targetId} 与 {@code condition}/{@code order} 表达。
   * 保留字段是为了让 V1 往返不丢信息，消费方不得据此判断分支语义。
   */
  String name;
  Condition condition;
  Integer order;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public FlowEdgeType getType() {
    return type;
  }

  public void setType(FlowEdgeType type) {
    this.type = type;
  }

  public String getSourceId() {
    return sourceId;
  }

  public void setSourceId(String sourceId) {
    this.sourceId = sourceId;
  }

  public String getTargetId() {
    return targetId;
  }

  public void setTargetId(String targetId) {
    this.targetId = targetId;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public Condition getCondition() {
    return condition;
  }

  public void setCondition(Condition condition) {
    this.condition = condition;
  }

  public Integer getOrder() {
    return order;
  }

  public void setOrder(Integer order) {
    this.order = order;
  }
}
