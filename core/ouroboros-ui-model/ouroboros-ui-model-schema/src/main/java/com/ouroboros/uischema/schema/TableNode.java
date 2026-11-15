package com.ouroboros.uischema.schema;


import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.ouroboros.uischema.schema.decorator.*;

@SuppressWarnings("unused")
public class TableNode extends SchemaNode implements WithTitle, WithSourceExpr, WithHeaderClassName,
    WithFooterClassName, WithPlaceholder, WithItemAction {
  public TableNode() {
    super();
    setType("table");
  }

  public TableNode(SchemaNode node) {
    super(node);
    setColumns(node.getProp("columns"));
  }

  public TableNode(Map<String, ?> props) {
    super(props);
    setColumns(props.get("columns"));
  }

  @SuppressWarnings("unchecked")
  public List<? extends TableColumnNode> getColumns() {
    return getProp(List.class, "columns", null);
  }

  @JsonSetter
  public void setColumns(Collection<? extends TableColumnNode> columns) {
    setProp("columns", columns);
  }

  public void setColumns(Object columns) {
    if (columns == null) {
      return;
    }
    if (columns instanceof Collection columnsColl) {
      var newColumns = new ArrayList<TableColumnNode>();
      for (var column : columnsColl) {
        if (column instanceof TableColumnNode tableColumn) {
          newColumns.add(tableColumn);
        }
        if (column instanceof SchemaNode schemaNode) {
          newColumns.add(new TableColumnNode(schemaNode));
        } else if (column instanceof Map columnMap) {
          newColumns.add(new TableColumnNode(columnMap));
        } else {
          var errorColumn = new TableColumnNode();
          errorColumn.setLabel("ERROR!!! Columns的元素类型不正确");
          newColumns.add(errorColumn);
        }
      }
      setProp("columns", newColumns);
    } else {
      var errorColumn = new TableColumnNode();
      errorColumn.setLabel("ERROR!!! Columns的类型不正确");
      var newColumns = new ArrayList<CrudTableColumnNode>();
      setProp("columns", newColumns);
    }
  }

  public void addColumn(TableColumnNode column) {
    var columns = getColumns();
    if (columns == null) {
      columns = new ArrayList<>();
      setColumns(columns);
    }
    ((List<TableColumnNode>) columns).add(column);
  }

  public Boolean getSelectable() {
    return getProp(Boolean.class, "selectable", null);
  }

  public void setSelectable(Boolean selectable) {
    setProp("selectable", selectable);
  }

  public Boolean getMultiple() {
    return getProp(Boolean.class, "multiple", null);
  }

  public void setMultiple(Boolean multiple) {
    setProp("multiple", multiple);
  }

  public Object getTableClassName() {
    return getProp("tableClassName");
  }

  public void setTableClassName(String tableClassName) {
    setProp("tableClassName", tableClassName);
  }

  public void setTableClassName(Map<String, Object> tableClassName) {
    setProp("tableClassName", tableClassName);
  }

  public Object getToolbarClassName() {
    return getProp("toolbarClassName");
  }

  public void setToolbarClassName(String toolbarClassName) {
    setProp("toolbarClassName", toolbarClassName);
  }

  public void setToolbarClassName(Map<String, Object> toolbarClassName) {
    setProp("toolbarClassName", toolbarClassName);
  }

  public Integer getCombineNum() {
    return getProp(Integer.class, "combineNum", null);
  }

  public void setCombineNum(Integer combineNum) {
    setProp("combineNum", combineNum);
  }

  @SuppressWarnings("unchecked")
  public List<ActionNode> getItemActions() {
    return getProp(List.class, "itemActions", null);
  }

  @JsonSetter
  public void setItemActions(List<ActionNode> itemActions) {
    setProp("itemActions", itemActions);
  }

  public String getItemCheckableOn() {
    return getProp(String.class, "itemCheckableOn", null);
  }

  public void setItemCheckableOn(String itemCheckableOn) {
    setProp("itemCheckableOn", itemCheckableOn);
  }

  public Boolean getCheckOnItemClick() {
    return getProp(Boolean.class, "checkOnItemClick", null);
  }

  public void setCheckOnItemClick(Boolean checkOnItemClick) {
    setProp("checkOnItemClick", checkOnItemClick);
  }

  public String getRowClassName() {
    return getProp(String.class, "rowClassName", null);
  }

  public void setRowClassName(String rowClassName) {
    setProp("rowClassName", rowClassName);
  }

  public String getRowClassNameExpr() {
    return getProp(String.class, "rowClassNameExpr", null);
  }

  public void setRowClassNameExpr(String rowClassNameExpr) {
    setProp("rowClassNameExpr", rowClassNameExpr);
  }

  @SuppressWarnings("unchecked")
  public List<SchemaNode> getPrefixRow() {
    return getProp(List.class, "prefixRow", null);
  }

  @JsonSetter
  public void setPrefixRow(List<SchemaNode> prefixRow) {
    setProp("prefixRow", prefixRow);
  }

  @SuppressWarnings("unchecked")
  public List<SchemaNode> getAffixRow() {
    return getProp(List.class, "affixRow", null);
  }

  @JsonSetter
  public void setAffixRow(List<SchemaNode> affixRow) {
    setProp("affixRow", affixRow);
  }

  public SchemaNode getItemBadge() {
    return getProp(SchemaNode.class, "itemBadge", null);
  }

  @JsonSetter
  public void setItemBadge(SchemaNode itemBadge) {
    setProp("itemBadge", itemBadge);
  }

  public String getItemDraggableOn() {
    return getProp(String.class, "itemDraggableOn", null);
  }

  public void setItemDraggableOn(String itemDraggableOn) {
    setProp("itemDraggableOn", itemDraggableOn);
  }

  public Object getAutoFillHeight() {
    return getProp("autoFillHeight");
  }

  public void setAutoFillHeight(Object autoFillHeight) {
    setProp("autoFillHeight", autoFillHeight);
  }

  public Boolean getResizable() {
    return getProp(Boolean.class, "resizable", null);
  }

  public void setResizable(Boolean resizable) {
    setProp("resizable", resizable);
  }
}
