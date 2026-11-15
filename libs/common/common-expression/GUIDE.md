# Common Expression - 表达式引擎指南

本文档描述了 `common-expression` 模块的核心功能和使用方法。该模块基于 Spring Expression Language (SpEL) 提供了强大的表达式求值能力。

## 模块概述

`common-expression` 是一个表达式引擎模块,提供了以下核心功能:

- 基于 Spring SpEL 的表达式求值
- 模板表达式支持 (`${ ... }` 语法)
- 可扩展的上下文变量机制
- 内置工具类支持 (Dates, Converter)
- 类型安全的结果处理

## 核心类和接口

### 1. EL - 表达式求值入口

路径: `com.ouroboros.expression.EL`

表达式引擎的主要入口类,提供静态方法进行表达式求值。

**核心方法:**

```java
// 基本求值,返回 Either<Throwable, Object>
Either<Throwable, Object> result = EL.eval("${ a + b }", context);

// 类型安全的求值,自动转换结果类型
Either<Throwable, Integer> result = EL.eval(Integer.class, "${ a + b }", context);

// 包装表达式为模板格式
String wrapped = EL.wrapExpression("a + b"); // 返回 "${ a + b }"
```

### 2. ExpressionContextContributor - 上下文扩展接口

路径: `com.ouroboros.expression.ExpressionContextContributor`

SPI 接口,用于向表达式上下文注入全局变量和工具类。

**实现示例:**

```java
public class MyContextContributor implements ExpressionContextContributor {
  @Override
  public Map<String, Object> getBindings() {
    Map<String, Object> map = new HashMap<>();
    map.put("MyUtil", new MyUtil());
    map.put("constant", "value");
    return map;
  }
}
```

**SPI 注册:**

在 `META-INF/services/com.ouroboros.expression.ExpressionContextContributor` 中添加:
```
com.yourpackage.MyContextContributor
```

### 3. ContextWrappingHelper - 上下文合并助手

路径: `com.ouroboros.expression.ContextWrappingHelper`

只读的 Map 实现,用于合并全局上下文和局部变量。全局上下文优先级更高。

**特点:**
- 不可修改(抛出 `UnsupportedOperationException`)
- wrapper 中的变量会覆盖 wrapped 中的同名变量
- 提供完整的 Map 接口实现

## 使用示例

### 基本表达式求值

```java
Map<String, Object> context = new HashMap<>();
context.put("a", 10);
context.put("b", 20);

// 简单算术表达式
Either<Throwable, Object> result = EL.eval("${ a + b }", context);
result.forEach(value -> System.out.println(value)); // 输出: 30

// 类型安全求值
Either<Throwable, Integer> intResult = EL.eval(Integer.class, "${ a * 2 }", context);
intResult.forEach(value -> System.out.println(value)); // 输出: 20
```

### 使用内置工具类

模块默认提供 `Dates` 和 `Converter` 工具类 (通过 `UtilsContextContributor`):

```java
// 使用 Dates 工具
Either<Throwable, Object> result = EL.eval(
    "${ Dates.now() }",
    Collections.emptyMap()
);

// 使用 Converter 工具
Map<String, Object> context = new HashMap<>();
context.put("value", "123");
Either<Throwable, Object> result = EL.eval(
    "${ Converter.toInt(value) }",
    context
);
```

### 访问 Map 和对象属性

```java
Map<String, Object> context = new HashMap<>();
Map<String, Object> user = new HashMap<>();
user.put("name", "张三");
user.put("age", 25);
context.put("user", user);

// 访问 Map 属性
Either<Throwable, String> name = EL.eval(
    String.class,
    "${ user.name }",
    context
);
name.forEach(n -> System.out.println(n)); // 输出: 张三

// 访问不存在的属性返回 null (不抛异常)
Either<Throwable, Object> result = EL.eval(
    "${ user.nonExistent }",
    context
);
result.forEach(v -> System.out.println(v)); // 输出: null
```

### 复杂表达式

```java
Map<String, Object> context = new HashMap<>();
context.put("score", 85);
context.put("threshold", 60);

// 条件表达式
Either<Throwable, String> result = EL.eval(
    String.class,
    "${ score >= threshold ? '及格' : '不及格' }",
    context
);
result.forEach(s -> System.out.println(s)); // 输出: 及格

// 方法调用
context.put("text", "hello world");
Either<Throwable, String> upper = EL.eval(
    String.class,
    "${ text.toUpperCase() }",
    context
);
upper.forEach(s -> System.out.println(s)); // 输出: HELLO WORLD
```

### 错误处理

```java
Map<String, Object> context = new HashMap<>();
context.put("a", "not a number");

// 表达式求值失败时返回 Left
Either<Throwable, Integer> result = EL.eval(
    Integer.class,
    "${ a + 10 }",
    context
);

// 处理错误
result
    .peek(value -> System.out.println("成功: " + value))
    .peekLeft(error -> System.out.println("失败: " + error.getMessage()));

// 提供默认值
Integer value = result.getOrElse(0);
```

### 扩展全局上下文

创建自定义工具类:

```java
public class MathUtils {
  public int square(int n) {
    return n * n;
  }

  public double average(List<Number> numbers) {
    return numbers.stream()
        .mapToDouble(Number::doubleValue)
        .average()
        .orElse(0.0);
  }
}

public class CustomContextContributor implements ExpressionContextContributor {
  @Override
  public Map<String, Object> getBindings() {
    Map<String, Object> map = new HashMap<>();
    map.put("Math", new MathUtils());
    return map;
  }
}
```

注册 SPI (在 `META-INF/services/com.ouroboros.expression.ExpressionContextContributor`):
```
com.yourpackage.CustomContextContributor
```

使用自定义工具:
```java
Map<String, Object> context = new HashMap<>();
context.put("n", 5);

Either<Throwable, Integer> result = EL.eval(
    Integer.class,
    "${ Math.square(n) }",
    context
);
result.forEach(v -> System.out.println(v)); // 输出: 25
```

## 编译计划与受限条件

正式接口见 [EL](src/main/java/com/ouroboros/expression/EL.java)、[ExpressionPlan](src/main/java/com/ouroboros/expression/ExpressionPlan.java) 和 [PredicatePlan](src/main/java/com/ouroboros/expression/PredicatePlan.java)。它们与 `eval` 使用同一表达式提供者。

- `EL.compile(expression)` 返回值计划，包含求值、可识别的 selector path 和存在性判断。简单选择器区分“键不存在”与“键存在但值为 null”；无法识别为选择器的复合表达式，其存在性判断返回 true，求值失败仍通过 Left 返回，不能把它当作任意表达式的缺失检测器。
- `EL.compilePredicate(expression, roots)` 限制可读根变量，收集属性/字面量索引访问路径，并在求值时要求布尔结果。`evalPredicate` 与 `validatePredicate` 提供受限条件的求值和语法检查。
- 当前 `SpElImpl` 的受限条件只接受单个 `${ ... }`，通过 AST 白名单拒绝方法调用、类型引用等节点，并以 `SimpleEvaluationContext` 求值；不会注入普通表达式的全局工具上下文。不要用普通 `eval` 替代该边界。
- 替换 EL 提供者时，上述新增能力的默认实现返回 UnsupportedOperationException；提供者需显式实现相应契约，调用者继续处理 Left。

下文完整 SpEL 语法说明针对普通 `eval`，不适用于受限条件。

## SpEL 语法支持

模块支持完整的 Spring Expression Language 语法:

- **算术运算**: `+`, `-`, `*`, `/`, `%`, `^`
- **比较运算**: `<`, `>`, `<=`, `>=`, `==`, `!=`
- **逻辑运算**: `and`, `or`, `not`, `!`
- **三元运算**: `condition ? true : false`
- **Elvis 运算**: `value ?: default`
- **安全导航**: `object?.property` (避免 NPE)
- **集合选择**: `list.?[condition]` (过滤)
- **集合投影**: `list.![expression]` (映射)
- **方法调用**: `object.method(args)`
- **静态方法**: `T(java.lang.Math).random()`

详细语法参考 [Spring Expression Language 文档](https://docs.spring.io/spring-framework/reference/core/expressions.html)。

## 使用建议

1. **统一使用 EL.eval** - 避免直接使用 SpElImpl
2. **类型安全** - 优先使用带类型参数的 `EL.eval(Class<T>, ...)`
3. **错误处理** - 始终处理 Either 的 Left 情况
4. **全局工具** - 常用工具类通过 ExpressionContextContributor 注入,避免在每个 context 中重复添加
5. **表达式包装** - 使用 `EL.wrapExpression()` 确保表达式格式正确
6. **空值安全** - 利用 NullReturningPropertyAccessor,访问不存在的属性返回 null 而非异常

## 注意事项

1. **不要修改上下文** - ContextWrappingHelper 不支持修改操作
2. **性能考虑** - 需要复用编译计划时使用正式入口 `EL.compile` 或 `EL.compilePredicate`，不要在消费模块另建 SpEL 解析链
3. **安全性** - 避免直接使用用户输入作为表达式,可能导致代码注入
4. **SPI 优先级** - 多个 ExpressionContextContributor 会按 SPI 加载顺序合并,后加载的优先级更高
5. **类型转换** - SpEL 会自动进行类型转换,但复杂类型可能失败,建议使用显式转换

## 内置工具类

### Dates (来自 ouroboros-util)

```java
// 日期格式化
"${ Dates.format(date, 'yyyy-MM-dd') }"

// 获取当前时间
"${ Dates.now() }"

// 日期计算
"${ Dates.addDays(date, 7) }"
```

### Converter (来自 ouroboros-util)

```java
// 类型转换
"${ Converter.toInt(stringValue) }"
"${ Converter.toDouble(stringValue) }"
"${ Converter.toBoolean(stringValue) }"
```

更多工具类方法参考 [ouroboros-util/GUIDE.md](../../util/ouroboros-util/GUIDE.md)。
