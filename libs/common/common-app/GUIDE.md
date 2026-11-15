# Common App - 应用支持组件指南

本文档描述了 `common-app` 模块中的核心类和接口，用于提供应用上下文、生命周期事件和 Web 应用信息管理。

## 模块概述

`common-app` 模块提供了以下核心功能：

1. **应用上下文管理** - 获取应用路径、应用信息等
2. **生命周期事件** - 应用刷新时的事件发布与监听
3. **Web 应用信息扩展** - 动态提供和修改 Web 应用配置信息
4. **低代码提供者标记** - 标识低代码元数据提供者

## 核心接口与类

### 1. AppContext - 应用上下文

路径: `com.ouroboros.AppContext`

应用上下文接口，提供应用基础信息访问。

#### 主要方法

```java
public interface AppContext {
  // 获取应用主路径
  String getHomePath();

  // 获取应用信息(从 app.metadata.json)
  Optional<AppInfo> getAppInfo();

  // 是否为调试模式
  Boolean isDebug();
}
```

#### 使用示例

```java
@Configuration
public class AppConfig {

  @Bean
  public AppContext getAppContext() {
    return () -> "/path/to/app/home";
  }
}

// 使用应用上下文
@Service
public class MyService {
  @Autowired
  private AppContext appContext;

  public void doSomething() {
    String homePath = appContext.getHomePath();
    Optional<AppInfo> appInfo = appContext.getAppInfo();

    appInfo.ifPresent(info -> {
      String appName = info.getName();
      String title = info.getTitle();
      String logo = info.getLogo();
    });
  }
}
```

### 2. AppInfo - 应用信息模型

路径: `com.ouroboros.model.AppInfo`

应用元数据模型，通常从 `app.metadata.json` 文件加载。

#### 属性说明

```java
public class AppInfo {
  private String name;           // 应用名称
  private String description;    // 应用描述
  private String title;          // 应用标题
  private String icon;           // 应用图标
  private String logo;           // 应用 Logo
  private String homePage;       // 首页路径
  private String defaultTheme;   // 默认主题 (dark/light)
  private Custom custom;         // 自定义配置

  public static class Custom {
    private String loginMicroWeb;   // 登录页微前端
    private String topBarMicroWeb;  // 顶栏微前端
    private String sideBarMicroWeb; // 侧边栏微前端
  }
}
```

#### 使用示例

```java
AppInfo appInfo = new AppInfo()
    .setName("MyApp")
    .setTitle("我的应用")
    .setIcon("icon.png")
    .setLogo("logo.png")
    .setHomePage("/dashboard")
    .setDefaultTheme("dark");

Custom custom = new Custom();
custom.setLoginMicroWeb("login-micro-web");
custom.setTopBarMicroWeb("top-bar-micro-web");
appInfo.setCustom(custom);
```

### 3. WebAppInfoContributor - Web 应用信息贡献者

路径: `com.ouroboros.WebAppInfoContributor`

SPI 接口，用于向前端应用信息聚合流程贡献配置片段。系统会自动收集所有实现并合并到 `/sys/web-app-info` API。

#### 接口定义

```java
public interface WebAppInfoContributor {
  Map<String, Object> getWebAppInfo();
}
```

#### 使用示例

```java
@Service
public class MyWebAppInfoContributor implements WebAppInfoContributor {

  @Override
  public Map<String, Object> getWebAppInfo() {
    Map<String, Object> info = new HashMap<>();
    info.put("version", "1.0.0");
    info.put("buildTime", "2025-12-10");
    info.put("features", Arrays.asList("feature1", "feature2"));
    return info;
  }
}
```

#### 实际使用案例

```java
// 提供应用配置信息
@Service
public class AppConfigWebAppInfoContributor {
  private ConfigurationService configurationService;

  public static class SingletonHolder implements WebAppInfoContributor {
    public static AppConfigWebAppInfoContributor INSTANCE;

    @Override
    public Map<String, Object> getWebAppInfo() {
      return Optional.ofNullable(INSTANCE)
          .map(AppConfigWebAppInfoContributor::getWebAppInfo)
          .orElseGet(Collections::emptyMap);
    }
  }

  public Map<String, Object> getWebAppInfo() {
    var infoMap = new HashMap<String, Object>();
    configurationService.getByKey(String.class, "appName")
        .ifPresent(appName -> infoMap.put("appName", appName));
    configurationService.getByKey(String.class, "logo")
        .ifPresent(logo -> infoMap.put("logo", logo));
    infoMap.put("config", configurationService.getMapByScope("frontend"));
    return infoMap;
  }
}
```

### 4. WebAppInfoModifier - Web 应用信息修改器

路径: `com.ouroboros.WebAppInfoModifier`

SPI 接口，用于修改最终的 Web 应用信息。在所有 `WebAppInfoContributor` 提供信息后执行。

#### 接口定义

```java
public interface WebAppInfoModifier {
  void modify(Map<String, Object> webAppInfo);
}
```

#### 使用示例

```java
@Service
public class MyWebAppInfoModifier implements WebAppInfoModifier {

  @Override
  public void modify(Map<String, Object> webAppInfo) {
    // 修改或增强 web app 信息
    webAppInfo.put("enhanced", true);

    // 可以基于现有信息进行计算
    if (webAppInfo.containsKey("version")) {
      String version = (String) webAppInfo.get("version");
      webAppInfo.put("majorVersion", version.split("\\.")[0]);
    }
  }
}
```

### 5. 生命周期事件系统

路径: `com.ouroboros.lifecycle.event.*`

Ouroboros 应用刷新生命周期事件体系。

#### 事件层次结构

```
OuroborosEvent (抽象基类)
├── OuroborosContextRefreshingEvent   - 上下文刷新开始
├── OuroborosMetadataRefreshingEvent  - 元数据刷新开始
├── OuroborosMetadataRefreshedEvent   - 元数据刷新完成
├── OuroborosModelRefreshingEvent     - 模型刷新开始
├── OuroborosModelRefreshedEvent      - 模型刷新完成
└── OuroborosContextRefreshedEvent    - 上下文刷新完成
```

#### 事件发布顺序

`OuroborosContextRefreshLifecycle` 定义了标准的事件发布顺序：

1. **OuroborosContextRefreshingEvent** - 上下文刷新开始
2. **OuroborosMetadataRefreshingEvent** - 元数据刷新开始
3. **OuroborosMetadataRefreshedEvent** - 元数据刷新完成
4. **OuroborosModelRefreshingEvent** - 模型刷新开始
5. **OuroborosModelRefreshedEvent** - 模型刷新完成
6. **OuroborosContextRefreshedEvent** - 上下文刷新完成

#### 使用示例

```java
// 发布生命周期事件
@Configuration
public class AppRuntimeAutoConfiguration {

  @Autowired
  ApplicationEventPublisher publisher;

  @EventListener(ApplicationStartedEvent.class)
  void onApplicationStarted(ApplicationStartedEvent event) {
    // 发布完整的生命周期事件序列
    OuroborosContextRefreshLifecycle.publish(event, e -> publisher.publishEvent(e));
  }
}

// 监听特定生命周期事件
@Component
public class MetadataRefreshListener {

  @EventListener
  public void onMetadataRefreshing(OuroborosMetadataRefreshingEvent event) {
    System.out.println("元数据刷新开始...");
    // 执行元数据刷新前的准备工作
  }

  @EventListener
  public void onMetadataRefreshed(OuroborosMetadataRefreshedEvent event) {
    System.out.println("元数据刷新完成");
    // 执行元数据刷新后的后续工作
  }

  @EventListener
  public void onModelRefreshed(OuroborosModelRefreshedEvent event) {
    System.out.println("模型刷新完成");
    // 重新加载模型相关的缓存
  }
}
```

### 6. @LowCodeProvider - 低代码提供者注解

路径: `com.ouroboros.annotation.LowCodeProvider`

标记注解，用于标识低代码元数据提供者类。通常用于标识从文件系统或 VCS 加载元数据的提供者。

#### 使用示例

```java
@Component
public class FileConfigurationMetadataProvider {

  @LowCodeProvider
  public static class SingletonHolder implements ConfigurationMetadataProvider {
    public static FileConfigurationMetadataProvider INSTANCE;

    @Override
    public Stream<ConfigurationMetadata> getMetadata() {
      return Optional.ofNullable(INSTANCE)
          .map(FileConfigurationMetadataProvider::getAllConfigurations)
          .orElseGet(Stream::empty);
    }
  }
}

// UI Schema 提供者示例
@Component
public class AppUiSchemaSchemaProvider {

  @LowCodeProvider
  public static class SingletonHolder implements SchemaProvider {
    public static AppUiSchemaSchemaProvider INSTANCE;

    @Override
    public Stream<Schema> provide() {
      return Optional.ofNullable(INSTANCE)
          .map(AppUiSchemaSchemaProvider::getSchemas)
          .orElseGet(Stream::empty);
    }
  }
}
```

## 典型使用场景

### 场景 1: 自定义应用信息

```java
@Configuration
public class CustomAppConfig {

  @Bean
  public AppContext appContext(@Value("${app.home}") String homePath) {
    return new AppContext() {
      @Override
      public String getHomePath() {
        return homePath;
      }

      @Override
      public Boolean isDebug() {
        return "dev".equals(System.getProperty("spring.profiles.active"));
      }
    };
  }
}
```

### 场景 2: 扩展 Web 应用信息

```java
// 1. 贡献基础信息
@Service
public class VersionInfoContributor implements WebAppInfoContributor {

  @Override
  public Map<String, Object> getWebAppInfo() {
    Map<String, Object> info = new HashMap<>();
    info.put("version", "1.0.0");
    info.put("apiVersion", "v1");
    return info;
  }
}

// 2. 修改最终信息
@Service
public class InfoEnhancer implements WebAppInfoModifier {

  @Override
  public void modify(Map<String, Object> webAppInfo) {
    // 添加计算字段
    webAppInfo.put("timestamp", System.currentTimeMillis());

    // 基于环境变量调整配置
    if ("production".equals(System.getenv("ENV"))) {
      webAppInfo.put("debugEnabled", false);
    }
  }
}
```

### 场景 3: 监听应用刷新完成加载数据

```java
@Component
public class DataLoader {

  @EventListener
  public void onContextRefreshed(OuroborosContextRefreshedEvent event) {
    // 应用上下文完全刷新后执行
    loadInitialData();
    buildCache();
  }

  @EventListener
  public void onModelRefreshed(OuroborosModelRefreshedEvent event) {
    // 模型刷新后重新生成运行时类
    regenerateRuntimeClasses();
  }

  private void loadInitialData() {
    // 加载初始数据
  }

  private void buildCache() {
    // 构建缓存
  }

  private void regenerateRuntimeClasses() {
    // 重新生成动态类
  }
}
```

## 注意事项

1. **应用信息文件** - `AppContext.getAppInfo()` 默认读取应用根目录下的 `app.metadata.json` 文件

2. **WebAppInfoContributor 执行顺序** - 所有 Contributor 的结果会合并，后执行的会覆盖先执行的同名字段

3. **生命周期事件顺序** - 必须按照 `OuroborosContextRefreshLifecycle.publish()` 定义的顺序发布事件

4. **@LowCodeProvider 用途** - 主要用于区分低代码元数据提供者和其他类型的提供者，便于管理和识别

5. **单例模式** - 许多提供者使用静态内部类 SingletonHolder 模式，在构造函数中初始化 INSTANCE

6. **线程安全** - 生命周期事件发布时需要同步，避免并发问题

## 与其他模块的关系

- **ouroboros-app-runtime** - 实现 AppContext，发布生命周期事件
- **ouroboros-configuration** - 通过 WebAppInfoContributor 贡献配置信息
- **ouroboros-ui-model** - 监听生命周期事件，刷新 UI Schema
- **ouroboros-business-model** - 监听 ModelRefreshed 事件重新加载业务模型
- **ouroboros-logicflow** - 监听 MetadataRefreshed 事件重新加载逻辑流模型
