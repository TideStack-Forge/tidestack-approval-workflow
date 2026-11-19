package com.ouroboros.fieldtype;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import io.vavr.control.Try;

import com.ouroboros.metadata.resource.ClasspathMetadataSource;
import com.ouroboros.metadata.resource.MetadataResource;
import com.ouroboros.metadata.resource.TypedMetadataCatalog;
import com.ouroboros.metadata.resource.TypedMetadataSpec;
import com.ouroboros.util.Json;

public class JarFieldTypeDefinitionProvider implements FieldTypeDefinitionProvider {
  private static final String METADATA_FIELD_TYPE_SUFFIX = ".field-type.json";
  private static final String DEFAULT_FIELDS_MODULE_NAME = "ouroboros-field-type-default-types";

  private final FieldTypeDefinitionReader reader;
  private final AtomicReference<List<FieldTypeDefinition>> cachedDefinitions = new AtomicReference<List<FieldTypeDefinition>>();

  public JarFieldTypeDefinitionProvider(FieldTypeDefinitionReader reader) {
    this.reader = reader;
  }

  @Override
  public boolean supports(FieldTypeCatalogContext context) {
    return context != null;
  }

  @Override
  public List<FieldTypeDefinition> load(FieldTypeCatalogContext context) {
    List<FieldTypeDefinition> definitions = cachedDefinitions.get();
    if (definitions != null) {
      return definitions;
    }
    var spec = TypedMetadataSpec.<FieldTypeDefinition>builder(FieldTypeDefinition.class)
        .typedSuffix(METADATA_FIELD_TYPE_SUFFIX)
        .resourceNamePredicate(resourceName -> !resourceName.startsWith("dev/")
            && !resourceName.replace('\\', '/').contains("/" + ClasspathMetadataSource.METADATA_DIRECTORY + "dev/"))
        .resourceParser(resource -> Try.of(() -> readDefinition(resource)))
        .keyExtractor(FieldTypeDefinition::getName)
        .parseFailurePolicy(TypedMetadataSpec.ParseFailurePolicy.ERROR)
        .blankKeyPolicy(TypedMetadataSpec.BlankKeyPolicy.ERROR)
        .build();
    Map<String, FieldTypeDefinition> definitionsByName = new TypedMetadataCatalog(new ClasspathMetadataSource())
        .load(spec)
        .values();
    List<FieldTypeDefinition> loaded = Collections.unmodifiableList(new ArrayList<FieldTypeDefinition>(definitionsByName.values()));
    cachedDefinitions.compareAndSet(null, loaded);
    return cachedDefinitions.get();
  }

  private FieldTypeDefinition readDefinition(MetadataResource resource) {
    var sourceLocation = resource.resourceName();
    var source = resolveSource(sourceLocation);
    return reader.read(Json.toMap(resource.content()), source, sourceLocation)
        .fold(exception -> {
          throw new FieldTypeCatalogException(exception.getMessage() + " @ " + sourceLocation);
        }, definition -> definition);
  }

  private FieldTypeDefinitionSource resolveSource(String sourceLocation) {
    if (sourceLocation != null && sourceLocation.contains(DEFAULT_FIELDS_MODULE_NAME)) {
      return FieldTypeDefinitionSource.DEFAULT;
    }
    return FieldTypeDefinitionSource.JAR;
  }
}
