# ouroboros-ui-model-core

UI模型核心模块，定义了Ouroboros平台中UI的元数据抽象。

## 设计理念

UIModel **不负责UI的具体渲染**，而是定义**UI的元数据**：

- 这个UI是什么（标题、描述）
- 在哪里（路径）
- 如何渲染（渲染类型）
- 扩展信息（图标、自定义属性）

实际的渲染工作由渲染引擎根据 `type` 字段决定：
- `amis` - 使用 Amis UI Schema 渲染
- `micro` - 使用微前端渲染
- `qb` - 模型接口保留的类型名，不能据此推定主前端已具备渲染器
- `internal` - 内置页面类型，具体页面由前端注册

## 核心接口

### UIModel

UI模型的核心契约，定义UI的基本属性。

```java
public interface UIModel {
  // 核心属性
  String getTitle();        // UI标题（用于菜单、面包屑、标签页等展示场景）
  String getPath();         // UI的唯一路径（用于路由和分层覆盖机制）
  String getType();         // UI的渲染类型（如 "amis", "micro", "qb", "internal"）

  // 可选属性（通过 extraProps 实现）
  String getIcon();                  // UI图标（如 "fa fa-database"）
  Optional<String> getDescription();  // UI的详细描述

  // 扩展机制
  Map<String, Object> getExtraProps();
  Optional<Object> getExtraProp(String key);
  <T> Optional<T> getExtraProp(Class<T> clazz, String key);
}
```

### UIModelProvider

UI模型的提供者接口，负责加载和管理UI模型。

```java
public interface UIModelProvider {
  Optional<UIModel> getUIModel(String path);
  Map<String, UIModel> getUIModelMap();
}
```

`title`、`path`、`type` 是配置契约要求；`SimpleUIModel` 的 setter 不执行必填或类型枚举校验。`getPath()` 只补 `/` 前缀；provider 可由文件名推导缺失 path。扩展键大小写不敏感，空字符串、空集合等通过 `getExtraProp` 读取时被视为空。

## 核心属性详解

### 1. title - UI标题

**类型**: `String`
**必填**: 是
**用途**: UI的简短标题，用于：
- 菜单项显示
- 浏览器标签页标题
- 面包屑导航
- 页面标题栏

**示例**:
```json
{
  "title": "字段库管理"
}
```

### 2. path - UI路径

**类型**: `String`
**必填**: 是
**格式**: 以 `/` 开头的路径字符串，支持路径变量（如 `{appName}`）
**用途**:
- 前端路由地址
- UI的唯一标识符
- 分层覆盖机制的键

**特性**:
- 自动标准化：`SimpleUIModel` 会自动为路径添加 `/` 前缀
- 支持变量：路径中可包含 `{appName}` 等占位符
- 层级结构：通过 `/` 分隔符表达UI的层级关系

**示例**:
```json
{
  "path": "/dev/{appName}/field-types"
}
```

### 3. type - 渲染类型

**类型**: `String`
**必填**: 是
**可选值**:
- `amis` - 使用 Amis UI Schema 渲染（需配合 `schemaId`）
- `micro` - 微前端应用（需配合 `entry` 或 `microWebName`）
- `qb` - Query Builder 页面
- `internal` - 平台内置页面

**用途**: 决定前端使用哪种渲染引擎来展示这个UI

**示例**:
```json
{
  "type": "amis"
}
```

**与 extraProps 的配合**:
```json
{
  "type": "amis",
  "schemaId": "/dev/{appName}/field-types"
}
```

### 4. icon - UI图标（可选）

**类型**: `String`
**必填**: 否
**格式**: FontAwesome 图标类名或自定义图标标识
**用途**:
- 菜单图标
- 卡片图标
- 列表项图标

**获取方式**:
```java
String icon = uiModel.getIcon();
```

**JSON配置**:
```json
{
  "icon": "fa fa-database"
}
```

### 5. description - UI描述（可选）

**类型**: `Optional<String>`
**必填**: 否
**用途**:
- 菜单项的提示信息
- 开发工具中的说明文字
- 文档生成
- 搜索关键字

**获取方式**:
```java
String desc = uiModel.getDescription().orElse("");
```

**JSON配置**:
```json
{
  "description": "管理系统中的所有自定义字段类型，支持增删改查操作"
}
```

### 6. extraProps - 扩展属性

**类型**: `Map<String, Object>`
**用途**: 存储渲染类型特定的配置和自定义属性

**常用扩展属性**:

#### amis 类型专用
- `schemaId` - UI Schema 的 ID（默认使用 `path`）

```json
{
  "type": "amis",
  "schemaId": "/dev/{appName}/field-types/detail"
}
```

#### micro 类型专用
- `entry` - 微前端入口地址
- `microWebName` - 关联的微前端模型名称（系统会自动解析为 `entry`）

```json
{
  "type": "micro",
  "microWebName": "field-type-editor"
}
```

或

```json
{
  "type": "micro",
  "entry": "https://cdn.example.com/micro-apps/field-editor.js"
}
```

#### 自定义属性
可以添加任意自定义属性，通过 `getExtraProp()` 访问：

```json
{
  "customKey": "customValue",
  "config": {
    "nested": "object"
  }
}
```

```java
// 类型安全访问
Optional<String> customValue = uiModel.getExtraProp(String.class, "customKey");

// 获取嵌套对象
Optional<Map> config = uiModel.getExtraProp(Map.class, "config");
```

## 使用示例

### 1. 完整的 JSON 配置

**位置**: 源文件 `src/main/metadata/<name>.ui-model.json`，打包到 `META-INF/ouroboros/metadata/` typed 根。旧 `META-INF/ouroboros/ui-model/**/*.json` 仅兼容读取，同键 typed 定义覆盖旧定义。

```json
{
  "path": "/dev/{appName}/field-types",
  "type": "amis",
  "title": "字段类型管理",
  "icon": "fa fa-database",
  "description": "管理系统中的所有自定义字段类型，支持增删改查操作",
  "schemaId": "/dev/{appName}/field-types"
}
```

### 2. 微前端 UI 配置

```json
{
  "path": "/dev/{appName}/logic-flow-designer",
  "type": "micro",
  "title": "逻辑流设计器",
  "icon": "fa fa-sitemap",
  "description": "可视化编辑业务逻辑流程",
  "microWebName": "logic-flow-designer"
}
```

### 3. Java 代码使用

```java
// 与运行时相同，按 SPI 顺序合并；后者覆盖同路径定义
Map<String, UIModel> models = new TreeMap<>(String::compareToIgnoreCase);
SPI.getCachedSortedServiceStream(UIModelProvider.class)
  .forEach(provider -> models.putAll(provider.getUIModelMap()));

public void example() {
  // 获取指定路径的 UI 模型
  Optional<UIModel> uiModel = Optional.ofNullable(models.get("/dev/{appName}/field-types"));

  uiModel.ifPresent(model -> {
    // 访问核心属性
    String title = model.getTitle();        // "字段类型管理"
    String path = model.getPath();          // "/dev/{appName}/field-types"
    String type = model.getType();          // "amis"

    // 访问可选属性
    String icon = model.getIcon();
    String description = model.getDescription().orElse("");

    // 访问扩展属性
    String schemaId = (String) model.getExtraProp("schemaId")
      .orElse(model.getPath());
  });
}
```

### 4. 自定义实现

```java
public class CustomUIModel implements UIModel {
  private String title;
  private String path;
  private String type;
  private String icon;
  private Map<String, Object> extraProps = new HashMap<>();

  @Override
  public String getTitle() {
    return title;
  }

  @Override
  public String getPath() {
    return path;
  }

  @Override
  public String getType() {
    return type;
  }

  @Override
  public String getIcon() {
    return icon;
  }

  @Override
  public Map<String, Object> getExtraProps() {
    return extraProps;
  }

  @Override
  public Optional<Object> getExtraProp(String key) {
    return Optional.ofNullable(extraProps.get(key));
  }

  @Override
  public <T> Optional<T> getExtraProp(Class<T> clazz, String key) {
    return getExtraProp(key).filter(clazz::isInstance).map(clazz::cast);
  }

}
```

## 分层覆盖机制

UIModel 使用 `path` 作为唯一标识，支持多层数据源的覆盖机制。

### 加载优先级

UIModelProvider 通过 `@Priority` 注解定义优先级（数字越大优先级越高）：

1. **低代码层** (`LowcodeUIModelProvider`, Priority=100) - 最高优先级
2. **JAR包层** (`JarUIModelProvider`, Priority=10) - 默认优先级

相同 `path` 的 UIModel，高优先级的会覆盖低优先级的。

### 覆盖示例

**JAR 包中的默认配置**:
```json
// src/main/metadata/dev/users.ui-model.json
{
  "path": "/dev/{appName}/users",
  "type": "amis",
  "title": "用户管理"
}
```

**低代码平台中的覆盖配置**:
```json
// <home>/ui-model/dev/users.ui-model.json
{
  "path": "/dev/{appName}/users",
  "type": "micro",
  "title": "自定义用户管理",
  "microWebName": "custom-user-manager"
}
```

**最终生效**: 低代码平台的配置（type=micro）会覆盖 JAR 包的配置（type=amis）

### 路径匹配规则

- 精确匹配：`path` 必须完全一致才会覆盖
- 大小写不敏感：内部使用 `TreeMap(String::compareToIgnoreCase)` 存储
- 变量占位符：`{appName}` 等占位符作为路径的一部分参与匹配

## 与其他组件的关系

### UIRoute

UIModel 会被转换为 UIRoute 供前端路由使用：

```java
// UIRouteServiceImpl.java
private UIRoute buildRoute(UIModel uiModel) {
  var route = new UIRoute();
  route.setPath(uiModel.getPath());
  route.setRouteType(uiModel.getType());
  route.setName(uiModel.getTitle());

  // 根据 type 添加特定配置
  Map<String, Object> config = new HashMap<>();
  switch (uiModel.getType()) {
    case "amis":
      config.put("schemaId", uiModel.getExtraProp("schemaId").orElse(uiModel.getPath()));
      break;
    case "micro":
      config.put("entry", uiModel.getExtraProp("entry").orElse(""));
      break;
  }

  route.setConfig(config);
  return route;
}
```

### MenuModel

MenuModel 定义菜单结构，通常会引用 UIModel 的 `path`：

```json
{
  "fullName": "dev.fieldType",
  "title": "字段类型",
  "url": "/dev/{appName}/field-types",  // 对应 UIModel.path
  "icon": "fa fa-database",
  "showMode": "menu"
}
```

### UISchemaProvider

当 `type="amis"` 时，UIModel 会配合 UISchemaProvider 使用：

1. UIModel 定义 UI 的元数据和 `schemaId`
2. UISchemaProvider 提供实际的 Amis Schema 内容

```
UIModel (path=/dev/users, type=amis, schemaId=/dev/users)
    ↓
UISchemaProvider.getUISchema("/dev/users")
    ↓
返回实际的 Amis Schema JSON
```

### MicroWebModel

当 `type="micro"` 时，UIModel 会引用 MicroWebModel：

```json
{
  "type": "micro",
  "microWebName": "logic-flow-designer"  // 引用 MicroWebModel.name
}
```

JAR 与低代码文件 provider 会按 `microWebName` 查找 MicroWebModel，优先取其 `entry`，为 null 时回退 `publicPath`，写入 UIModel 的 `entry`。未找到时保留原值。

## 扩展开发指南

### 1. 实现自定义 UIModelProvider

```java
@Priority(50)  // SPI 排序权重
public class MyUIModelProvider implements UIModelProvider {
  private final Map<String, UIModel> modelMap = Map.of(); // 替换为实际加载结果

  @Override
  public Optional<UIModel> getUIModel(String path) {
    return Optional.ofNullable(modelMap.get(path));
  }

  @Override
  public Map<String, UIModel> getUIModelMap() {
    return Collections.unmodifiableMap(modelMap);
  }
}
```

在 `META-INF/services/com.ouroboros.uimodel.UIModelProvider` 中登记实现类全名。Provider 必须可由 SPI 创建；仅声明 Spring `@Component` 不会进入运行时聚合。需要 Spring 服务时沿用低代码 provider 的已注册 holder 方式。

### 2. 监听 UIModel 变更事件

```java
@Component
public class UIModelChangeListener {
  @EventListener
  public void onUIModelChanged(UIModelChangedEvent event) {
    // UIModel 发生变化时的处理逻辑
    // 例如：刷新缓存、通知前端等
  }
}
```

### 3. 添加自定义渲染类型

如果需要支持新的渲染类型（如 `type="custom"`）：

1. 在 `UIRouteServiceImpl.buildRoute()` 中添加处理逻辑
2. 前端实现对应的渲染组件
3. 定义该类型需要的 `extraProps` 规范

```java
switch (uiModel.getType()) {
  case "custom":
    config.put("customRenderer", uiModel.getExtraProp("renderer").orElse("default"));
    config.put("customConfig", uiModel.getExtraProp("config").orElse(Map.of()));
    break;
}
```

## 最佳实践

### 1. 命名规范

**path 命名**:
- 使用小写字母和短横线：`/dev/field-types`
- 避免使用下划线：~~`/dev/field_repository`~~
- 体现层级关系：`/dev/{appName}/module/submodule`

**title 命名**:
- 简洁明了，2-5个字为佳
- 避免冗余前缀：`字段类型管理` 而非 `系统字段类型管理`

### 2. icon 选择

- 优先使用 FontAwesome 图标：`fa fa-database`
- 保持风格一致性
- 避免过于复杂的图标

### 3. description 编写

- 一句话说明 UI 的核心功能
- 不超过 50 字
- 突出关键操作："支持增删改查操作"

### 4. extraProps 使用

- 只在 extraProps 中存放渲染类型特定的配置
- 避免存放业务数据
- 使用类型安全的 `getExtraProp(Class<T>, String)` 访问

### 5. 分层覆盖

- 基础配置放在 JAR 包中（Priority=10）
- 可定制部分通过低代码覆盖（Priority=100）
- 避免在同一优先级层重复定义相同 path

## 常见问题

### Q: UIModel 和 UISchema 的区别？

**UIModel**: UI 的**元数据**，定义"有哪些 UI、在哪里、怎么渲染"
**UISchema**: UI 的**内容定义**，定义"UI 具体长什么样"（仅适用于 amis 类型）

关系：
```
UIModel (type=amis, schemaId=/dev/users)
    ↓ 引用
UISchema (/dev/users) → 具体的 Amis JSON 配置
```

### Q: 为什么不直接在 UIModel 中存储 UI 内容？

因为：
1. **职责分离**：UIModel 关注路由和组织，UISchema 关注渲染
2. **灵活性**：同一个 UIModel 可以引用不同的 Schema（分层覆盖）
3. **性能**：UIModel 需要全量加载用于路由，UISchema 按需加载

### Q: 如何实现 UI 的动态启用/禁用？

UIModel 没有内置 `enabled` 字段，当前 `UIRouteServiceImpl` 也不读取或过滤它。仅添加 extraProps 不会禁用路由；下面只是自定义 provider 的过滤思路，需在正式 provider 中实现后才生效：

```java
// 定义
{
  "enabled": false
}

// 使用
boolean enabled = uiModel.getExtraProp(Boolean.class, "enabled").orElse(true);
if (!enabled) {
  // 跳过该 UI
}
```

如果这个需求频繁出现，可以考虑在 UIModel 接口中添加 `default` 方法：

```java
default boolean isEnabled() {
  return getExtraProp(Boolean.class, "enabled").orElse(true);
}
```

### Q: 可以在运行时修改 UIModel 吗？

取决于 UIModelProvider 的实现：
- **JarUIModelProvider**: 只读，加载自 JAR 包
- **LowcodeUIModelProvider**: 读取 `<home>/ui-model/` 下的文件，兼容 `.json` 并让 `.ui-model.json` 覆盖同键旧格式；它本身没有写接口

要实现运行时修改，需要：
1. 实现支持写操作的 UIModelProvider
2. 修改后发布 `UIModelChangedEvent` 事件
3. 先刷新 provider 的数据，再发布 `UIModelChangedEvent` 供 UIRouteService 重建路由；该事件不会自动让 provider 重扫文件。现有低代码文件重读发生在 `OuroborosMetadataRefreshingEvent`

## 相关文档

- [ouroboros-ui-model 设计概要](../DESIGN.md)
- [开发模块模式指南](../../../docs/guides/development-module-guide.md)
- [UI Schema 体系](../DESIGN.md#32-ui-schema-体系)
- [微前端开发指南](../../../frontends/web/README.md)
