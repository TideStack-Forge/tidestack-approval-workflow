package com.ouroboros.metadata.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.vavr.control.Try;

class TypedMetadataCatalogTest {

  @Test
  void lastWinsKeepsLatestValueAndMarksPreviousTraceOverridden() {
    var catalog = new TypedMetadataCatalog(new FixedMetadataSource(List.of(
        resource("first.demo.json", "demo", "first"),
        resource("second.demo.json", "demo", "second")
    )));

    var result = catalog.load(demoSpec());

    assertEquals("second", result.values().get("demo").getTitle());
    assertEquals("second", result.values().get("DEMO").getTitle());
    assertEquals(MetadataSourceTrace.Status.OVERRIDDEN, result.traces().get(0).status());
    assertEquals(MetadataSourceTrace.Status.ACCEPTED, result.traces().get(1).status());
  }

  @Test
  void blankKeyWarnsAndSkipsByDefault() {
    var catalog = new TypedMetadataCatalog(new FixedMetadataSource(List.of(
        resource("blank.demo.json", "", "blank")
    )));

    var result = catalog.load(demoSpec());

    assertTrue(result.values().isEmpty());
    assertEquals(MetadataDiagnostic.Level.WARN, result.diagnostics().get(0).level());
    assertEquals(MetadataSourceTrace.Status.SKIPPED, result.traces().get(0).status());
  }

  @Test
  void parserChainFallsThroughToNextParser() {
    var catalog = new TypedMetadataCatalog(new FixedMetadataSource(List.of(
        resource("demo.json", "demo", "parsed")
    )));
    var spec = TypedMetadataSpec.<DemoMetadata>builder(DemoMetadata.class)
        .typedSuffix(".demo.json")
        .parser(content -> Try.failure(new IllegalArgumentException("not this shape")))
        .jsonParser(DemoMetadata.class)
        .keyExtractor(DemoMetadata::getName)
        .build();

    var result = catalog.load(spec);

    assertEquals("parsed", result.values().get("demo").getTitle());
  }

  @Test
  void resourceParserCanUseResourceName() {
    var catalog = new TypedMetadataCatalog(new FixedMetadataSource(List.of(
        new MetadataResource("resource-key.demo.json", "resource-title")
    )));
    var spec = TypedMetadataSpec.<DemoMetadata>builder(DemoMetadata.class)
        .typedSuffix(".demo.json")
        .resourceParser(resource -> Try.of(() -> {
          var metadata = new DemoMetadata();
          metadata.setName(resource.resourceName());
          metadata.setTitle(resource.content());
          return metadata;
        }))
        .keyExtractor(DemoMetadata::getName)
        .build();

    var result = catalog.load(spec);

    assertEquals("resource-title", result.values().get("resource-key.demo.json").getTitle());
  }

  @Test
  void resourceNamePredicateSkipsUnwantedResourcesBeforeParsing() {
    var catalog = new TypedMetadataCatalog(new FixedMetadataSource(List.of(
        resource("metadata/dev/accepted.demo.json", "accepted", "accepted"),
        resource("metadata/runtime/skipped.demo.json", "skipped", "skipped")
    )));
    var spec = TypedMetadataSpec.<DemoMetadata>builder(DemoMetadata.class)
        .typedSuffix(".demo.json")
        .jsonParser(DemoMetadata.class)
        .resourceNamePredicate(resourceName -> resourceName.contains("metadata/dev/"))
        .keyExtractor(DemoMetadata::getName)
        .build();

    var result = catalog.load(spec);

    assertEquals(1, result.values().size());
    assertEquals("accepted", result.values().get("accepted").getTitle());
  }

  @Test
  void failOnDuplicateCollectsAllDuplicatesBeforeThrowing() {
    var catalog = new TypedMetadataCatalog(new FixedMetadataSource(List.of(
        resource("first.demo.json", "demo", "first"),
        resource("second.demo.json", "demo", "second"),
        resource("third.demo.json", "demo", "third")
    )));
    var spec = TypedMetadataSpec.<DemoMetadata>builder(DemoMetadata.class)
        .typedSuffix(".demo.json")
        .jsonParser(DemoMetadata.class)
        .keyExtractor(DemoMetadata::getName)
        .mergeStrategy(TypedMetadataSpec.MergeStrategy.FAIL_ON_DUPLICATE)
        .build();

    var exception = assertThrows(IllegalStateException.class, () -> catalog.load(spec));

    assertTrue(exception.getMessage().contains("first.demo.json"));
    assertTrue(exception.getMessage().contains("second.demo.json"));
    assertTrue(exception.getMessage().contains("third.demo.json"));
  }

  @Test
  void parseFailurePolicyErrorThrowsAfterScanning() {
    var catalog = new TypedMetadataCatalog(new FixedMetadataSource(List.of(
        new MetadataResource("broken.demo.json", "not-json")
    )));
    var spec = TypedMetadataSpec.<DemoMetadata>builder(DemoMetadata.class)
        .typedSuffix(".demo.json")
        .jsonParser(DemoMetadata.class)
        .keyExtractor(DemoMetadata::getName)
        .parseFailurePolicy(TypedMetadataSpec.ParseFailurePolicy.ERROR)
        .build();

    var exception = assertThrows(IllegalStateException.class, () -> catalog.load(spec));

    assertTrue(exception.getMessage().contains("broken.demo.json"));
  }

  @Test
  void strictParserRetainsFieldDiagnosisAlongsideResourceLocation() {
    var catalog = new TypedMetadataCatalog(new FixedMetadataSource(List.of(new MetadataResource("purchase.demo.json", "{}"))));
    var spec = TypedMetadataSpec.<DemoMetadata>builder(DemoMetadata.class)
        .typedSuffix(".demo.json")
        .parser(content -> Try.failure(new IllegalArgumentException("字段 amount 的 params 必须为对象")))
        .keyExtractor(DemoMetadata::getName)
        .parseFailurePolicy(TypedMetadataSpec.ParseFailurePolicy.ERROR).build();
    var failure = assertThrows(IllegalStateException.class, () -> catalog.load(spec));
    assertTrue(failure.getMessage().contains("purchase.demo.json"));
    assertTrue(failure.getMessage().contains("字段 amount"));
  }

  private static TypedMetadataSpec<DemoMetadata> demoSpec() {
    return TypedMetadataSpec.<DemoMetadata>builder(DemoMetadata.class)
        .typedSuffix(".demo.json")
        .jsonParser(DemoMetadata.class)
        .keyExtractor(DemoMetadata::getName)
        .build();
  }

  private static MetadataResource resource(String resourceName, String name, String title) {
    return new MetadataResource(resourceName, """
        {
          "name": "%s",
          "title": "%s"
        }
        """.formatted(name, title));
  }

  private record FixedMetadataSource(List<MetadataResource> resources) implements MetadataSource {
    @Override
    public List<MetadataResource> findResources(TypedMetadataSpec<?> spec) {
      return resources;
    }
  }

  public static class DemoMetadata {
    private String name;
    private String title;

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getTitle() {
      return title;
    }

    public void setTitle(String title) {
      this.title = title;
    }
  }
}
