package com.ouroboros.fieldtype;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import io.vavr.control.Either;

public class DefaultFieldTypeCatalog implements FieldTypeCatalog {
  private final List<FieldTypeDefinitionProvider> providers;
  private final FieldTypeCompiler compiler;
  /** 元数据按上下文缓存：加载要遍历全部 provider 并构建 TreeMap，启动期会被调用数千次。 */
  private final Map<String, List<FieldTypeDefinition>> metadataCache = new ConcurrentHashMap<>();
  /** 名称（忽略大小写）到定义的索引，避免每次按名查找都线性扫描整个列表。 */
  private final Map<String, Map<String, FieldTypeDefinition>> metadataIndexCache = new ConcurrentHashMap<>();

  public DefaultFieldTypeCatalog(List<FieldTypeDefinitionProvider> providers, FieldTypeCompiler compiler) {
    this.providers = providers == null ? Collections.emptyList() : providers;
    this.compiler = compiler;
  }

  @Override
  public Optional<FieldTypeDefinition> getMetadata(FieldTypeCatalogContext context, String name) {
    if (name == null) {
      return Optional.empty();
    }
    // 索引以小写名为键，查询必须同样归一化：旧实现在这里是区分大小写的线性扫描
    // （equalsIgnoreCase），改成索引后若直接取原名，大小写不同的调用方会全部查不到。
    return Optional.ofNullable(metadataIndexOf(context).get(name.toLowerCase(Locale.ROOT)));
  }

  @Override
  public List<FieldTypeDefinition> listMetadata(FieldTypeCatalogContext context) {
    return metadataCache.computeIfAbsent(cacheKey(context), key -> Collections.unmodifiableList(loadMetadata(context)));
  }

  private Map<String, FieldTypeDefinition> metadataIndexOf(FieldTypeCatalogContext context) {
    var key = cacheKey(context);
    listMetadata(context);
    return metadataIndexCache.computeIfAbsent(key, ignored -> {
      var index = new HashMap<String, FieldTypeDefinition>();
      for (FieldTypeDefinition definition : listMetadata(context)) {
        index.putIfAbsent(definition.getName().toLowerCase(Locale.ROOT), definition);
      }
      return index;
    });
  }

  private static String cacheKey(FieldTypeCatalogContext context) {
    if (context == null || context.getScene() == null) {
      return "";
    }
    var appName = context.getAppName();
    return context.getScene().name() + "|" + (appName == null ? "" : appName);
  }

  private List<FieldTypeDefinition> loadMetadata(FieldTypeCatalogContext context) {
    var merged = new TreeMap<String, FieldTypeDefinition>(String::compareToIgnoreCase);
    providers.stream()
        .filter(provider -> provider != null && provider.supports(context))
        .map(provider -> provider.load(context))
        .filter(Objects::nonNull)
        .flatMap(Collection::stream)
        .filter(Objects::nonNull)
        .forEach(definition -> {
          var existing = merged.get(definition.getName());
          if (existing == null || layerPriority(context, definition.getSource()) >= layerPriority(context, existing.getSource())) {
            merged.put(definition.getName(), definition);
          }
        });
    return new ArrayList<FieldTypeDefinition>(merged.values());
  }

  @Override
  public Either<FieldTypeCompileException, CompiledFieldType> compile(FieldTypeCatalogContext context,
                                                                      String name,
                                                                      Map<String, Object> params) {
    return getMetadata(context, name)
        .map(definition -> compiler.compile(definition, params, context))
        .orElseGet(() -> Either.left(new FieldTypeCompileException(
            "字段类型不存在: " + name,
            name,
            null,
            null,
            "resolve")));
  }

  private int layerPriority(FieldTypeCatalogContext context, FieldTypeDefinitionSource source) {
    if (source == null) {
      return -1;
    }
    if (context != null && context.getScene() == FieldTypeCatalogScene.DEV_APP) {
      switch (source) {
        case APP:
          return 5;
        case DEV_BUILTIN:
          return 4;
        case RUNTIME_EXTEND:
          return 3;
        case JAR:
          return 2;
        case DEFAULT:
          return 1;
        default:
          return 0;
      }
    }
    switch (source) {
      case RUNTIME_EXTEND:
        return 3;
      case JAR:
        return 2;
      case DEFAULT:
        return 1;
      default:
        return 0;
    }
  }
}
