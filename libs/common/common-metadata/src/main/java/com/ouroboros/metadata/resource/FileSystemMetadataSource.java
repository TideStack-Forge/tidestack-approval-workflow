package com.ouroboros.metadata.resource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

public class FileSystemMetadataSource implements MetadataSource {
  private static final String JSON_SUFFIX = ".json";

  private final Path rootPath;

  public FileSystemMetadataSource(Path rootPath) {
    this.rootPath = Objects.requireNonNull(rootPath, "rootPath");
  }

  @Override
  public List<MetadataResource> findResources(TypedMetadataSpec<?> spec) {
    if (!Files.exists(rootPath)) {
      return List.of();
    }
    if (spec.sourcePlan() == TypedMetadataSpec.SourcePlan.TYPED_SUFFIX_ONLY) {
      return findBySuffix(spec.typedSuffix(), path -> true);
    }
    Pattern legacyPattern = spec.legacyFilePattern().orElse(Pattern.compile(".*" + Pattern.quote(JSON_SUFFIX)));
    List<MetadataResource> legacyResources = findBySuffix(JSON_SUFFIX, path -> legacyPattern.matcher(relativeName(path)).matches()
        && !path.getFileName().toString().endsWith(spec.typedSuffix()));
    List<MetadataResource> typedResources = findBySuffix(spec.typedSuffix(), path -> true);
    return concat(legacyResources, typedResources);
  }

  private List<MetadataResource> findBySuffix(String suffix, PathFilter filter) {
    try (var walkStream = Files.walk(rootPath)) {
      return walkStream
          .filter(Files::isRegularFile)
          .filter(path -> path.getFileName().toString().endsWith(suffix))
          .filter(filter::matches)
          .sorted(Comparator.comparing(this::relativeName))
          .map(this::readResource)
          .toList();
    } catch (UncheckedIOException | IOException e) {
      throw new IllegalStateException("加载元数据目录失败: " + rootPath, e);
    }
  }

  private MetadataResource readResource(Path path) {
    try {
      return new MetadataResource(relativeName(path), Files.readString(path, StandardCharsets.UTF_8));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private String relativeName(Path path) {
    return rootPath.relativize(path).toString().replace('\\', '/');
  }

  private static List<MetadataResource> concat(List<MetadataResource> legacyResources, List<MetadataResource> typedResources) {
    if (legacyResources.isEmpty()) {
      return typedResources;
    }
    if (typedResources.isEmpty()) {
      return legacyResources;
    }
    var resources = new java.util.ArrayList<MetadataResource>(legacyResources.size() + typedResources.size());
    resources.addAll(legacyResources);
    resources.addAll(typedResources);
    return resources;
  }

  private interface PathFilter {
    boolean matches(Path path);
  }
}
