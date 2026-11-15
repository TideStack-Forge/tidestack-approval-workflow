package com.ouroboros.uischema.schema;

import java.util.Map;

public class CrudTableColumnNode extends TableColumnNode {
  public CrudTableColumnNode() {
    super();
  }

  public CrudTableColumnNode(SchemaNode node) {
    super(node);
  }

  public CrudTableColumnNode(Map<String, ?> props) {
    super(props);
  }

  public Boolean getSortable() {
    return getProp(Boolean.class, "sortable", null);
  }

  public void setSortable(Boolean sortable) {
    setProp("sortable", sortable);
  }

  public Object getSearchable() {
    return getProp("searchable");
  }

  public void setSearchable(Object searchable) {
    setProp("searchable", searchable);
  }

  public Object getFilterable() {
    return getProp("filterable");
  }

  public void setFilterable(Object filterable) {
    setProp("filterable", filterable);
  }

  public Object getQuickEdit() {
    return getProp("quickEdit");
  }

  public void setQuickEdit(Object quickEdit) {
    setProp("quickEdit", quickEdit);
  }
}
