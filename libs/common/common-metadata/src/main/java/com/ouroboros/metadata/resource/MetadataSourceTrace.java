package com.ouroboros.metadata.resource;

import java.util.Objects;

public record MetadataSourceTrace(String resourceName, String key, Status status, MetadataDiagnostic diagnostic) {
  public MetadataSourceTrace {
    Objects.requireNonNull(resourceName, "resourceName");
    Objects.requireNonNull(status, "status");
  }

  public static MetadataSourceTrace accepted(String resourceName, String key) {
    return new MetadataSourceTrace(resourceName, key, Status.ACCEPTED, null);
  }

  public MetadataSourceTrace overridden() {
    return new MetadataSourceTrace(resourceName, key, Status.OVERRIDDEN, diagnostic);
  }

  public static MetadataSourceTrace skipped(String resourceName, String key, MetadataDiagnostic diagnostic) {
    return new MetadataSourceTrace(resourceName, key, Status.SKIPPED, diagnostic);
  }

  public enum Status {
    ACCEPTED,
    OVERRIDDEN,
    SKIPPED
  }
}
