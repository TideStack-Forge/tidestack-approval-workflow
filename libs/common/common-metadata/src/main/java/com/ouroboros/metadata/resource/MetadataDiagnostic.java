package com.ouroboros.metadata.resource;

import java.util.Objects;

public record MetadataDiagnostic(Level level, String resourceName, String message, Throwable cause) {
  public MetadataDiagnostic {
    Objects.requireNonNull(level, "level");
    Objects.requireNonNull(resourceName, "resourceName");
    Objects.requireNonNull(message, "message");
  }

  public static MetadataDiagnostic warn(String resourceName, String message) {
    return new MetadataDiagnostic(Level.WARN, resourceName, message, null);
  }

  public static MetadataDiagnostic warn(String resourceName, String message, Throwable cause) {
    return new MetadataDiagnostic(Level.WARN, resourceName, message, cause);
  }

  public static MetadataDiagnostic error(String resourceName, String message) {
    return new MetadataDiagnostic(Level.ERROR, resourceName, message, null);
  }

  public static MetadataDiagnostic error(String resourceName, String message, Throwable cause) {
    return new MetadataDiagnostic(Level.ERROR, resourceName, message, cause);
  }

  public enum Level {
    WARN,
    ERROR
  }
}
