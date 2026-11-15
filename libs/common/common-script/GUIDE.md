# Common Script - 脚本执行工具指南

本文档描述了 `common-script` 模块的功能和使用方法。该模块提供了在 Java 中执行脚本的能力,支持 JavaScript (GraalVM) 和 Groovy 两种脚本语言。

## 模块概述

`common-script` 基于 GraalVM JavaScript 和 Groovy 提供统一的脚本执行接口,支持:
- 动态脚本执行
- 脚本预编译(提升重复执行性能)
- 上下文变量绑定
- 可扩展的上下文贡献接口
- Java 对象与脚本对象互操作

**依赖版本:**
- GraalVM JavaScript: 21.3.6
- Groovy: 由 BOM 管理

## 核心类和接口

### Scripts - 脚本执行入口

路径: `com.ouroboros.script.Scripts`

这是使用脚本引擎的主要入口点,提供静态方法执行脚本。

**主要方法:**

```java
// 执行脚本 - 直接执行,返回 Try<Object>
Try<Object> result = Scripts.execute("js", scriptCode, bindingsMap);
Try<Object> result = Scripts.execute("groovy", scriptCode, bindingsMap);

// 编译脚本 - 返回可重用的函数,适合重复执行
Function<Map<String, Object>, Try<Object>> compiled = Scripts.compile("js", scriptCode);
Try<Object> result = compiled.apply(bindingsMap);
```

**支持的语言:**
- `"js"` 或 `"javascript"` - JavaScript (GraalVM)
- `"groovy"` - Groovy

### ScriptRunner - 脚本执行器接口

路径: `com.ouroboros.script.ScriptRunner`

定义脚本执行器的基本接口,可通过 SPI 扩展自定义脚本语言。

```java
public interface ScriptRunner {
  String getLanguage();  // 返回语言标识符
  Try<Object> execute(String script, Map<String, Object> bindings);
}
```

### ScriptCompiler - 脚本编译器接口

路径: `com.ouroboros.script.ScriptCompiler`

定义脚本编译器接口,用于预编译脚本提升性能。

```java
public interface ScriptCompiler {
  String getLanguage();  // 返回语言标识符
  Function<Map<String, Object>, Try<Object>> compile(String script);
}
```

### ScriptContextContributor - 上下文贡献接口

路径: `com.ouroboros.script.ScriptContextContributor`

通过 SPI 机制向脚本上下文注入全局对象和工具类。

```java
public interface ScriptContextContributor {
  Map<String, Object> getBindings();  // 返回要注入的全局对象
}
```

**内置贡献者:**
- `LoggerContextContributor` - 提供 `logger` 和 `console` 对象
- `UtilsContextContributor` - 提供 `Dates`, `Converter`, `Maps` 工具类

## 使用示例

### 基本脚本执行

```java
// JavaScript 示例
Map<String, Object> bindings = new HashMap<>();
bindings.put("name", "World");
bindings.put("count", 42);

Try<Object> result = Scripts.execute("js", "return 'Hello, ' + name + '! Count: ' + count", bindings);
if (result.isSuccess()) {
    System.out.println(result.get()); // Hello, World! Count: 42
}

// Groovy 示例
Map<String, Object> bindings = new HashMap<>();
bindings.put("x", 10);
bindings.put("y", 20);

Try<Object> result = Scripts.execute("groovy", "x + y", bindings);
if (result.isSuccess()) {
    System.out.println(result.get()); // 30
}
```

### 预编译脚本(性能优化)

对于需要重复执行的脚本,应预先编译以提升性能:

```java
// 编译一次
Function<Map<String, Object>, Try<Object>> compiled =
    Scripts.compile("js", "return $1.price * $1.quantity");

// 重复使用
for (Order order : orders) {
    Map<String, Object> bindings = Map.of("$1", order);
    Try<Object> total = compiled.apply(bindings);
    // 处理结果...
}
```

### 使用内置工具类

脚本中可以直接使用注入的工具类:

```java
// JavaScript - 使用 Dates 工具类
Try<Object> result = Scripts.execute("js",
    "return Dates.toString(Dates.now(), 'yyyy-MM-dd')",
    null);

// JavaScript - 使用 Maps 工具类
Map<String, Object> bindings = new HashMap<>();
Try<Object> result = Scripts.execute("js",
    "return Maps.pick({a:1, b:2, c:3}, 'a', 'b')",
    bindings);

// Groovy - 使用 Dates 工具类
Try<Object> result = Scripts.execute("groovy",
    "Dates.now()",
    null);
```

### 使用日志输出

脚本中可以使用 `logger` 或 `console` 输出日志:

```java
String script = """
    logger.info('Processing item: {}', item.name);
    console.log('Debug info: ' + item.id);
    return item.price * 1.1;
    """;

Map<String, Object> bindings = Map.of("item", item);
Try<Object> result = Scripts.execute("js", script, bindings);
```

### 访问 Java 对象和集合

JavaScript 可以直接访问 Java 对象、List 和 Map:

```java
Map<String, Object> bindings = new HashMap<>();
bindings.put("data", Map.of("user", Map.of("name", "Alice", "age", 30)));

// JavaScript - 访问嵌套 Map
Try<Object> result = Scripts.execute("js",
    "return data.user.name",
    bindings);

// JavaScript - 处理 List
bindings.put("numbers", Arrays.asList(1, 2, 3, 4, 5));
Try<Object> result = Scripts.execute("js",
    "return numbers.filter(n => n > 2).map(n => n * 2)",
    bindings);
```

### 调用 Java 函数

可以将 Java 函数传递给脚本:

```java
Map<String, Object> bindings = new HashMap<>();
bindings.put("transform", (Function<String, String>) str -> str.toUpperCase());

// JavaScript
Try<Object> result = Scripts.execute("js",
    "return transform('hello')",
    bindings);

// Groovy
Try<Object> result = Scripts.execute("groovy",
    "transform.apply('hello')",
    bindings);
```

### 类型转换

JavaScript 脚本返回的值会自动转换为 Java 类型:
- JavaScript 对象 → `Map<String, Object>`
- JavaScript 数组 → `List<Object>`
- JavaScript Date → `LocalDateTime`
- 原始类型保持不变

```java
// 返回 JavaScript 对象,自动转为 Java Map
Try<Object> result = Scripts.execute("js",
    "return {name: 'Alice', age: 30}",
    null);

if (result.isSuccess()) {
    Map<String, Object> map = (Map<String, Object>) result.get();
    System.out.println(map.get("name")); // Alice
}
```

## 扩展机制

### 扩展脚本语言

通过 SPI 机制添加新的脚本语言:

1. 实现 `ScriptRunner` 和/或 `ScriptCompiler` 接口
2. 在 `META-INF/services/` 下注册服务

**示例:**

```java
public class PythonEngine implements ScriptRunner, ScriptCompiler {
    @Override
    public String getLanguage() {
        return "python";
    }

    @Override
    public Try<Object> execute(String script, Map<String, Object> bindings) {
        // 实现 Python 脚本执行
    }

    @Override
    public Function<Map<String, Object>, Try<Object>> compile(String script) {
        // 实现 Python 脚本编译
    }
}
```

注册服务(创建文件 `META-INF/services/com.ouroboros.script.ScriptRunner`):
```
com.example.PythonEngine
```

### 扩展上下文贡献者

通过 SPI 向脚本上下文注入自定义对象:

1. 实现 `ScriptContextContributor` 接口
2. 在 `META-INF/services/com.ouroboros.script.ScriptContextContributor` 注册

**示例:**

```java
public class CustomContextContributor implements ScriptContextContributor {
    @Override
    public Map<String, Object> getBindings() {
        Map<String, Object> bindings = new HashMap<>();
        bindings.put("myUtil", MyUtilityClass.class);
        bindings.put("myService", myServiceInstance);
        return bindings;
    }
}
```

### 配置 JavaScript Host Access

对于 JavaScript 引擎,可以通过 `JsHostAccessConfigurer` 配置 Java 对象访问权限:

```java
public class CustomHostAccessConfigurer implements JsHostAccessConfigurer {
    @Override
    public void config(HostAccess.Builder builder) {
        builder.targetTypeMapping(
            Value.class,
            MyCustomType.class,
            Value::isHostObject,
            v -> convertToMyCustomType(v),
            HostAccess.TargetMappingPrecedence.HIGH
        );
    }
}
```

注册服务(创建文件 `META-INF/services/com.ouroboros.script.JsHostAccessConfigurer`):
```
com.example.CustomHostAccessConfigurer
```

## 注意事项

1. **线程安全**
   - `Scripts.execute()` 每次创建新的执行上下文,线程安全
   - 预编译的函数也是线程安全的
   - Groovy 编译模式当前未实现对象池,重复使用与直接执行性能相同

2. **性能建议**
   - 重复执行的脚本应使用 `Scripts.compile()` 预编译
   - JavaScript 编译后性能提升显著,Groovy 当前编译优化待实现

3. **错误处理**
   - 所有执行方法返回 `Try<Object>`,应检查 `isSuccess()` 或 `isFailure()`
   - JavaScript PolyglotException 会自动提取 Host Exception

4. **上下文变量**
   - 绑定的变量在脚本中不可修改(只读)
   - JavaScript 中访问 Class 对象会自动转换为 `Java.type()`

5. **返回值类型**
   - JavaScript 返回值自动转换为 Java 类型
   - Groovy 返回值保持原始类型
   - 注意类型转换和 null 值处理

6. **安全性**
   - JavaScript 引擎允许完全的 Java 类访问 (`allowHostClassLookup`)
   - 生产环境应评估脚本来源的可信度
   - 考虑实施脚本沙箱或权限控制

## 使用建议

1. **选择合适的语言** - JavaScript 适合表达式和数据转换,Groovy 适合复杂业务逻辑
2. **预编译优化** - 重复执行的脚本务必使用 `compile()` 方法
3. **工具类复用** - 充分利用内置的 `Dates`, `Maps`, `Converter` 等工具类
4. **日志输出** - 使用 `logger` 或 `console` 输出调试信息
5. **错误处理** - 始终检查 `Try` 的执行结果,妥善处理失败情况
6. **扩展机制** - 通过 SPI 扩展而非修改核心代码
