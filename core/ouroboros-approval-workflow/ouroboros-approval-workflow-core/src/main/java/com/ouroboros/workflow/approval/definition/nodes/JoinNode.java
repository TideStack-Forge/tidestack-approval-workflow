package com.ouroboros.workflow.approval.definition.nodes;

import com.ouroboros.workflow.approval.definition.FlowNode;

/**
 * 合并节点，用于汇聚多个分支。
 * 
 * <p>支持的合并类型：
 * <ul>
 *   <li>OR - 当前稳定设计时能力；等待所有被激活的分支完成（InclusiveGateway join）</li>
 *   <li>AND - 兼容/后续能力；当前轮次不作为稳定设计时 contract 开放</li>
 * </ul>
 * 
 * <p>注意：当前 V2 设计时语义里，分支拆分不使用显式 split node：
 * <ul>
 *   <li>任意节点 outgoing > 1 即可成为分流起点</li>
 *   <li>没有下游 JoinNode 时，多出边默认按 XOR 理解</li>
 *   <li>显式 JoinNode(joinType=OR) 反向界定上游 OR 分支组</li>
 *   <li>当前不通过“全无条件边”或“混合条件边”等隐式规则推导 AND / OR</li>
 * </ul>
 */
public class JoinNode extends FlowNode {
  String joinType;

  @Override
  public String getType() {
    return BuiltInFlowNodeTypes.JOIN;
  }

  public String getJoinType() {
    return joinType;
  }

  public void setJoinType(String joinType) {
    this.joinType = joinType;
  }
}
