package com.ouroboros.uischema;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.annotation.Priority;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vavr.control.Try;

import com.ouroboros.metadata.resource.ClasspathMetadataSource;
import com.ouroboros.metadata.resource.MetadataDiagnostic;
import com.ouroboros.metadata.resource.MetadataResource;
import com.ouroboros.metadata.resource.MetadataSource;
import com.ouroboros.metadata.resource.TypedMetadataCatalog;
import com.ouroboros.metadata.resource.TypedMetadataSpec;
import com.ouroboros.uischema.schema.SchemaNode;
import com.ouroboros.uischema.wrapper.SchemaNodeWrapper;
import com.ouroboros.util.Json;
import com.ouroboros.util.Strings;

@Priority(50)
public class JarUISchemaProvider implements UISchemaProvider {
  private static final Logger logger = LoggerFactory.getLogger(JarUISchemaProvider.class);
  private static final String LEGACY_SCHEMA_DIRECTORY = "META-INF/ouroboros/ui-schema/";
  private static final Pattern JSON_FILE_PATTERN = Pattern.compile(".*\\.json");
  private static final String METADATA_SCHEMA_SUFFIX = ".ui-schema.json";
  private Map<String, Object> schemaMap;

  private final Supplier<List<MetadataResource>> resources;

  private static final Supplier<List<MetadataResource>> CLASSPATH_RESOURCES = cacheResources(new ClasspathMetadataSource());

  private static Supplier<List<MetadataResource>> cacheResources(MetadataSource source) {
    return new Supplier<>() {
      private List<MetadataResource> snapshot;

      @Override
      public synchronized List<MetadataResource> get() {
        if (snapshot != null) {
          return snapshot;
        }
        var loaded = List.copyOf(source.findResources(metadataSpec()));
        var result = new TypedMetadataCatalog(ignored -> loaded).load(metadataSpec());
        if (result.diagnostics().isEmpty()) {
          snapshot = loaded;
        }
        return loaded;
      }
    };
  }

  public JarUISchemaProvider() {
    this(CLASSPATH_RESOURCES);
  }

  JarUISchemaProvider(MetadataSource source) {
    this(cacheResources(source));
  }

  private JarUISchemaProvider(Supplier<List<MetadataResource>> resources) {
    this.resources = resources;
    loadDefinitions();
  }

  private static Try<NamedSchema> parseSchema(MetadataResource resource) {
    if (resource.resourceName().endsWith(METADATA_SCHEMA_SUFFIX)) {
      return parseWrappedSchema(resource);
    }
    String path = "/" + stripJsonSuffix(resource.resourceName(), ".json");
    return Json.tryToBean(SchemaNode.class, resource.content())
        .filter(Objects::nonNull)
        .map(schema -> new NamedSchema(path, schema));
  }

  private static Try<NamedSchema> parseWrappedSchema(MetadataResource resource) {
    return Json.tryToBean(SchemaNodeWrapper.class, resource.content())
        .flatMap(wrapper -> {
          if (StringUtils.isBlank(wrapper.getName()) || wrapper.getSchema() == null) {
            return Try.<NamedSchema>failure(new IllegalArgumentException(
                "metadata ui-schema wrapper requires name and schema"));
          }
          return Try.success(new NamedSchema(normalizeSchemaPath(wrapper.getName()), wrapper.getSchema()));
        });
  }

  private static String normalizeSchemaPath(String name) {
    String trimmedName = name.trim();
    if (trimmedName.startsWith("/")) {
      return trimmedName;
    }
    if (trimmedName.contains("/")) {
      return "/" + trimmedName;
    }
    return "/" + Stream.of(trimmedName.split("\\."))
        .map(Strings::toKebabCase)
        .collect(Collectors.joining("/"));
  }

  private static String stripJsonSuffix(String resourceName, String suffix) {
    if (resourceName.endsWith(suffix)) {
      return resourceName.substring(0, resourceName.length() - suffix.length());
    }
    return resourceName.substring(0, resourceName.length() - 5);
  }

  private static void logDiagnostic(MetadataDiagnostic diagnostic) {
    if (diagnostic.level() == MetadataDiagnostic.Level.ERROR) {
      logger.error("UI Schema 元数据文件 {} 加载失败: {}", diagnostic.resourceName(), diagnostic.message(), diagnostic.cause());
    } else {
      logger.warn("UI Schema 元数据文件 {} 加载跳过: {}", diagnostic.resourceName(), diagnostic.message(), diagnostic.cause());
    }
  }

  private synchronized Map<String, Object> loadDefinitions() {
    if (schemaMap != null) {
      return schemaMap;
    }
    try {
      var spec = metadataSpec();
      var result = new TypedMetadataCatalog(ignored -> resources.get()).load(spec);
      result.diagnostics().forEach(JarUISchemaProvider::logDiagnostic);
      Map<String, Object> schemas = result.values().values().stream()
          .collect(() -> new TreeMap<String, Object>(String::compareToIgnoreCase),
              (map, namedSchema) -> map.put(namedSchema.path(), namedSchema.schema()), Map::putAll);
      if (result.diagnostics().isEmpty()) {
        schemaMap = schemas;
      }
      return schemas;
    } catch (Exception e) {
      logger.error("加载 JAR 元数据失败，跳过本次加载: JarUISchemaProvider", e);
      return Map.of();
    }
  }

  private static TypedMetadataSpec<NamedSchema> metadataSpec() {
    return TypedMetadataSpec.<NamedSchema>builder(NamedSchema.class)
        .typedSuffix(METADATA_SCHEMA_SUFFIX)
        .sourcePlan(TypedMetadataSpec.SourcePlan.LEGACY_DIRECTORY_THEN_TYPED_SUFFIX)
        .legacyDirectory(LEGACY_SCHEMA_DIRECTORY)
        .legacyFilePattern(JSON_FILE_PATTERN)
        .resourceParser(JarUISchemaProvider::parseSchema)
        .keyExtractor(NamedSchema::path)
        .build();
  }

  @Override
  public Optional<Object> getUISchema(String path) {
    return Optional.ofNullable(loadDefinitions()).map(map -> map.get(path));
  }

  @Override
  public Map<String, Object> getUISchemaMap() {
    return Optional.ofNullable(loadDefinitions()).map(Collections::unmodifiableMap).orElse(Collections.emptyMap());
  }

  public record NamedSchema(String path, Object schema) {
  }
}
