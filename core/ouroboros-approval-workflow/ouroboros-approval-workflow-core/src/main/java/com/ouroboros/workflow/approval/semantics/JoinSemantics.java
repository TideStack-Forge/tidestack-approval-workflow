package com.ouroboros.workflow.approval.semantics;

/**
 * 网关语义决策层，供 Flowable / Activiti6 共享。
 *
 * <p>该类只负责归一化 join 网关语义类型，不创建任何引擎特定对象。
 * 输入契约：{@code joinType} 为内置引擎转译层收到的语义标识。输出契约：仅返回 {@link JoinType#AND} 或
 * {@link JoinType#OR}。{@code AND} 是延后开放的引擎兼容语义，不属于 Stable V2 definition boundary。
 *
 * <p>决策规则：
 * <ul>
 *   <li>{@code AND} → ParallelGateway</li>
 *   <li>{@code OR} → InclusiveGateway</li>
 *   <li>其他值默认归一化为 {@code OR}</li>
 * </ul>
 */
public final class JoinSemantics {

  private JoinSemantics() {
  }

  /**
   * 归一化 join 网关类型。
   *
   * <p>AND 对应 ParallelGateway，OR 对应 InclusiveGateway。
   *
   * @param joinType 转译层收到的 join 类型
   * @return {@link JoinType#AND} 或 {@link JoinType#OR}
   */
  public static JoinType normalizeJoinType(String joinType) {
    JoinType normalizedType = JoinType.fromString(joinType);
    if (normalizedType == JoinType.AND) {
      return JoinType.AND;
    }
    return JoinType.OR;
  }
}
