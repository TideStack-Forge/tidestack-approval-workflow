package com.ouroboros.uischema.wrapper;

import com.ouroboros.uischema.schema.SchemaNode;

/**
 * @author liansz
 */
public class SchemaNodeWrapper {

  private String name;

  private String title;

  private String description;

  private SchemaNode schema;

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

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public SchemaNode getSchema() {
    return schema;
  }

  public void setSchema(SchemaNode schema) {
    this.schema = schema;
  }
}
