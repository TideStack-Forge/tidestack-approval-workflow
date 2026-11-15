package com.ouroboros.workflow.approval.definition.condition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import org.flowable.common.engine.impl.el.ExpressionManager;
import org.flowable.common.engine.impl.variable.MapDelegateVariableContainer;
import org.flowable.engine.impl.el.ProcessExpressionManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.ouroboros.util.Converter;
import com.ouroboros.workflow.approval.definition.codec.FlowDefinitionCodecException;

/**
 * 条件操作符生成表达式的真实求值验证。
 *
 * <p>引擎是 Flowable，条件最终由 JUEL 求值。此前只断言过"表达式字符串长什么样"，
 * 从未确认它能否被引擎解析、语义是否与操作符名一致——这直接掩盖了一类致命缺陷：
 * 字符串字面量没有引号时，`level.equals(特级)` 里的 `特级` 会被 JUEL 当作变量名，
 * <b>字符串相等条件恒假且不报错</b>。
 *
 * <p>本测试用与 {@code FlowableFlowDefinitionService.getBranchEdgeFilter} 相同的求值路径
 * （{@code ProcessExpressionManager} + {@code MapDelegateVariableContainer} + {@code Converter.toBoolean}），
 * 每个操作符同时断言命中与不命中两种取值。
 */
class ConditionExpressionEvaluationTest {

  private static ExpressionManager expressionManager;

  @BeforeAll
  static void setUp() {
    expressionManager = new ProcessExpressionManager(new HashMap<Object, Object>());
  }

  @AfterAll
  static void tearDown() {
    expressionManager = null;
  }

  @Test
  void stringEqualityComparesAgainstAStringLiteralNotAVariable() {
    assertThat(eval(OpEnum.equal, "level", "特级", "特级")).isTrue();
    assertThat(eval(OpEnum.equal, "level", "特级", "一级")).isFalse();
    assertThat(eval(OpEnum.notEqual, "level", "特级", "一级")).isTrue();
    assertThat(eval(OpEnum.notEqual, "level", "特级", "特级")).isFalse();
  }

  @Test
  void stringLiteralContainingQuoteStaysASingleLiteral() {
    assertThat(eval(OpEnum.equal, "name", "O'Brien", "O'Brien")).isTrue();
    assertThat(eval(OpEnum.equal, "name", "O'Brien", "OBrien")).isFalse();
  }

  @Test
  void selectEqualsBehavesLikeStringEquality() {
    assertThat(eval(OpEnum.selectEquals, "level", "特级", "特级")).isTrue();
    assertThat(eval(OpEnum.selectEquals, "level", "特级", "一级")).isFalse();
    assertThat(eval(OpEnum.selectNotEquals, "level", "特级", "一级")).isTrue();
  }

  @Test
  void numericComparisonOperatorsCompareNumerically() {
    assertThat(eval(OpEnum.greater, "amount", 1000, 1500)).isTrue();
    assertThat(eval(OpEnum.greater, "amount", 1000, 1000)).isFalse();
    assertThat(eval(OpEnum.greaterEqual, "amount", 1000, 1000)).isTrue();
    assertThat(eval(OpEnum.less, "amount", 1000, 500)).isTrue();
    assertThat(eval(OpEnum.lessEqual, "amount", 1000, 1000)).isTrue();
    assertThat(eval(OpEnum.lessEqual, "amount", 1000, 1500)).isFalse();
  }

  @Test
  void betweenChecksInclusiveBounds() {
    List<Object> bounds = Arrays.asList(100, 200);
    assertThat(eval(OpEnum.between, "amount", bounds, 150)).isTrue();
    assertThat(eval(OpEnum.between, "amount", bounds, 100)).isTrue();
    assertThat(eval(OpEnum.between, "amount", bounds, 200)).isTrue();
    assertThat(eval(OpEnum.between, "amount", bounds, 250)).isFalse();
    assertThat(eval(OpEnum.notBetween, "amount", bounds, 250)).isTrue();
    assertThatThrownBy(() -> eval(OpEnum.between, "amount", Arrays.asList(1), 1))
        .isInstanceOf(FlowDefinitionCodecException.class);
  }

  @Test
  void betweenDoesNotMutateTheCallerSuppliedBounds() {
    List<Object> bounds = new java.util.ArrayList<>(Arrays.asList(200, 100));

    eval(OpEnum.between, "amount", bounds, 150);

    assertThat(bounds).containsExactly(200, 100);
  }

  @Test
  void emptyChecksTreatOnlyNullAsEmpty() {
    assertThat(eval(OpEnum.isEmpty, "memo", null, null)).isTrue();
    assertThat(eval(OpEnum.isEmpty, "memo", null, "")).isFalse();
    assertThat(eval(OpEnum.isNotEmpty, "memo", null, "有内容")).isTrue();
    assertThat(eval(OpEnum.isNotEmpty, "memo", null, null)).isFalse();
  }

  @Test
  void textMatchingOperatorsUseStringLiterals() {
    assertThat(eval(OpEnum.like, "name", "审批", "采购审批流")).isTrue();
    assertThat(eval(OpEnum.like, "name", "报销", "采购审批流")).isFalse();
    assertThat(eval(OpEnum.notLike, "name", "报销", "采购审批流")).isTrue();
    assertThat(eval(OpEnum.startsWith, "name", "采购", "采购审批流")).isTrue();
    assertThat(eval(OpEnum.startsWith, "name", "审批", "采购审批流")).isFalse();
    // 字段值 "采购审批流" 的结尾是 "审批流" 而不是 "流程"
    assertThat(eval(OpEnum.endsWith, "name", "审批流", "采购审批流")).isTrue();
    assertThat(eval(OpEnum.endsWith, "name", "流程", "采购审批流")).isFalse();
  }

  /**
   * 操作符语义是"字段值属于给定集合"，表达式必须是"集合包含字段值"。
   *
   * <p>命中与不命中都要断言：只断言"能求值为 true"不足以发现参数序写反——
   * 写反的形态在 JUEL 下要么解析失败、要么恒假。
   */
  @Test
  void selectAnyInMeansTheFieldValueBelongsToTheGivenSet() {
    List<Object> levels = Arrays.asList("特级", "一级");
    assertThat(eval(OpEnum.selectAnyIn, "level", levels, "特级")).isTrue();
    assertThat(eval(OpEnum.selectAnyIn, "level", levels, "二级")).isFalse();
    assertThat(eval(OpEnum.selectNotAnyIn, "level", levels, "二级")).isTrue();
    assertThat(eval(OpEnum.selectNotAnyIn, "level", levels, "特级")).isFalse();
  }

  @Test
  void collectionOperatorsRejectNonListOperands() {
    assertThatThrownBy(() -> eval(OpEnum.selectAnyIn, "level", "特级", "特级"))
        .isInstanceOf(FlowDefinitionCodecException.class)
        .hasMessageContaining("必须是数组");
  }

  @Test
  void nullFieldsNeverMatchEvenForNegativeOperators() {
    assertThat(eval(OpEnum.equal, "level", "特级", null)).isFalse();
    assertThat(eval(OpEnum.notEqual, "level", "特级", null)).isFalse();
    assertThat(eval(OpEnum.selectAnyIn, "level", Arrays.asList("特级"), null)).isFalse();
    assertThat(eval(OpEnum.selectNotAnyIn, "level", Arrays.asList("特级"), null)).isFalse();
  }

  /**
   * 按引擎的求值契约执行：表达式的承载形态是 {@code ${expr}}。
   *
   * <p>裸表达式（如 {@code (level != null && level == 'x')}）交给
   * {@code createExpression} 会被原样当成字面量返回，既不求值也不报错——
   * 这个坑本身值得记一笔：判断"条件是否生效"必须看求值结果，不能看字符串长相。
   */
  /**
   * 条件对象（{@link CompareCondition}）是运行时的真实入口：转译器调 {@code toExpression()}，
   * 引擎求值的是它的产出。这里直接断言这一层的求值结果——只测 {@code OpEnum} 会漏掉
   * 条件对象自己再加工右值的情况（历史上它就曾给字符串右值补过一层双引号）。
   */
  @Test
  void compareConditionIsTheSinglePlaceThatRendersTheRightOperand() {
    CompareCondition condition = new CompareCondition()
        .setLeft(new CompareCondition.Left().setType("field").setField("level"))
        .setOp("not_equal")
        .setRight("三级");

    assertThat(condition.toExpression()).isEqualTo("(level != null && level != '三级')");
    assertThat(evaluateExpression(condition.toExpression(), "level", "三级")).isFalse();
    assertThat(evaluateExpression(condition.toExpression(), "level", "一级")).isTrue();
  }

  @Test
  void compareConditionRendersNumericAndCollectionOperands() {
    CompareCondition numeric = new CompareCondition()
        .setLeft(new CompareCondition.Left().setType("field").setField("amount"))
        .setOp("greater")
        .setRight(1000);
    assertThat(evaluateExpression(numeric.toExpression(), "amount", 1500)).isTrue();

    CompareCondition setMembership = new CompareCondition()
        .setLeft(new CompareCondition.Left().setType("field").setField("level"))
        .setOp("select_any_in")
        .setRight(Arrays.asList("特级", "一级"));
    assertThat(evaluateExpression(setMembership.toExpression(), "level", "特级")).isTrue();
    assertThat(evaluateExpression(setMembership.toExpression(), "level", "三级")).isFalse();
  }

  private boolean eval(OpEnum op, String field, Object right, Object fieldValue) {
    MapDelegateVariableContainer variables = new MapDelegateVariableContainer();
    variables.setVariable(field, fieldValue);
    return evaluateExpression(op.generateExpression(field, right), field, fieldValue);
  }

  private boolean evaluateExpression(String rawExpression, String field, Object fieldValue) {
    MapDelegateVariableContainer variables = new MapDelegateVariableContainer();
    variables.setVariable(field, fieldValue);
    Object value = expressionManager
        .createExpression("${" + rawExpression + "}")
        .getValue(variables);
    return Converter.toBoolean(value);
  }
}
