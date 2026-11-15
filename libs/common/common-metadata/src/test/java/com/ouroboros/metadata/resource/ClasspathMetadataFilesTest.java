package com.ouroboros.metadata.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ClasspathMetadataFilesTest {
  @TempDir
  Path tempDir;

  @Test
  void legacyThenMetadataContentStreamReadsBothFixedFilesInOverrideOrder() throws Exception {
    Path legacyDirectory = Files.createDirectories(tempDir.resolve("META-INF/ouroboros"));
    Path metadataDirectory = Files.createDirectories(tempDir.resolve(ClasspathMetadataFiles.METADATA_DIRECTORY));
    Files.writeString(legacyDirectory.resolve("app.metadata.json"), "legacy");
    Files.writeString(metadataDirectory.resolve("app.metadata.json"), "metadata");

    try (var classLoader = new URLClassLoader(new URL[] {tempDir.toUri().toURL()}, null)) {
      var metadataFiles = new ClasspathMetadataFiles(classLoader);

      assertEquals(List.of("legacy", "metadata"),
          metadataFiles.legacyThenMetadataContentStreamForOverride("META-INF/ouroboros/app.metadata.json").toList());
      assertEquals(List.of("metadata"), metadataFiles.metadataContentStreamForOverride("app.metadata.json").toList());
    }
  }
}
