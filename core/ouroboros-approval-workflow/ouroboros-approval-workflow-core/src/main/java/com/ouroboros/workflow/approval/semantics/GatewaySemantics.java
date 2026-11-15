package com.ouroboros.workflow.approval.semantics;

import com.ouroboros.workflow.approval.definition.condition.Condition;
import com.ouroboros.workflow.approval.definition.v2.FlowEdgeV2;

import java.util.List;

/**
 * 旧 split 网关语义兼容 helper，供 deferred / compatibility 场景共享。
 *
 * <p>该类不再定义 Stable V2 默认主链的 split 语义，只负责保留旧引擎/旧图结构里
 * “按出边条件分布隐式推导 AND/XOR/OR” 的兼容规则。默认主链应基于 canonical V2 图语义：
 * 没有显式 {@code JoinNode(OR)} 时默认按 XOR 理解；只有显式 OR join 才表示允许多路命中。
 *
 * <p>决策规则：
 * <ul>
 *   <li>全部出边无条件（{@code condition == null} 或表达式为空）→ {@code AND}</li>
 *   <li>全部出边有条件 → {@code XOR}</li>
 *   <li>部分有条件、部分无条件 → {@code OR}</li>
 * </ul>
 */
public final class GatewaySemantics {

  private GatewaySemantics() {
  }

  /**
   * 推导分裂网关类型。
   *
   * <p>仅返回语义类型，不创建引擎对象。
   *
   * @param outgoingEdges 当前节点的出边集合
   * @return {@link SplitType#AND}、{@link SplitType#XOR} 或 {@link SplitType#OR}
   */
  public static SplitType inferSplitType(List<FlowEdgeV2> outgoingEdges) {
    long conditionalCount = outgoingEdges.stream()
        .filter(edge -> {
          Condition condition = edge.getCondition();
          if (condition == null) {
            return false;
          }
          String expression = condition.toExpression();
          return expression != null && !expression.trim().isEmpty();
        })
        .count();
    long totalCount = outgoingEdges.size();

    if (conditionalCount == 0) {
      return SplitType.AND;
    }
    if (conditionalCount == totalCount) {
      return SplitType.XOR;
    }
    return SplitType.OR;
  }
}
