package com.ouroboros.uischema.schema;


import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.ouroboros.uischema.schema.decorator.*;

public class CRUDTableNode extends TableNode implements WithTitle, WithAPI, WithAutoRefresh,
    WithMessages, WithCRUD {
  public CRUDTableNode() {
    super();
    setType("crud");
    setMode("table");
  }

  public CRUDTableNode(SchemaNode node) {
    super(node);
  }

  public CRUDTableNode(Map<String, ?> props) {
    super(props);
  }

  @Override
  public List<CrudTableColumnNode> getColumns() {
    return getProp(List.class, "columns", null);
  }

  @JsonSetter
  public void setColumns(List<? extends TableColumnNode> columns) {
    var newColumns = columns.stream()
        .map(c -> c instanceof CrudTableColumnNode ? c : new CrudTableColumnNode(c))
        .collect(Collectors.toList());
    setProp("columns", newColumns);
  }

  @Override
  public void setColumns(Object columns) {
    if (columns == null) {
      return;
    }
    if (columns instanceof Collection columnsColl) {
      var newColumns = new ArrayList<CrudTableColumnNode>();
      for (var column : columnsColl) {
        if (column instanceof CrudTableColumnNode crudTableColumn) {
          newColumns.add(crudTableColumn);
        } else if (column instanceof TableColumnNode tableColumn) {
          newColumns.add(new CrudTableColumnNode(tableColumn));
        } else if (column instanceof Map columnMap) {
          newColumns.add(new CrudTableColumnNode(columnMap));
        } else {
          var errorColumn = new CrudTableColumnNode();
          errorColumn.setLabel("ERROR!!! Columns的元素类型不正确");
          newColumns.add(errorColumn);
        }
      }
      setProp("columns", newColumns);
    } else {
      var errorColumn = new CrudTableColumnNode();
      errorColumn.setLabel("ERROR!!! Columns的类型不正确");
      var newColumns = new ArrayList<CrudTableColumnNode>();
      setProp("columns", newColumns);
    }
  }

  public void addColumn(CrudTableColumnNode column) {
    var columns = getColumns();
    if (columns == null) {
      columns = new ArrayList<CrudTableColumnNode>();
      setColumns(columns);
    }
    columns.add(column);
  }
}
