# Common-All - 公共组件聚合模块

本模块聚合了 Ouroboros 平台的所有核心公共组件,提供统一的依赖入口。

## 模块概述

`common-all` 是一个聚合模块,集成了平台开发所需的所有基础公共组件。通过引入这一个依赖,即可获得异常处理、Web API、表达式引擎、脚本执行、数据模型等核心能力。

## 聚合的子模块

| 模块 | 功能说明 |
|------|---------|
| **common-exception** | 异常体系定义,包含 OuroborosException、BusinessException、ValidationException、PlatformException、InternalException |
| **common-web** | Web API 响应封装,包含 WebApiResult、PagedResult、SelectOption 等 |
| **common-expression** | 表达式引擎(SpEL),支持 `${ expression }` 格式的动态表达式求值 |
| **common-script** | 脚本执行引擎,支持 JavaScript(GraalVM)和 Groovy 脚本 |
| **common-app** | 应用生命周期事件,如 OuroborosContextRefreshEvent、OuroborosModelRefreshedEvent |
| **common-model** | 数据模型基类,包含 Model、AbstractModel、@LowCodeModel、@ProCodeModel 注解 |

## 使用说明

### Maven 依赖

```xml
<dependency>
    <groupId>com.ouroboros</groupId>
    <artifactId>common-all</artifactId>
</dependency>
```

### 核心功能示例

#### 1. 异常处理

```java
import com.ouroboros.exception.*;

// 业务异常
throw new BusinessException("业务处理失败");

// 验证异常(带字段错误)
throw new ValidationException(java.util.Map.of(
    "email", "邮箱格式不正确",
    "phone", "手机号码格式不正确"
));

// 平台异常(开发配置错误)
throw new PlatformException("数据源配置错误");

// 内部异常(系统底层错误)
throw new InternalException("数据库连接失败", cause);
```

#### 2. Web API 响应

```java
import com.ouroboros.web.*;

// 成功响应
return WebApiResult.ok(data);

// 失败响应
return WebApiResult.error("操作失败");

// 分页响应
PagedResult<User> pagedData = new PagedResult<>(userList, total, page, pageSize);
return new PagedWebApiResult<>(pagedData);

// 从 Try/Either 构建
Try<User> result = userService.getUser(id);
return WebApiResult.of(result);
```

#### 3. 表达式求值

```java
import com.ouroboros.expression.EL;

// 基本求值
Map<String, Object> context = Map.of("name", "张三", "age", 25);
Either<Throwable, Object> result = EL.eval("${name + '的年龄是' + age}", context);

// 类型安全求值
Either<Throwable, String> stringResult = EL.eval(String.class, "${name}", context);

// 包装表达式
String wrapped = EL.wrapExpression("name"); // 返回 "${ name }"
```

#### 4. 脚本执行

```java
import com.ouroboros.script.Scripts;

// 执行 JavaScript
Map<String, Object> bindings = Map.of("x", 10, "y", 20);
Try<Object> jsResult = Scripts.execute("js", "x + y", bindings);

// 执行 Groovy
Try<Object> groovyResult = Scripts.execute("groovy", "x * y", bindings);

// 编译脚本(可重复使用)
Function<Map<String, Object>, Try<Object>> compiled =
    Scripts.compile("js", "x + y");
Try<Object> result1 = compiled.apply(Map.of("x", 1, "y", 2));
Try<Object> result2 = compiled.apply(Map.of("x", 3, "y", 4));
```

#### 5. 数据模型

```java
import com.ouroboros.model.*;
import com.ouroboros.model.annotation.*;

// 低代码模型标注
@LowCodeModel
public class UserModel extends AbstractModel<UserModel> {
    private String name;
    private Integer age;

    @Override
    public UserModel clone() {
        return new UserModel(this.name, this.age);
    }
}

// 纯代码模型标注
@ProCodeModel
public class SystemConfig extends AbstractModel<SystemConfig> {
    // ...
}
```

## 其他可用模块

以下模块未包含在 `common-all` 中,需单独引入:

- **common-exception-mapper**: 数据模型异常到 Ouroboros 异常的映射器
- **common-service**: 服务层基类
- **common-dev**: 开发工具支持
- **common-cache**: 缓存支持
- **common-template**: 模板引擎

## 注意事项

1. **异常处理规范**
   - 业务逻辑错误使用 `BusinessException`
   - 用户输入验证错误使用 `ValidationException`
   - 平台配置错误使用 `PlatformException`
   - 系统内部错误使用 `InternalException`

2. **WebApiResult 使用**
   - Controller 方法统一返回 `WebApiResult<T>` 类型
   - 优先使用 `WebApiResult.of()` 方法从 `Try` 或 `Either` 构建响应
   - 避免手动构造错误响应,使用异常映射器自动转换

3. **表达式引擎**
   - 表达式格式为 `${ expression }`,可使用 `EL.wrapExpression()` 自动包装
   - 上下文对象通过 Map 传入,支持嵌套对象访问(如 `${user.name}`)
   - 内置工具类通过 `ExpressionContextContributor` SPI 扩展

4. **脚本引擎**
   - JavaScript 基于 GraalVM,性能优异但启动较慢
   - Groovy 启动快但运行性能略低
   - 脚本上下文通过 `ScriptContextContributor` SPI 扩展

5. **模型注解**
   - `@LowCodeModel` 标注动态生成的低代码模型
   - `@ProCodeModel` 标注开发者编写的纯代码模型
   - 实现 `Model<M>` 接口时必须实现 `clone()` 方法

## 参考资源

- [ouroboros-util 工具类指南](../../util/ouroboros-util/GUIDE.md)
- [异常映射器说明](../common-exception-mapper/README.md)
