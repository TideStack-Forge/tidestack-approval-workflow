package com.ouroboros.fieldtype;

import java.util.List;

public interface FieldTypeDefinitionProvider {
  boolean supports(FieldTypeCatalogContext context);

  List<FieldTypeDefinition> load(FieldTypeCatalogContext context);
}
