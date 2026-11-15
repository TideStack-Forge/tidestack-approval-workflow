package com.ouroboros.metadata.resource;

import java.util.Objects;

public record MetadataResource(String resourceName, String content) {
  public MetadataResource {
    Objects.requireNonNull(resourceName, "resourceName");
    Objects.requireNonNull(content, "content");
  }
}
