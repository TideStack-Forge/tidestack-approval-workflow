package com.ouroboros.fieldtype;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.vavr.control.Either;

public interface FieldTypeCatalog {
  Optional<FieldTypeDefinition> getMetadata(FieldTypeCatalogContext context, String name);

  List<FieldTypeDefinition> listMetadata(FieldTypeCatalogContext context);

  Either<FieldTypeCompileException, CompiledFieldType> compile(FieldTypeCatalogContext context,
                                                               String name,
                                                               Map<String, Object> params);
}
