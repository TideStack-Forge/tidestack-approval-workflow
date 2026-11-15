# Common Web - Web API 工具指南

本文档描述了 `common-web` 模块中的 Web API 响应工具类，用于统一 REST API 的返回格式。

## 概述

`common-web` 模块提供了一套标准化的 Web API 响应封装类和工具方法，支持：
- 统一的成功/失败响应格式
- 分页数据和列表数据的标准化返回
- 异常到响应的自动映射
- 下拉选项和树形选项的数据结构
- 请求参数的解析工具

## 核心类

### WebApiResult - 通用 API 响应

路径: `com.ouroboros.web.WebApiResult`

标准的 API 响应封装，包含状态码、消息和数据。

#### 响应结构
```json
{
  "code": 0,          // 0 表示成功，-1 或其他表示失败
  "msg": null,        // 错误消息，成功构造器默认为 null
  "data": {},         // 响应数据
  "errors": null,     // 验证错误详情(可选)
  "traceId": "..."    // setExtra 的键通过 JsonAnyGetter 展开在顶层
}
```

#### 常用方法

**成功响应**
```java
// 无数据成功
WebApiResult<Void> result = WebApiResult.ok();
WebApiResult<Void> result = WebApiResult.success();

// 有数据成功
WebApiResult<User> result = WebApiResult.ok(user);
WebApiResult<User> result = WebApiResult.success(user);
```

**错误响应**
```java
// 默认错误码 -1
WebApiResult<Void> result = WebApiResult.error("操作失败");
WebApiResult<Void> result = WebApiResult.fail("操作失败");

// 自定义错误码
WebApiResult<Void> result = WebApiResult.error(1001, "权限不足");
WebApiResult<Void> result = WebApiResult.fail(1001, "权限不足");
```

**从 Try/Either 构建**
```java
// 从 Try 构建，自动处理异常
Try<User> userTry = Try.of(() -> userService.getUser(id));
WebApiResult<User> result = WebApiResult.of(userTry);

// 从 Either 构建
Either<Throwable, User> userEither = userService.findUser(id);
WebApiResult<User> result = WebApiResult.of(userEither);

// 自定义错误码/消息
WebApiResult<User> result = WebApiResult.of(1001, "用户不存在", userTry);
WebApiResult<User> result = WebApiResult.of("用户不存在", userEither);
```

**从 Optional 构建**
```java
Optional<User> userOpt = userRepository.findById(id);

// 不存在时返回默认错误
WebApiResult<User> result = WebApiResult.of(userOpt);

// 自定义错误消息
WebApiResult<User> result = WebApiResult.of("用户不存在", userOpt);
WebApiResult<User> result = WebApiResult.of(404, "用户不存在", userOpt);
```

**数据转换**
```java
WebApiResult<User> userResult = WebApiResult.ok(user);

// 转换数据类型
WebApiResult<UserDTO> dtoResult = userResult.map(u -> toDTO(u));
```

### PagedWebApiResult - 分页响应

路径: `com.ouroboros.web.PagedWebApiResult`

用于返回分页数据的标准响应。

#### 响应结构
```json
{
  "code": 0,
  "msg": "",
  "data": {
    "items": [...],   // 当前页数据
    "total": 100      // 总记录数
  }
}
```

#### 使用示例

```java
// 创建分页响应
PagedWebApiResult<User> result = PagedWebApiResult.ok(100L, userList);
PagedWebApiResult<User> result = PagedWebApiResult.ok(100, userList);

// 从 PagedResult 创建
PagedResult<User> pagedData = PagedResult.of(100L, userList);
PagedWebApiResult<User> result = PagedWebApiResult.ok(pagedData);

// 从 Try/Either 构建
Try<PagedResult<User>> pagedTry = Try.of(() -> userService.getUsers(skip, limit));
PagedWebApiResult<User> result = PagedWebApiResult.of(pagedTry);

// 数据转换
PagedWebApiResult<User> userResult = PagedWebApiResult.ok(100, users);
PagedWebApiResult<UserDTO> dtoResult = userResult.map(u -> toDTO(u));
```

### ItemsWebApiResult - 列表响应

路径: `com.ouroboros.web.ItemsWebApiResult`

用于返回列表数据(无分页)的标准响应。

#### 响应结构
```json
{
  "code": 0,
  "msg": "",
  "data": {
    "items": [...]    // 数据列表
  }
}
```

#### 使用示例

```java
// 创建列表响应
ItemsWebApiResult<User> result = ItemsWebApiResult.ok(userList);
ItemsWebApiResult<User> result = ItemsWebApiResult.ok(singleUser);

// 从 Try/Either 构建
Try<List<User>> usersTry = Try.of(() -> userService.getAllUsers());
ItemsWebApiResult<User> result = ItemsWebApiResult.of(usersTry);

// 数据转换
ItemsWebApiResult<User> userResult = ItemsWebApiResult.ok(users);
ItemsWebApiResult<UserDTO> dtoResult = userResult.map(u -> toDTO(u));
```

### PagedResult - 分页数据容器

路径: `com.ouroboros.web.PagedResult`

分页数据的容器类，包含数据列表和总数。

```java
// 创建分页数据
PagedResult<User> paged = PagedResult.of(100L, userList);

// 数据转换
PagedResult<UserDTO> dtosPaged = paged.map(u -> toDTO(u));

// 转为 WebApiResult
WebApiResult<PagedResult<User>> result = paged.toWebApiResult();

// 获取数据
List<User> items = paged.getItems();
Long total = paged.getTotal();
```

### ItemsResult - 列表数据容器

路径: `com.ouroboros.web.ItemsResult`

列表数据的容器类。

```java
// 创建列表数据
ItemsResult<User> items = ItemsResult.of(userList);
ItemsResult<User> items = ItemsResult.of(singleUser);

// 数据转换
ItemsResult<UserDTO> dtosItems = items.map(u -> toDTO(u));

// 转为 WebApiResult
WebApiResult<ItemsResult<User>> result = items.toWebApiResult();

// 获取数据
Collection<User> collection = items.getItems();
```

## 选项数据结构

### SelectOption - 下拉选项

路径: `com.ouroboros.web.SelectOption`

用于下拉框等组件的选项数据结构。

```java
// 创建选项
SelectOption option = new SelectOption("1", "选项一");

// 链式调用
SelectOption option = new SelectOption()
    .setValue("1")
    .setLabel("选项一")
    .addExtraProp("disabled", false)
    .addExtraProp("icon", "user");

// 响应结构
{
  "value": "1",
  "label": "选项一",
  "disabled": false,
  "icon": "user"
}
```

### TreeSelectOption - 树形选项

路径: `com.ouroboros.web.TreeSelectOption`

用于树形选择器的选项数据结构，支持嵌套。

```java
// 创建树形选项
TreeSelectOption root = new TreeSelectOption("1", "根节点");
TreeSelectOption child1 = new TreeSelectOption("1-1", "子节点1");
TreeSelectOption child2 = new TreeSelectOption("1-2", "子节点2");

root.addChild(child1).addChild(child2);

// 设置额外属性
root.addExtraProp("icon", "folder");
child1.addExtraProp("icon", "file");

// 响应结构
{
  "value": "1",
  "label": "根节点",
  "icon": "folder",
  "children": [
    {
      "value": "1-1",
      "label": "子节点1",
      "icon": "file"
    },
    {
      "value": "1-2",
      "label": "子节点2"
    }
  ]
}
```

## 请求参数工具

### RequestUtil - 请求参数解析

路径: `com.ouroboros.web.RequestUtil`

用于解析标准化的查询参数。

#### 分页参数解析

支持多种分页参数格式：
- `page` + `perPage/pageSize`: 页码方式
- `skip/offset` + `limit`: 偏移量方式

```java
Map<String, Object> params = new HashMap<>(); // 由控制器 @RequestParam Map 接收标量参数；Servlet getParameterMap() 的值是 String[]，需先展开

// 解析分页参数 (skip, limit)
Tuple2<Integer, Integer> pagination = RequestUtil.getSkipLimit(params);
Integer skip = pagination._1;
Integer limit = pagination._2;

// 使用示例
List<User> users = userService.getUsers(skip, limit);
Long total = userService.countUsers();
return PagedWebApiResult.ok(total, users);
```

支持的参数组合：
```java
// 页码方式: page=2&perPage=20  => skip=20, limit=20
// 页码方式: page=3&pageSize=10 => skip=20, limit=10
// 偏移量方式: skip=10&limit=20 => skip=10, limit=20
// 偏移量方式: offset=10&limit=20 => skip=10, limit=20
```

#### 排序参数解析

支持多种排序参数格式：
- 字符串: `sort=name` => `{"name": "asc"}`
- 字段+方向: `sort=name desc` => `{"name": "desc"}`
- JSON 对象: `sort={"name":"desc","age":"asc"}` => Map

```java
// 解析排序参数
Map<String, String> order = RequestUtil.getOrder(params);

// order = {"name": "desc", "age": "asc"}
// 应用到查询中
query.orderBy(order);
```

#### 过滤条件解析

自动提取除分页/排序外的其他参数作为查询条件。

```java
// 解析查询条件
Map<String, Object> where = RequestUtil.getWhere(params);

// 请求: ?name=张三&age=25&page=1&perPage=10
// where = {"name": "张三", "age": "25"}（保留输入类型，不自动转数字）
// (自动排除 page, perPage, pageSize, skip, limit, offset, sort, order)

// 支持层级结构
// 请求: ?user.name=张三&user.age=25
// where = {"user": {"name": "张三", "age": "25"}}
```

#### 完整使用示例

```java
@GetMapping("/users")
public PagedWebApiResult<User> listUsers(@RequestParam Map<String, Object> params) {
    // 解析参数
    Tuple2<Integer, Integer> pagination = RequestUtil.getSkipLimit(params);
    Map<String, String> order = RequestUtil.getOrder(params);
    Map<String, Object> where = RequestUtil.getWhere(params);

    // 查询数据
    List<User> users = userService.findUsers(where, order, pagination._1, pagination._2);
    Long total = userService.countUsers(where);

    // 返回结果
    return PagedWebApiResult.ok(total, users);
}
```

## 异常处理

### 自定义异常映射

通过 SPI 选择一个异常到响应的 mapper：按 `@Priority` 数值从高到低取首，缺省使用默认实现。这里没有逐个尝试直到非 null 的责任链。

#### 实现自定义映射器

```java
import jakarta.annotation.Priority;
import com.ouroboros.web.WebApiResultExceptionMapper;
import io.vavr.Tuple;
import io.vavr.Tuple3;

@Priority(100) // 优先级，数字越大优先级越高
public class CustomExceptionMapper implements WebApiResultExceptionMapper {
    @Override
    public Tuple3<Integer, String, Object> map(Throwable throwable) {
        // 返回: (错误码, 错误消息, 错误详情)
        if (throwable instanceof CustomException e) {
            return Tuple.of(e.getCode(), e.getMessage(), e.getDetails());
        }
        // 该实现会替换默认 mapper，其他异常必须显式委托
        return new com.ouroboros.web.DefaultWebApiResultExceptionMapper().map(throwable);
    }
}
```

#### 注册映射器

在 `META-INF/services/com.ouroboros.web.WebApiResultExceptionMapper` 文件中注册：
```
com.example.CustomExceptionMapper
```

#### 默认异常映射

默认映射器 (`DefaultWebApiResultExceptionMapper`) 处理：
- `ValidationException`: 返回验证错误详情
- `RecordValidationException` / `DataValidationException`：返回记录字段错误或数据校验消息
- `BusinessException`：返回业务错误码和消息
- 请求方法、参数类型、JSON 请求体错误及 Spring 4xx `ErrorResponse`：返回对应请求错误
- 其他异常（含非 BusinessException 的平台/内部异常）：默认返回 `50000` 与“服务暂时不可用，请稍后重试”

实际响应还受应用注册的高优先级 mapper 影响。MVC 返回 `AbstractWebApiResult` 时，`WebApiResultHttpStatusAdvice` 在未显式指定状态的情况下依据业务码映射 HTTP 状态；HTTP 处理与响应体错误码是两个步骤。

## Controller 使用示例

### 基本 CRUD 操作

```java
@RestController
@RequestMapping("/api/users")
public class UserController {
    @Autowired
    private UserService userService;

    // 创建
    @PostMapping
    public WebApiResult<User> create(@RequestBody User user) {
        return WebApiResult.of(
            Try.of(() -> userService.create(user))
        );
    }

    // 查询单个
    @GetMapping("/{id}")
    public WebApiResult<User> get(@PathVariable String id) {
        return WebApiResult.of(
            "用户不存在",
            userService.findById(id)
        );
    }

    // 更新
    @PutMapping("/{id}")
    public WebApiResult<User> update(@PathVariable String id, @RequestBody User user) {
        return WebApiResult.of(
            Try.of(() -> userService.update(id, user))
        );
    }

    // 删除
    @DeleteMapping("/{id}")
    public WebApiResult<Void> delete(@PathVariable String id) {
        return WebApiResult.of(
            Try.of(() -> {
                userService.delete(id);
                return null;
            })
        );
    }

    // 分页查询
    @GetMapping
    public PagedWebApiResult<User> list(@RequestParam Map<String, Object> params) {
        var pagination = RequestUtil.getSkipLimit(params);
        var order = RequestUtil.getOrder(params);
        var where = RequestUtil.getWhere(params);

        var users = userService.findUsers(where, order, pagination._1, pagination._2);
        var total = userService.countUsers(where);

        return PagedWebApiResult.ok(total, users);
    }

    // 获取全部(无分页)
    @GetMapping("/all")
    public ItemsWebApiResult<User> listAll() {
        return ItemsWebApiResult.of(
            Try.of(() -> userService.findAll())
        );
    }

    // 下拉选项
    @GetMapping("/options")
    public ItemsWebApiResult<SelectOption> options() {
        var users = userService.findAll();
        var options = users.stream()
            .map(u -> new SelectOption(u.getId(), u.getName()))
            .collect(Collectors.toList());
        return ItemsWebApiResult.ok(options);
    }
}
```

### 使用 Either 进行业务逻辑处理

```java
@PostMapping("/register")
public WebApiResult<User> register(@RequestBody RegisterDTO dto) {
    Either<Throwable, User> result = userService.validateEmail(dto.getEmail())
        .flatMap(email -> userService.validatePassword(dto.getPassword()))
        .flatMap(password -> userService.createUser(dto));

    return WebApiResult.of("注册失败", result);
}
```

## 使用建议

1. **统一使用 WebApiResult** - 普通 JSON Controller 使用统一响应；下载流等按端点合同返回
2. **优先使用 of 方法** - 使用 `WebApiResult.of(Try/Either)` 自动处理异常
3. **分页用 PagedWebApiResult** - 分页接口统一使用 PagedWebApiResult
4. **列表用 ItemsWebApiResult** - 非分页列表统一使用 ItemsWebApiResult
5. **使用 RequestUtil** - 标准化请求参数解析，支持多种参数格式
6. **自定义异常映射** - 通过 SPI 扩展异常处理逻辑
7. **数据转换用 map** - 使用 map 方法转换数据；当前实现会重建响应，不保留原 extra/errors，不要用它承诺附加字段完整传递
8. **选项数据规范化** - 使用 SelectOption/TreeSelectOption 统一选项数据格式

## 重要提醒

- **响应码约定**: 0 表示成功，-1 表示默认错误，其他为自定义错误码
- **空值处理**: 使用 `WebApiResult.of(Optional)` 处理可能为空的数据
- **异常传播**: `of(Try/Either)` 会自动映射异常，无需手动 try-catch
- **Extra 字段**: 需要额外字段时使用 `setExtra(key, value)` 而不是修改 data 结构
- **SPI 优先级**: 自定义 ExceptionMapper 使用 `@Priority` 注解控制优先级
