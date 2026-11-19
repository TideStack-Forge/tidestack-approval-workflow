package com.ouroboros.fieldtype;

public class FieldTypeCompileException extends RuntimeException {
  private final String fieldName;
  private final FieldTypeDefinitionSource source;
  private final String sourceLocation;
  private final String phase;

  public FieldTypeCompileException(String message,
                                   String fieldName,
                                   FieldTypeDefinitionSource source,
                                   String sourceLocation,
                                   String phase) {
    super(message);
    this.fieldName = fieldName;
    this.source = source;
    this.sourceLocation = sourceLocation;
    this.phase = phase;
  }

  public FieldTypeCompileException(String message,
                                   Throwable cause,
                                   String fieldName,
                                   FieldTypeDefinitionSource source,
                                   String sourceLocation,
                                   String phase) {
    super(message, cause);
    this.fieldName = fieldName;
    this.source = source;
    this.sourceLocation = sourceLocation;
    this.phase = phase;
  }

  public String getFieldName() {
    return fieldName;
  }

  public FieldTypeDefinitionSource getSource() {
    return source;
  }

  public String getSourceLocation() {
    return sourceLocation;
  }

  public String getPhase() {
    return phase;
  }
}
