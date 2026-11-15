package com.ouroboros.metadata.resource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.ouroboros.util.ResourceUtil;

public final class ClasspathMetadataFiles {
  public static final String METADATA_DIRECTORY = ClasspathMetadataSource.METADATA_DIRECTORY;
  private static final String OUROBOROS_DIRECTORY = "META-INF/ouroboros/";

  private final ClassLoader classLoader;

  public ClasspathMetadataFiles() {
    this(ResourceUtil.class.getClassLoader());
  }

  public ClasspathMetadataFiles(ClassLoader classLoader) {
    this.classLoader = Objects.requireNonNull(classLoader, "classLoader");
  }

  public Stream<String> legacyThenMetadataContentStreamForOverride(String legacyResourceName) {
    return legacyThenMetadataResourcesForOverride(legacyResourceName).stream()
        .map(MetadataResource::content);
  }

  public List<MetadataResource> legacyThenMetadataResourcesForOverride(String legacyResourceName) {
    return Stream.concat(
            resourcesForOverride(legacyResourceName).stream(),
            resourcesForOverride(toMetadataResourceName(legacyResourceName)).stream())
        .toList();
  }

  public Stream<String> metadataContentStreamForOverride(String metadataFileName) {
    return resourcesForOverride(METADATA_DIRECTORY + metadataFileName).stream()
        .map(MetadataResource::content);
  }

  private List<MetadataResource> resourcesForOverride(String resourceName) {
    return ResourceUtil.getResourceUrlListForOverride(classLoader, resourceName).stream()
        .map(this::readResource)
        .toList();
  }

  private MetadataResource readResource(URL url) {
    try (var inputStream = url.openStream();
         var reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
      return new MetadataResource(url.toExternalForm(), reader.lines().collect(Collectors.joining("\n")));
    } catch (IOException e) {
      throw new UncheckedIOException("读取classpath元数据资源失败: " + url, e);
    }
  }

  private String toMetadataResourceName(String legacyResourceName) {
    if (legacyResourceName.startsWith(OUROBOROS_DIRECTORY)) {
      return METADATA_DIRECTORY + legacyResourceName.substring(OUROBOROS_DIRECTORY.length());
    }
    return METADATA_DIRECTORY + legacyResourceName;
  }
}
