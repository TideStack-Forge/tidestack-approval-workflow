# Common Model - 模型基类指南

本文档描述了 `common-model` 模块中的模型基类和接口,用于创建具有 Builder 模式、克隆能力和扩展属性的不可变模型类。

## 模块概述

`common-model` 提供了统一的模型基类,支持:
- Builder 模式构建对象
- 对象克隆和映射
- 动态扩展属性(extraProps)
- 低代码/专业代码模型标记

## 核心类/接口

### Model 接口

路径: `com.ouroboros.model.Model`

所有模型的顶层接口,定义克隆能力和 Builder 接口。

```java
public interface Model<M extends Model<M>> extends Cloneable {
  M clone();

  interface Builder<M extends Model<M>> {
    M build();
  }
}
```

### AbstractModel 抽象类

路径: `com.ouroboros.model.AbstractModel`

提供基础模型实现,支持克隆和映射操作。

**核心方法:**
- `clone()` - 克隆模型对象
- `map(Consumer<B>... mappers)` - 基于当前对象创建新对象并应用修改
- `createBuilder()` - 创建 Builder 实例(子类实现)

**使用示例:**

```java
public class User extends AbstractModel<User, User.Builder> {
  private String name;
  private String email;

  private User() {}

  public static Builder builder() {
    return new Builder();
  }

  @Override
  protected Builder createBuilder() {
    return builder();
  }

  public String getName() {
    return name;
  }

  public String getEmail() {
    return email;
  }

  public static class Builder extends AbstractModel.Builder<User> {
    private Builder() {
      this.model = new User();
    }

    public Builder name(String name) {
      model.name = name;
      return this;
    }

    public Builder email(String email) {
      model.email = email;
      return this;
    }
  }
}

// 使用 Builder 创建对象
User user = User.builder()
  .name("张三")
  .email("zhangsan@example.com")
  .build();

// 使用 map 创建修改后的副本
User updatedUser = user.map(
  b -> b.email("newemail@example.com")
);
```

### AbstractExtraPropsModel 抽象类

路径: `com.ouroboros.model.AbstractExtraPropsModel`

继承自 `AbstractModel`,添加动态扩展属性支持。适用于需要存储额外 JSON 字段的场景。

**核心方法:**
- `getExtraProps()` - 获取所有扩展属性
- `getExtraProp(String key)` - 获取指定扩展属性
- `getExtraProp(Class<T> clazz, String key)` - 获取类型化扩展属性

**Builder 方法:**
- `extraProps(Map<String, Object> props)` - 批量设置扩展属性
- `extraProp(String key, Object value)` - 设置单个扩展属性

**使用示例:**

```java
public class FlowNode extends AbstractExtraPropsModel<FlowNode, FlowNode.Builder> {
  private String type;
  private String name;

  private FlowNode() {}

  public static Builder builder() {
    return new Builder();
  }

  @Override
  protected Builder createBuilder() {
    return builder();
  }

  public String getType() {
    return type;
  }

  public String getName() {
    return name;
  }

  public static class Builder extends AbstractExtraPropsModel.Builder<FlowNode, Builder> {
    private Builder() {
      model = new FlowNode();
    }

    public Builder type(String type) {
      model.type = type;
      return this;
    }

    public Builder name(String name) {
      model.name = name;
      return this;
    }
  }
}

// 创建带扩展属性的对象
FlowNode node = FlowNode.builder()
  .type("HttpRequest")
  .name("API调用")
  .extraProp("timeout", 5000)
  .extraProp("retryCount", 3)
  .build();

// 读取扩展属性
Optional<Object> timeout = node.getExtraProp("timeout");
Optional<Integer> typedTimeout = node.getExtraProp(Integer.class, "timeout");

// 获取所有扩展属性(只读)
Map<String, Object> extraProps = node.getExtraProps();
```

### WithExtraProps 接口

路径: `com.ouroboros.model.WithExtraProps`

定义扩展属性的访问接口。与 Jackson 的 `@JsonAnyGetter` 集成,支持 JSON 序列化/反序列化时自动处理额外字段。

```java
public interface WithExtraProps {
  @JsonAnyGetter
  Map<String, Object> getExtraProps();

  Optional<Object> getExtraProp(String key);

  <T> Optional<T> getExtraProp(Class<T> clazz, String key);
}
```

### 模型标记注解

路径: `com.ouroboros.model.annotation`

用于标记模型的来源类型:

**@LowCodeModel** - 标记低代码生成的模型
```java
@LowCodeModel
public class DynamicEntity extends AbstractExtraPropsModel<...> {
  // 低代码平台动态生成的实体
}
```

**@ProCodeModel** - 标记专业代码编写的模型
```java
@ProCodeModel
public class CoreEntity extends AbstractModel<...> {
  // 开发者手工编写的核心实体
}
```

## 设计模式

### Builder 模式

所有模型使用 Builder 模式构建,遵循以下约定:
1. 模型类构造函数为 `private`
2. 提供静态 `builder()` 方法
3. Builder 方法返回 `this` 支持链式调用
4. 实现 `createBuilder()` 方法用于 `map()` 操作

### 不可变性

模型对象创建后不可修改。修改需通过以下方式:
- 使用 `clone()` 创建副本后修改
- 使用 `map()` 方法创建修改后的新对象

```java
// 不推荐: 直接修改(模型类没有 setter)
// user.setName("李四"); // 编译错误

// 推荐: 使用 map 创建新对象
User newUser = user.map(
  b -> b.name("李四"),
  b -> b.email("lisi@example.com")
);
```

## JSON 序列化支持

模型类与 Jackson 无缝集成:

```java
// 序列化
String json = objectMapper.writeValueAsString(node);
// {"type":"HttpRequest","name":"API调用","timeout":5000,"retryCount":3}

// 反序列化(extraProps 自动处理未定义字段)
FlowNode deserialized = objectMapper.readValue(json, FlowNode.class);

// 额外字段自动存入 extraProps
deserialized.getExtraProp("timeout"); // Optional[5000]
```

## 使用建议

1. **选择基类:**
   - 固定字段的模型 → 继承 `AbstractModel`
   - 需要扩展属性的模型 → 继承 `AbstractExtraPropsModel`

2. **Builder 设计:**
   - 为集合类型字段提供批量和单个添加方法
   - 为布尔类型提供无参便捷方法(如 `async()` 等价于 `async(true)`)

3. **泛型声明:**
   - `AbstractModel<M, B>` - M 为模型类型,B 为 Builder 类型
   - `AbstractExtraPropsModel<M, B>` - 泛型约束更严格,确保类型安全

4. **扩展属性使用:**
   - 用于存储动态配置、元数据等非核心字段
   - 不要滥用 extraProps,核心字段应定义为类属性
   - 使用类型化访问 `getExtraProp(Class<T>, String)` 提高安全性

5. **注解标记:**
   - 根据模型来源添加 `@LowCodeModel` 或 `@ProCodeModel` 注解
   - 有助于运行时识别和处理不同来源的模型

## 完整示例

参考以下核心模块中的实现:
- `core/ouroboros-logicflow/ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/model/FlowConnectionMeta.java` - AbstractModel 示例
- `core/ouroboros-logicflow/ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/model/FlowNodeMeta.java` - AbstractExtraPropsModel 示例
