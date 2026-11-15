package com.ouroboros.workflow.approval.definition.condition;

import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonCreator;

import com.ouroboros.workflow.approval.definition.codec.FlowDefinitionCodecException;

public enum OpEnum {
  equal("equal", OpEnum::equalOp),
  notEqual("not_equal", OpEnum::notEqualOp),
  greater("greater", OpEnum::greaterOp),
  greaterEqual("greater_or_equal", OpEnum::greaterOrEqualOp),
  less("less", OpEnum::lessOp),
  lessEqual("less_or_equal", OpEnum::lessOrEqualOp),
  between("between", OpEnum::betweenOp),
  notBetween("not_between", OpEnum::notBetweenOp),
  isEmpty("is_empty", OpEnum::isEmptyOp),
  isNotEmpty("is_not_empty", OpEnum::isNotEmptyOp),
  like("like", OpEnum::likeOp),
  notLike("not_like", OpEnum::notLikeOp),
  startsWith("starts_with", OpEnum::startsWithOp),
  endsWith("ends_with", OpEnum::endsWithOp),
  selectEquals("select_equals", OpEnum::equalOp),
  selectNotEquals("select_not_equals", OpEnum::notEqualOp),
  selectAnyIn("select_any_in", OpEnum::selectAnyInOp),
  selectNotAnyIn("select_not_any_in", OpEnum::selectNotAnyInOp);
  // TODO: 补全所有操作符

  static String equalOp(String left, Object right) {
    return String.format("(%s != null && %s == %s)", left, left, literal(right));
  }

  static String notEqualOp(String left, Object right) {
    return String.format("(%s != null && %s != %s)", left, left, literal(right));
  }

  static String greaterOp(String left, Object right) {
    return String.format("(%s != null && %s > %s)", left, left, literal(right));
  }

  static String greaterOrEqualOp(String left, Object right) {
    return String.format("(%s != null && %s >= %s)", left, left, literal(right));
  }

  static String lessOp(String left, Object right) {
    return String.format("(%s != null && %s < %s)", left, left, literal(right));
  }

  static String lessOrEqualOp(String left, Object right) {
    return String.format("(%s != null && %s <= %s)", left, left, literal(right));
  }

  /**
   * 区间比较：right 必须是两个元素的列表（下界、上界）。
   *
   * <p>不再 `Collections.sort(rightList)`：那会就地修改调用方传入的集合，
   * 而调用方（条件解码）持有的是流程定义里的对象。改为在本地副本上排序。
   */
  static String betweenOp(String left, Object right) {
    List<Object> bounds = sortedBounds(right);
    return String.format("(%s != null && %s >= %s && %s <= %s)", left, left,
        literal(bounds.get(0)), left, literal(bounds.get(1)));
  }

  static String notBetweenOp(String left, Object right) {
    List<Object> bounds = sortedBounds(right);
    return String.format("(%s != null && (%s < %s || %s > %s))", left, left,
        literal(bounds.get(0)), left, literal(bounds.get(1)));
  }

  static String isEmptyOp(String left, Object right) {
    return String.format("(%s == null)", left);
  }

  static String isNotEmptyOp(String left, Object right) {
    return String.format("(%s != null)", left);
  }

  static String likeOp(String left, Object right) {
    return String.format("(%s != null && %s.contains(%s))", left, left, literal(right));
  }

  static String notLikeOp(String left, Object right) {
    return String.format("(%s != null && !%s.contains(%s))", left, left, literal(right));
  }

  static String startsWithOp(String left, Object right) {
    return String.format("(%s != null && %s.startsWith(%s))", left, left, literal(right));
  }

  static String endsWithOp(String left, Object right) {
    return String.format("(%s != null && %s.endsWith(%s))", left, left, literal(right));
  }

  /**
   * 字段值属于给定集合。
   *
   * <p>不能用集合字面量 + {@code contains} 表达：Flowable 的 JUEL 方言既不支持
   * {@code {a,b}}（词法错误），也不支持 {@code in} 运算符，而 {@code [a,b]} 在 EL 里是
   * 数组、没有 {@code contains} 方法。唯一可移植的形态是等值比较的析取链——
   * 已用引擎实际求值验证命中与不命中两种情况。
   */
  static String selectAnyInOp(String left, Object right) {
    return String.format("(%s != null && (%s))", left, equalityChain(left, right, " || "));
  }

  static String selectNotAnyInOp(String left, Object right) {
    return String.format("(%s != null && !(%s))", left, equalityChain(left, right, " || "));
  }

  private static String equalityChain(String left, Object right, String separator) {
    return listValues(right).stream()
        .map(value -> String.format("%s == %s", left, literal(value)))
        .collect(Collectors.joining(separator));
  }

  /**
   * 把右侧操作数渲染成 JUEL 字面量。
   *
   * <p>此前字符串直接以 `%s` 拼进表达式，生成的是 `level.equals(特级)`——JUEL 把 `特级`
   * 当作变量名解析，条件恒假且不报错。字符串必须带引号，且要转义内嵌引号与反斜杠。
   */
  private static String literal(Object value) {
    if (value == null) {
      return "null";
    }
    if (value instanceof Number || value instanceof Boolean) {
      return String.valueOf(value);
    }
    // 列表作为等值比较的标量没有意义：集合类操作符走 equalityChain，不经过这里

    return "'" + String.valueOf(value)
        .replace("\\", "\\\\")
        .replace("'", "\\'") + "'";
  }

  private static List<?> listValues(Object value) {
    if (!(value instanceof List<?> list)) {
      throw new FlowDefinitionCodecException(
          "集合类操作符的右操作数必须是数组，实际为: "
              + (value == null ? "null" : value.getClass().getSimpleName()));
    }
    return list;
  }

  /** 取区间下上界：不改动调用方集合，非二元列表直接报错而不是 IndexOutOfBounds。 */
  private static List<Object> sortedBounds(Object right) {
    if (!(right instanceof List<?> list) || list.size() != 2) {
      throw new FlowDefinitionCodecException(
          "区间操作符的右操作数必须是两个元素的数组，实际为: "
              + (right == null ? "null" : right.getClass().getSimpleName()));
    }
    List<Object> bounds = new java.util.ArrayList<>(list);
    bounds.sort(java.util.Comparator.comparing(String::valueOf));
    return bounds;
  }

  private final String opName;
  private final BiFunction<String, Object, String> opFunction;

  OpEnum(String opName, BiFunction<String, Object, String> opFunction) {
    this.opName = opName;
    this.opFunction = opFunction;
  }

  public String getOpName() {
    return opName;
  }

  public String generateExpression(String left, Object right) {
    return opFunction.apply(left, right);
  }

  /**
   * 按操作符名解析枚举。
   *
   * <p>只接受本枚举登记的操作符名（大小写不敏感）。未登记的操作符必须显式失败：此前的实现把未知值
   * 静默降级为 {@link #equal}，会让 {@code {"op":"greaterThan"}} 编译成 {@code amount == 1000}，
   * 条件路由走错分支且没有任何异常或日志。
   *
   * @throws FlowDefinitionCodecException 操作符为空或未登记
   */
  @JsonCreator
  public static OpEnum getValue(String opName) {
    if (opName == null || opName.isBlank()) {
      throw new FlowDefinitionCodecException("条件操作符不能为空，受支持的操作符: " + supportedOpNames());
    }
    for (OpEnum opEnum : OpEnum.values()) {
      if (opEnum.getOpName().equalsIgnoreCase(opName)) {
        return opEnum;
      }
    }
    throw new FlowDefinitionCodecException(
        "未知的条件操作符: " + opName + "，受支持的操作符: " + supportedOpNames());
  }

  private static String supportedOpNames() {
    return Arrays.stream(values()).map(OpEnum::getOpName).collect(Collectors.joining("/"));
  }
}
