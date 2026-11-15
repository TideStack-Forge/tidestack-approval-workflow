package com.ouroboros.uischema.schema.decorator;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.ouroboros.uischema.schema.ApiConfig;
import com.ouroboros.uischema.schema.FormNode;
import com.ouroboros.uischema.schema.Schema;
import com.ouroboros.uischema.schema.SchemaNode;

public interface WithCRUD extends Schema {

  default String getMode() {
    return getProp(String.class, "mode", null);
  }

  default void setMode(String mode) {
    setProp("mode", mode);
  }

  default Boolean getLoadDataOnce() {
    return getProp(Boolean.class, "loadDataOnce", null);
  }

  default void setLoadDataOnce(Boolean loadDataOnce) {
    setProp("loadDataOnce", loadDataOnce);
  }

  default Boolean getLoadDataOnceFetchOnFilter() {
    return getProp(Boolean.class, "loadDataOnceFetchOnFilter", null);
  }

  default void setLoadDataOnceFetchOnFilter(Boolean loadDataOnceFetchOnFilter) {
    setProp("loadDataOnceFetchOnFilter", loadDataOnceFetchOnFilter);
  }

  default FormNode getFilter() {
    return getProp(FormNode.class, "filter", null);
  }

  default void setFilter(Object filter) {
    setProp("filter", filter);
  }

  default Boolean getFilterTogglable() {
    return getProp(Boolean.class, "filterTogglable", null);
  }

  default void setFilterTogglable(Boolean filterTogglable) {
    setProp("filterTogglable", filterTogglable);
  }

  default Boolean getFilterDefaultVisible() {
    return getProp(Boolean.class, "filterDefaultVisible", null);
  }

  default void setFilterDefaultVisible(Boolean filterDefaultVisible) {
    setProp("filterDefaultVisible", filterDefaultVisible);
  }

  default Boolean getStopAutoRefreshWhenModalIsOpen() {
    return getProp(Boolean.class, "stopAutoRefreshWhenModalIsOpen", null);
  }

  default void setStopAutoRefreshWhenModalIsOpen(Boolean stopAutoRefreshWhenModalIsOpen) {
    setProp("stopAutoRefreshWhenModalIsOpen", stopAutoRefreshWhenModalIsOpen);
  }

  default Boolean getSyncLocation() {
    return getProp(Boolean.class, "syncLocation", null);
  }

  default void setSyncLocation(Boolean syncLocation) {
    setProp("syncLocation", syncLocation);
  }

  default Boolean getDraggable() {
    return getProp(Boolean.class, "draggable", null);
  }

  default void setDraggable(Boolean draggable) {
    setProp("draggable", draggable);
  }

  default Boolean getResizable() {
    return getProp(Boolean.class, "resizable", null);
  }

  default void setResizable(Boolean resizable) {
    setProp("resizable", resizable);
  }

  default String getItemDraggableOn() {
    return getProp(String.class, "itemDraggableOn", null);
  }

  default void setItemDraggableOn(String itemDraggableOn) {
    setProp("itemDraggableOn", itemDraggableOn);
  }

  default Object getSaveOrderApi() {
    return getProp(Object.class, "saveOrderApi", null);
  }

  @JsonSetter
  @JsonDeserialize(using = APIDeserializer.class)
  default void setSaveOrderApi(Object saveOrderApi) {
    setProp("saveOrderApi", saveOrderApi);
  }

  default void setSaveOrderApi(String saveOrderApi) {
    setProp("saveOrderApi", saveOrderApi);
  }

  default void setSaveOrderApi(ApiConfig saveOrderApi) {
    setProp("saveOrderApi", saveOrderApi);
  }

  default Object getQuickSaveApi() {
    return getProp(Object.class, "quickSaveApi", null);
  }

  @JsonSetter
  @JsonDeserialize(using = APIDeserializer.class)
  default void setQuickSaveApi(Object quickSaveApi) {
    setProp("quickSaveApi", quickSaveApi);
  }

  default void setQuickSaveApi(String quickSaveApi) {
    setProp("quickSaveApi", quickSaveApi);
  }

  default void setQuickSaveApi(ApiConfig quickSaveApi) {
    setProp("quickSaveApi", quickSaveApi);
  }

  default Object getQuickSaveItemApi() {
    return getProp(Object.class, "quickSaveItemApi", null);
  }

  @JsonSetter
  @JsonDeserialize(using = APIDeserializer.class)
  default void setQuickSaveItemApi(Object quickSaveItemApi) {
    setProp("quickSaveItemApi", quickSaveItemApi);
  }

  default void setQuickSaveItemApi(String quickSaveItemApi) {
    setProp("quickSaveItemApi", quickSaveItemApi);
  }

  default void setQuickSaveItemApi(ApiConfig quickSaveItemApi) {
    setProp("quickSaveItemApi", quickSaveItemApi);
  }

  default List<SchemaNode> getBulkActions() {
    return getProp(List.class, "bulkActions", null);
  }

  @JsonSetter
  default void setBulkActions(List<SchemaNode> bulkActions) {
    setProp("bulkActions", bulkActions);
  }

  default String getPrimaryField() {
    return getProp(String.class, "primaryField", null);
  }

  default void setPrimaryField(String primaryField) {
    setProp("primaryField", primaryField);
  }

  default Integer getPerPage() {
    return getProp(Integer.class, "perPage", null);
  }

  default void setPerPage(Integer perPage) {
    setProp("perPage", perPage);
  }

  default String getOrderBy() {
    return getProp(String.class, "orderBy", null);
  }

  default void setOrderBy(String orderBy) {
    setProp("orderBy", orderBy);
  }

  default String getOrderDir() {
    return getProp(String.class, "orderDir", null);
  }

  default void setOrderDir(String orderDir) {
    setProp("orderDir", orderDir);
  }

  default Map<String, Object> getDefaultParams() {
    return getProp(Map.class, "defaultParams", null);
  }

  default void setDefaultParams(Map<String, ?> defaultParams) {
    setProp("defaultParams", defaultParams);
  }

  default String getPageField() {
    return getProp(String.class, "pageField", null);
  }

  default void setPageField(String pageField) {
    setProp("pageField", pageField);
  }

  default String getPerPageField() {
    return getProp(String.class, "perPageField", null);
  }

  default void setPerPageField(String perPageField) {
    setProp("perPageField", perPageField);
  }

  default List<Integer> getPerPageAvailable() {
    return getProp(List.class, "perPageAvailable", null);
  }

  default void setPerPageAvailable(List<Integer> perPageAvailable) {
    setProp("perPageAvailable", perPageAvailable);
  }

  default String getOrderField() {
    return getProp(String.class, "orderField", null);
  }

  default void setOrderField(String orderField) {
    setProp("orderField", orderField);
  }

  default Boolean getHideQuickSaveBtn() {
    return getProp(Boolean.class, "hideQuickSaveBtn", null);
  }

  default void setHideQuickSaveBtn(Boolean hideQuickSaveBtn) {
    setProp("hideQuickSaveBtn", hideQuickSaveBtn);
  }

  default Boolean getAutoJumpToTopOnPagerChange() {
    return getProp(Boolean.class, "autoJumpToTopOnPagerChange", null);
  }

  default void setAutoJumpToTopOnPagerChange(Boolean autoJumpToTopOnPagerChange) {
    setProp("autoJumpToTopOnPagerChange", autoJumpToTopOnPagerChange);
  }

  default Boolean getSyncResponse2Query() {
    return getProp(Boolean.class, "syncResponse2Query", null);
  }

  default void setSyncResponse2Query(Boolean syncResponse2Query) {
    setProp("syncResponse2Query", syncResponse2Query);
  }

  default Boolean getKeepItemSelectionOnPageChange() {
    return getProp(Boolean.class, "keepItemSelectionOnPageChange", null);
  }

  default void setKeepItemSelectionOnPageChange(Boolean keepItemSelectionOnPageChange) {
    setProp("keepItemSelectionOnPageChange", keepItemSelectionOnPageChange);
  }

  default String getLabelTpl() {
    return getProp(String.class, "labelTpl", null);
  }

  default void setLabelTpl(String labelTpl) {
    setProp("labelTpl", labelTpl);
  }

  default List<Object> getHeaderToolbar() {
    return getProp(List.class, "headerToolbar", null);
  }

  default void setHeaderToolbar(List<Object> headerToolbar) {
    setProp("headerToolbar", headerToolbar);
  }

  default List<Object> getFooterToolbar() {
    return getProp(List.class, "footerToolbar", null);
  }

  default void setFooterToolbar(List<Object> footerToolbar) {
    setProp("footerToolbar", footerToolbar);
  }

  default Boolean getAlwaysShowPagination() {
    return getProp(Boolean.class, "alwaysShowPagination", null);
  }

  default void setAlwaysShowPagination(Boolean alwaysShowPagination) {
    setProp("alwaysShowPagination", alwaysShowPagination);
  }

  default Boolean getAffixHeader() {
    return getProp(Boolean.class, "affixHeader", null);
  }

  default void setAffixHeader(Boolean affixHeader) {
    setProp("affixHeader", affixHeader);
  }

  default Boolean getAutoGenerateFilter() {
    return getProp(Boolean.class, "autoGenerateFilter", null);
  }

  default void setAutoGenerateFilter(Boolean autoGenerateFilter) {
    setProp("autoGenerateFilter", autoGenerateFilter);
  }

  default Boolean getResetPageAfterAjaxItemAction() {
    return getProp(Boolean.class, "resetPageAfterAjaxItemAction", null);
  }

  default void setResetPageAfterAjaxItemAction(Boolean resetPageAfterAjaxItemAction) {
    setProp("resetPageAfterAjaxItemAction", resetPageAfterAjaxItemAction);
  }
}
