package com.ouroboros.workflow.approval.definition.condition;

public class CompareCondition implements Condition {
  private Left left;
  private String op;
  private Object right;

  public Left getLeft() {
    return left;
  }

  public CompareCondition setLeft(Left left) {
    this.left = left;
    return this;
  }

  public String getOp() {
    return op;
  }

  public CompareCondition setOp(String op) {
    this.op = op;
    return this;
  }

  public Object getRight() {
    return right;
  }

  public CompareCondition setRight(Object right) {
    this.right = right;
    return this;
  }

  /**
   * 生成条件表达式。
   *
   * <p>{@code right} 原样交给 {@link OpEnum#generateExpression}——字面量渲染（字符串加引号并转义）
   * 由操作符统一负责。这里此前会**预先给字符串右值加双引号**，与操作符的引号叠加成
   * {@code level == '"特级"'}，使字符串条件在 JUEL 下恒假；这也是"条件静默失效"的主因。
   *
   * <p>{@code left.type} 目前不参与渲染：字段与字面量的类型对齐由调用方（设计器产出）保证。
   * 时间/日期类型按字符串字面量比较（ISO-8601 可直接比较大小），如需专门的类型转换，
   * 应在 {@link OpEnum#literal} 一处扩展，而不是在条件对象里分支。
   */
  @Override
  public String toExpression() {
    return operator().generateExpression(getLeft().getField(), getRight());
  }

  public OpEnum operator() {
    return OpEnum.getValue(getOp());
  }

  public static class Left {
    private String type;
    private String field;

    public String getType() {
      return type;
    }

    public Left setType(String type) {
      this.type = type;
      return this;
    }

    public String getField() {
      return field;
    }

    public Left setField(String field) {
      this.field = field;
      return this;
    }
  }
}
