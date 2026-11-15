package com.ouroboros.workflow.approval.testkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

class EngineTestkitPackagingIT {
  @Test
  void packagesOnlyTestJarArtifact() {
    Path buildDirectory = Paths.get(System.getProperty("project.build.directory"));
    String finalName = System.getProperty("project.build.finalName");
    String packaging = System.getProperty("project.packaging");

    assertEquals(
        "pom",
        packaging,
        "engine-testkit should publish POM metadata as its main artifact"
    );

    assertFalse(
        Files.exists(buildDirectory.resolve(finalName + ".jar")),
        "engine-testkit should not publish a main jar artifact"
    );
    assertTrue(
        Files.isRegularFile(buildDirectory.resolve(finalName + "-tests.jar")),
        "engine-testkit should continue publishing the attached tests jar"
    );
  }
}
