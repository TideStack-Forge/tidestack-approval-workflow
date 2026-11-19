package com.ouroboros.fieldtype;

import java.util.Map;

import io.vavr.control.Either;

public interface FieldTypeCompiler {
  Either<FieldTypeCompileException, CompiledFieldType> compile(FieldTypeDefinition definition,
                                                               Map<String, Object> params,
                                                               FieldTypeCatalogContext context);
}
