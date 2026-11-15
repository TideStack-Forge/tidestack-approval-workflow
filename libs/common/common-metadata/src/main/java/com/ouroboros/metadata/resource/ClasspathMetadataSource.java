package com.ouroboros.metadata.resource;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

import com.ouroboros.util.ResourceUtil;

public class ClasspathMetadataSource implements MetadataSource {
  public static final String METADATA_DIRECTORY = "META-INF/ouroboros/metadata/";

  private final ClassLoader classLoader;

  public ClasspathMetadataSource() {
    this(ResourceUtil.class.getClassLoader());
  }

  public ClasspathMetadataSource(ClassLoader classLoader) {
    this.classLoader = Objects.requireNonNull(classLoader, "classLoader");
  }

  @Override
  public List<MetadataResource> findResources(TypedMetadataSpec<?> spec) {
    if (spec.sourcePlan() == TypedMetadataSpec.SourcePlan.TYPED_SUFFIX_ONLY) {
      return findTypedResources(spec.typedSuffix());
    }

    String legacyDirectory = spec.legacyDirectory()
        .orElseThrow(() -> new IllegalArgumentException("legacyDirectory is required for legacy source plan"));
    Pattern legacyPattern = spec.legacyFilePattern()
        .orElseThrow(() -> new IllegalArgumentException("legacyFilePattern is required for legacy source plan"));
    return findLegacyThenTypedResources(legacyDirectory, legacyPattern, spec.typedSuffix());
  }

  public List<MetadataResource> findTypedResources(String typedSuffix) {
    return findDirectoryResourcesForOverride(METADATA_DIRECTORY, suffixPattern(typedSuffix));
  }

  public List<MetadataResource> findTypedResourcesInClasspathOrder(String typedSuffix) {
    return findDirectoryResources(METADATA_DIRECTORY, suffixPattern(typedSuffix));
  }

  public List<MetadataResource> findResourcesForOverride(String directory, Pattern filePattern) {
    return findDirectoryResourcesForOverride(directory, filePattern);
  }

  public List<MetadataResource> findLegacyThenTypedResources(String legacyDirectory, Pattern legacyPattern, String typedSuffix) {
    var resources = new ArrayList<MetadataResource>();
    resources.addAll(findDirectoryResourcesForOverride(legacyDirectory, legacyPattern));
    resources.addAll(findTypedResources(typedSuffix));
    return resources;
  }

  private List<MetadataResource> findDirectoryResources(String directory, Pattern filePattern) {
    return ResourceUtil.getResourceNameAndContentListFromDirectory(classLoader, directory, filePattern)
        .stream()
        .map(resource -> new MetadataResource(resource._1(), resource._2()))
        .toList();
  }

  private List<MetadataResource> findDirectoryResourcesForOverride(String directory, Pattern filePattern) {
    return ResourceUtil.getResourceNameAndContentListFromDirectoryForOverride(classLoader, directory, filePattern)
        .stream()
        .map(resource -> new MetadataResource(resource._1(), resource._2()))
        .toList();
  }

  private Pattern suffixPattern(String typedSuffix) {
    return Pattern.compile(".*" + Pattern.quote(typedSuffix));
  }
}
