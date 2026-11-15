package com.ouroboros.metadata.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ClasspathMetadataSourceTest {
  @TempDir
  Path tempDir;

  @Test
  void injectedClassLoaderFindsTypedMetadataResources() throws Exception {
    Path metadataDirectory = Files.createDirectories(tempDir.resolve(ClasspathMetadataSource.METADATA_DIRECTORY));
    Files.writeString(metadataDirectory.resolve("sample.demo.json"), "demo-content");

    try (var classLoader = new URLClassLoader(new URL[] {tempDir.toUri().toURL()}, null)) {
      var source = new ClasspathMetadataSource(classLoader);
      var resources = source.findTypedResources(".demo.json");

      assertEquals(1, resources.size());
      assertEquals("sample.demo.json", resources.get(0).resourceName());
      assertEquals("demo-content", resources.get(0).content());
      assertEquals(resources, source.findTypedResourcesInClasspathOrder(".demo.json"));
    }
  }
}
