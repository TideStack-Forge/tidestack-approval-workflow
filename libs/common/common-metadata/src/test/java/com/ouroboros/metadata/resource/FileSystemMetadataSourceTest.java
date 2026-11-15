package com.ouroboros.metadata.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileSystemMetadataSourceTest {
  @TempDir
  Path tempDir;

  @Test
  void legacyThenTypedPlanLetsTypedFileOverrideLegacyFile() throws Exception {
    Files.writeString(tempDir.resolve("customer.json"), dictJson("customer", "legacy"));
    Files.writeString(tempDir.resolve("customer.dict.json"), dictJson("customer", "typed"));

    var spec = TypedMetadataSpec.<DictMetadata>builder(DictMetadata.class)
        .typedSuffix(".dict.json")
        .sourcePlan(TypedMetadataSpec.SourcePlan.LEGACY_DIRECTORY_THEN_TYPED_SUFFIX)
        .legacyFilePattern(Pattern.compile(".*\\.json"))
        .jsonParser(DictMetadata.class)
        .keyExtractor(DictMetadata::getName)
        .build();
    var result = new TypedMetadataCatalog(new FileSystemMetadataSource(tempDir)).load(spec);

    assertEquals("typed", result.values().get("customer").getTitle());
  }

  private static String dictJson(String name, String title) {
    return """
        {
          "name": "%s",
          "title": "%s"
        }
        """.formatted(name, title);
  }

  public static class DictMetadata {
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
