package com.ouroboros.workflow.approval.definition.condition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.ouroboros.workflow.approval.definition.codec.FlowDefinitionCodecException;

/**
 * 条件操作符解析：只接受登记词表，未登记取值必须显式失败。
 *
 * <p>此前 {@code getValue} 对未知操作符返回 {@code equal}，使 {@code greaterThan} 这类未登记取值
 * 被编译成等值判断，条件路由静默走错分支。
 */
class OpEnumTest {

  @Test
  void resolvesRegisteredOperatorsCaseInsensitively() {
    assertThat(OpEnum.getValue("greater")).isEqualTo(OpEnum.greater);
    assertThat(OpEnum.getValue("GREATER")).isEqualTo(OpEnum.greater);
    assertThat(OpEnum.getValue("less_or_equal")).isEqualTo(OpEnum.lessEqual);
    assertThat(OpEnum.getValue("select_any_in")).isEqualTo(OpEnum.selectAnyIn);
  }

  @Test
  void rejectsUnregisteredCamelCaseOperatorInsteadOfDowngradingToEqual() {
    assertThatThrownBy(() -> OpEnum.getValue("greaterThan"))
        .isInstanceOf(FlowDefinitionCodecException.class)
        .hasMessageContaining("greaterThan")
        .hasMessageContaining("greater");
  }

  @Test
  void rejectsUnknownOperatorInsteadOfDowngradingToEqual() {
    assertThatThrownBy(() -> OpEnum.getValue("camunda_script"))
        .isInstanceOf(FlowDefinitionCodecException.class)
        .hasMessageContaining("camunda_script");
  }

  @Test
  void rejectsEmptyOperator() {
    assertThatThrownBy(() -> OpEnum.getValue(" "))
        .isInstanceOf(FlowDefinitionCodecException.class);
    assertThatThrownBy(() -> OpEnum.getValue(null))
        .isInstanceOf(FlowDefinitionCodecException.class);
  }

  @Test
  void generatedExpressionReflectsTheResolvedOperator() {
    assertThat(OpEnum.getValue("greater").generateExpression("amount", 1000))
        .isEqualTo(OpEnum.greater.generateExpression("amount", 1000))
        .isNotEqualTo(OpEnum.equal.generateExpression("amount", 1000));
  }

  @Test
  void everyRegisteredOperatorIsResolvableByItsOwnName() {
    for (OpEnum op : OpEnum.values()) {
      assertThat(OpEnum.getValue(op.getOpName())).isEqualTo(op);
    }
  }
}
