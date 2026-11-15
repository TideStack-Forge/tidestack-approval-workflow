package com.ouroboros.workflow.approval.testkit.support;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.stream.Collectors;

public class ScenarioResourceLoader {

  public String load(String resourcePath) {
    InputStream stream = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourcePath);
    Objects.requireNonNull(stream, "Missing scenario resource: " + resourcePath);
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
      return reader.lines().collect(Collectors.joining("\n"));
    } catch (Exception e) {
      throw new IllegalStateException("Failed to load scenario resource: " + resourcePath, e);
    }
  }
}
