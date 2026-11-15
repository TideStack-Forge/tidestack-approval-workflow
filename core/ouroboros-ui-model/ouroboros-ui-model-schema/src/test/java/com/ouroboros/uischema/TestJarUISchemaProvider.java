package com.ouroboros.uischema;

import static org.junit.Assert.*;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Optional;

import org.junit.Test;

import com.ouroboros.metadata.resource.ClasspathMetadataSource;
import com.ouroboros.util.ResourceUtil;

public class TestJarUISchemaProvider {
  private static final String PROVIDER_CLASS_NAME = "com.ouroboros.uischema.JarUISchemaProvider";
  private static final String RESOURCE_UTIL_CLASS_NAME = "com.ouroboros.util.ResourceUtil";
  private static final String METADATA_RESOURCE_PACKAGE = "com.ouroboros.metadata.resource.";

  @Test
  public void partialDefinitionsAreUsableAndSameProviderRetriesAfterFailure() {
    var broken = new AtomicReference<>("{");
    var provider = new JarUISchemaProvider(spec -> List.of(
        new com.ouroboros.metadata.resource.MetadataResource("good.ui-schema.json", "{\"name\":\"/good\",\"schema\":{\"type\":\"page\"}}"),
        new com.ouroboros.metadata.resource.MetadataResource("broken.good.ui-schema.json", broken.get())));
    assertEquals(1, provider.getUISchemaMap().size());
    assertTrue(provider.getUISchema("/good").isPresent());
    broken.set("{\"name\":\"/recovered\",\"schema\":{\"type\":\"page\"}}");
    assertEquals(2, provider.getUISchemaMap().size());
  }

  @Test
  public void sourceFailureIsNonblockingAndSameProviderCanRecover() {
    var broken = new AtomicBoolean(true);
    var provider = new JarUISchemaProvider(spec -> {
      if (broken.get()) {
        throw new IllegalStateException("jar unavailable");
      }
      return List.of(new com.ouroboros.metadata.resource.MetadataResource("good.ui-schema.json", "{\"name\":\"/good\",\"schema\":{\"type\":\"page\"}}"));
    });
    assertTrue(provider.getUISchemaMap().isEmpty());
    broken.set(false);
    assertEquals(1, provider.getUISchemaMap().size());
  }

  @Test
  public void recreatedProviderKeepsSchemaAfterResourceRemoval() throws Exception {
    Path root = Files.createTempDirectory("jar-ui-schema-snapshot");
    Path file = root.resolve("META-INF/ouroboros/metadata/snapshot.ui-schema.json");
    Files.createDirectories(file.getParent());
    Files.writeString(file, "{\"name\":\"Snapshot\",\"schema\":{\"type\":\"page\",\"title\":\"Original\"}}");
    try (var classLoader = new ChildFirstProviderClassLoader(buildClassPath(root), getClass().getClassLoader())) {
      Class<?> providerClass = classLoader.loadClass(PROVIDER_CLASS_NAME);
      Object first = providerClass.getDeclaredConstructor().newInstance();
      Optional<?> original = (Optional<?>) providerClass.getMethod("getUISchema", String.class).invoke(first, "/snapshot");
      assertTrue(original.isPresent());
      Files.delete(file);

      Object second = providerClass.getDeclaredConstructor().newInstance();
      Optional<?> loaded = (Optional<?>) providerClass.getMethod("getUISchema", String.class).invoke(second, "/snapshot");

      assertTrue(loaded.isPresent());
      assertEquals("Original", loaded.get().getClass().getMethod("getProp", String.class).invoke(loaded.get(), "title"));
    }
  }

  @Test
  public void shouldPreferEarlierClasspathResourceWhenSchemaPathDuplicates() throws Exception {
    Path overrideRoot = Files.createTempDirectory("jar-ui-schema-override");
    Path schemaDir = overrideRoot.resolve("META-INF").resolve("ouroboros").resolve("ui-schema");
    Files.createDirectories(schemaDir);
    Files.write(schemaDir.resolve("override-order.json"), (
        "{\n"
            + "  \"type\": \"page\",\n"
            + "  \"title\": \"override\"\n"
            + "}\n"
    ).getBytes(StandardCharsets.UTF_8));

    try (URLClassLoader classLoader = new ChildFirstProviderClassLoader(buildClassPath(overrideRoot),
        TestJarUISchemaProvider.class.getClassLoader())) {
      Class<?> providerClass = classLoader.loadClass(PROVIDER_CLASS_NAME);
      Object provider = providerClass.getDeclaredConstructor().newInstance();

      Optional<?> schemaOptional = (Optional<?>) providerClass.getMethod("getUISchema", String.class)
          .invoke(provider, "/override-order");

      assertTrue(schemaOptional.isPresent());

      Object schema = schemaOptional.get();
      Object title = schema.getClass().getMethod("getProp", String.class).invoke(schema, "title");
      assertEquals("override", title);
    }
  }

  @Test
  public void shouldPreferMetadataDirectoryWhenSchemaPathDuplicates() throws Exception {
    Path overrideRoot = Files.createTempDirectory("jar-ui-schema-metadata-override");
    Path legacySchemaDir = overrideRoot.resolve("META-INF").resolve("ouroboros").resolve("ui-schema");
    Path metadataDir = overrideRoot.resolve("META-INF").resolve("ouroboros").resolve("metadata");
    Files.createDirectories(legacySchemaDir);
    Files.createDirectories(metadataDir);
    Files.write(legacySchemaDir.resolve("duplicate.json"), (
        "{\n"
            + "  \"type\": \"page\",\n"
            + "  \"title\": \"legacy\"\n"
            + "}\n"
    ).getBytes(StandardCharsets.UTF_8));
    Files.write(metadataDir.resolve("duplicate.ui-schema.json"), (
        "{\n"
            + "  \"name\": \"/duplicate\",\n"
            + "  \"title\": \"Duplicate\",\n"
            + "  \"description\": \"metadata wrapper\",\n"
            + "  \"schema\": {\n"
            + "    \"type\": \"page\",\n"
            + "    \"title\": \"metadata\"\n"
            + "  }\n"
            + "}\n"
    ).getBytes(StandardCharsets.UTF_8));

    try (URLClassLoader classLoader = new ChildFirstProviderClassLoader(buildClassPath(overrideRoot),
        TestJarUISchemaProvider.class.getClassLoader())) {
      Class<?> providerClass = classLoader.loadClass(PROVIDER_CLASS_NAME);
      Object provider = providerClass.getDeclaredConstructor().newInstance();

      Optional<?> schemaOptional = (Optional<?>) providerClass.getMethod("getUISchema", String.class)
          .invoke(provider, "/duplicate");

      assertTrue(schemaOptional.isPresent());

      Object schema = schemaOptional.get();
      Object title = schema.getClass().getMethod("getProp", String.class).invoke(schema, "title");
      assertEquals("metadata", title);
    }
  }

  @Test
  public void shouldLoadWrappedMetadataSchemaByName() throws Exception {
    Path overrideRoot = Files.createTempDirectory("jar-ui-schema-wrapper");
    Path metadataDir = overrideRoot.resolve("META-INF").resolve("ouroboros").resolve("metadata").resolve("nested");
    Files.createDirectories(metadataDir);
    Files.write(metadataDir.resolve("dashboard.ui-schema.json"), (
        "{\n"
            + "  \"name\": \"Admin.Home\",\n"
            + "  \"title\": \"Admin Home\",\n"
            + "  \"description\": \"Dashboard page\",\n"
            + "  \"schema\": {\n"
            + "    \"type\": \"page\",\n"
            + "    \"title\": \"dashboard\"\n"
            + "  }\n"
            + "}\n"
    ).getBytes(StandardCharsets.UTF_8));

    try (URLClassLoader classLoader = new ChildFirstProviderClassLoader(buildClassPath(overrideRoot),
        TestJarUISchemaProvider.class.getClassLoader())) {
      Class<?> providerClass = classLoader.loadClass(PROVIDER_CLASS_NAME);
      Object provider = providerClass.getDeclaredConstructor().newInstance();

      Optional<?> schemaOptional = (Optional<?>) providerClass.getMethod("getUISchema", String.class)
          .invoke(provider, "/admin/home");

      assertTrue(schemaOptional.isPresent());

      Object schema = schemaOptional.get();
      Object title = schema.getClass().getMethod("getProp", String.class).invoke(schema, "title");
      assertEquals("dashboard", title);
    }
  }

  @Test
  public void shouldTreatTypedSchemaInLegacyDirectoryAsWrappedSchema() throws Exception {
    Path overrideRoot = Files.createTempDirectory("jar-ui-schema-legacy-wrapper");
    Path legacySchemaDir = overrideRoot.resolve("META-INF").resolve("ouroboros").resolve("ui-schema").resolve("nested");
    Files.createDirectories(legacySchemaDir);
    Files.write(legacySchemaDir.resolve("filename-ignored.ui-schema.json"), (
        "{\n"
            + "  \"name\": \"Legacy.Wrapped\",\n"
            + "  \"schema\": {\n"
            + "    \"type\": \"page\",\n"
            + "    \"title\": \"wrapped legacy\"\n"
            + "  }\n"
            + "}\n"
    ).getBytes(StandardCharsets.UTF_8));

    try (URLClassLoader classLoader = new ChildFirstProviderClassLoader(buildClassPath(overrideRoot),
        TestJarUISchemaProvider.class.getClassLoader())) {
      Class<?> providerClass = classLoader.loadClass(PROVIDER_CLASS_NAME);
      Object provider = providerClass.getDeclaredConstructor().newInstance();

      Optional<?> schemaOptional = (Optional<?>) providerClass.getMethod("getUISchema", String.class)
          .invoke(provider, "/legacy/wrapped");
      Optional<?> filenameSchemaOptional = (Optional<?>) providerClass.getMethod("getUISchema", String.class)
          .invoke(provider, "/nested/filename-ignored");

      assertTrue(schemaOptional.isPresent());
      assertFalse(filenameSchemaOptional.isPresent());

      Object schema = schemaOptional.get();
      Object title = schema.getClass().getMethod("getProp", String.class).invoke(schema, "title");
      assertEquals("wrapped legacy", title);
    }
  }

  private URL[] buildClassPath(Path overrideRoot) throws Exception {
    ArrayList<URL> urls = new ArrayList<>();
    urls.add(overrideRoot.toUri().toURL());
    File testClassesDir = new File(TestJarUISchemaProvider.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    File classesDir = new File(testClassesDir.getParentFile(), "classes");
    urls.add(testClassesDir.toURI().toURL());
    urls.add(classesDir.toURI().toURL());
    urls.add(ResourceUtil.class.getProtectionDomain().getCodeSource().getLocation());
    urls.add(ClasspathMetadataSource.class.getProtectionDomain().getCodeSource().getLocation());
    return urls.toArray(new URL[0]);
  }

  private static class ChildFirstProviderClassLoader extends URLClassLoader {
    private ChildFirstProviderClassLoader(URL[] urls, ClassLoader parent) {
      super(urls, parent);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
      synchronized (getClassLoadingLock(name)) {
        if (PROVIDER_CLASS_NAME.equals(name) || name.startsWith(PROVIDER_CLASS_NAME + "$")
            || name.startsWith(METADATA_RESOURCE_PACKAGE) || RESOURCE_UTIL_CLASS_NAME.equals(name)) {
          Class<?> loadedClass = findLoadedClass(name);
          if (loadedClass == null) {
            loadedClass = findClass(name);
          }
          if (resolve) {
            resolveClass(loadedClass);
          }
          return loadedClass;
        }
        return super.loadClass(name, resolve);
      }
    }

    @Override
    public Enumeration<URL> getResources(String name) throws java.io.IOException {
      List<URL> resources = new ArrayList<>();
      resources.addAll(Collections.list(findResources(name)));
      resources.addAll(Collections.list(getParent().getResources(name)));
      return Collections.enumeration(resources);
    }
  }
}
