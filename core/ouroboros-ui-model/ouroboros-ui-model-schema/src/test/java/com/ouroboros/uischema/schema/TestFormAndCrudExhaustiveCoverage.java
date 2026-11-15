package com.ouroboros.uischema.schema;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Test;

public class TestFormAndCrudExhaustiveCoverage {

  @Test
  public void shouldCoverMostFormNodeProperties() {
    FormNode form = new FormNode();
    form.setMode("horizontal");
    FormNode.HorizontalConfig horizontalConfig = new FormNode.HorizontalConfig();
    horizontalConfig.setLeft(4);
    horizontalConfig.setRight(8);
    horizontalConfig.setOffset(2);
    horizontalConfig.setLeftFixed(true);
    horizontalConfig.setJustify(false);
    form.setHorizontal(horizontalConfig);
    form.setLabelAlign("left");
    form.setLabelWidth(120);
    form.setLabelWidth("30%");
    form.setSubmitText("提交");
    form.setWrapWithPanel(true);
    form.setPanelClassName("panel");

    FormNode.FormRule rule = new FormNode.FormRule();
    rule.setRule("isRequired");
    rule.setMessage("必填");
    rule.setName(Arrays.asList("fieldA"));
    form.setRules(Collections.singletonList(rule));

    form.setInitAsyncApi(new ApiConfig("get:/init-async"));
    form.setInitCheckInterval(10);
    form.setInitFinishedField("done");
    form.setAsyncApi(new ApiConfig("get:/async"));
    form.setCheckInterval(5);
    form.setFinishedField("finished");
    form.setPrimaryField("id");
    form.setSubmitOnChange(true);
    form.setSubmitOnInit(false);
    form.setResetAfterSubmit(true);
    form.setTarget("window");
    form.setRedirect("/home");
    form.setReload("crud");
    form.setAutoFocus(true);
    form.setCanAccessSuperData(true);
    form.setPersistData("session");
    form.setPersistDataKeys(Arrays.asList("k1", "k2"));
    form.setPreventEnterSubmit(true);
    form.setColumnCount(3);
    form.setInheritData(false);

    assertEquals("horizontal", form.getMode());
    assertNotNull(form.getHorizontal());
    assertEquals(Integer.valueOf(4), form.getHorizontal().getLeft());
    assertEquals("30%", form.getLabelWidth());
    assertEquals("提交", form.getSubmitText());
    assertTrue(form.getWrapWithPanel());
    assertEquals("panel", form.getPanelClassName());
    assertEquals(1, form.getRules().size());
    assertEquals("isRequired", form.getRules().get(0).getRule());
    assertTrue(form.getInitAsyncApi() instanceof ApiConfig);
    assertTrue(form.getAsyncApi() instanceof ApiConfig);
    assertEquals(Integer.valueOf(10), form.getInitCheckInterval());
    assertEquals(Integer.valueOf(5), form.getCheckInterval());
    assertEquals("id", form.getPrimaryField());
    assertEquals(Boolean.TRUE, form.getSubmitOnChange());
    assertEquals(Boolean.FALSE, form.getSubmitOnInit());
    assertEquals(Boolean.TRUE, form.getResetAfterSubmit());
    assertEquals("window", form.getTarget());
    assertEquals("/home", form.getRedirect());
    assertEquals("crud", form.getReload());
    assertEquals(Boolean.TRUE, form.getAutoFocus());
    assertEquals(Boolean.TRUE, form.getCanAccessSuperData());
    assertEquals("session", form.getPersistData());
    assertEquals(2, form.getPersistDataKeys().size());
    assertEquals(Boolean.TRUE, form.getPreventEnterSubmit());
    assertEquals(Integer.valueOf(3), form.getColumnCount());
    assertEquals(Boolean.FALSE, form.getInheritData());
  }

  @Test
  public void shouldCoverRemainingCrudProperties() {
    CRUDTableNode crud = new CRUDTableNode();
    crud.setApi("get:/list");
    crud.setTitle("列表");
    crud.setPlaceholder("none");

    crud.setLoadDataOnce(false);
    crud.setLoadDataOnceFetchOnFilter(false);
    crud.setFilter(new FormNode());
    crud.setFilterTogglable(false);
    crud.setFilterDefaultVisible(false);
    crud.setStopAutoRefreshWhenModalIsOpen(false);
    crud.setSyncLocation(true);
    crud.setDraggable(false);
    crud.setResizable(false);
    crud.setItemDraggableOn("false");
    crud.setSaveOrderApi(new ApiConfig("post:/save-order"));
    crud.setQuickSaveApi("post:/quick-save");
    crud.setQuickSaveItemApi(new ApiConfig("post:/quick-save-item"));
    crud.setBulkActions(Collections.singletonList(new SchemaNode()));
    crud.setPrimaryField("pk");
    crud.setPerPage(15);
    crud.setOrderBy("name");
    crud.setOrderDir("asc");
    Map<String, Object> defaults = new LinkedHashMap<String, Object>();
    defaults.put("status", 1);
    crud.setDefaultParams(defaults);
    crud.setPageField("current");
    crud.setPerPageField("size");
    crud.setPerPageAvailable(Arrays.asList(15, 30));
    crud.setOrderField("sort");
    crud.setHideQuickSaveBtn(true);
    crud.setAutoJumpToTopOnPagerChange(false);
    crud.setSyncResponse2Query(true);
    crud.setKeepItemSelectionOnPageChange(false);
    crud.setLabelTpl("${id}");
    crud.setHeaderToolbar(Arrays.asList((Object) "columns-toggler"));
    crud.setFooterToolbar(Arrays.asList((Object) "statistics"));
    crud.setAlwaysShowPagination(false);
    crud.setAffixHeader(true);
    crud.setAutoGenerateFilter(false);
    crud.setResetPageAfterAjaxItemAction(false);

    assertTrue(crud.getApi() instanceof String);
    assertEquals("列表", crud.getTitle());
    assertEquals("none", crud.getPlaceholder());
    assertEquals(Boolean.FALSE, crud.getLoadDataOnce());
    assertEquals(Boolean.FALSE, crud.getLoadDataOnceFetchOnFilter());
    assertNotNull(crud.getFilter());
    assertEquals(Boolean.FALSE, crud.getFilterTogglable());
    assertEquals(Boolean.FALSE, crud.getFilterDefaultVisible());
    assertEquals(Boolean.FALSE, crud.getStopAutoRefreshWhenModalIsOpen());
    assertEquals(Boolean.TRUE, crud.getSyncLocation());
    assertEquals(Boolean.FALSE, crud.getDraggable());
    assertEquals(Boolean.FALSE, crud.getResizable());
    assertEquals("false", crud.getItemDraggableOn());
    assertTrue(crud.getSaveOrderApi() instanceof ApiConfig);
    assertTrue(crud.getQuickSaveApi() instanceof String);
    assertTrue(crud.getQuickSaveItemApi() instanceof ApiConfig);
    assertEquals(1, crud.getBulkActions().size());
    assertEquals("pk", crud.getPrimaryField());
    assertEquals(Integer.valueOf(15), crud.getPerPage());
    assertEquals("name", crud.getOrderBy());
    assertEquals("asc", crud.getOrderDir());
    assertEquals(Integer.valueOf(1), crud.getDefaultParams().get("status"));
    assertEquals("current", crud.getPageField());
    assertEquals("size", crud.getPerPageField());
    assertEquals(2, crud.getPerPageAvailable().size());
    assertEquals("sort", crud.getOrderField());
    assertEquals(Boolean.TRUE, crud.getHideQuickSaveBtn());
    assertEquals(Boolean.FALSE, crud.getAutoJumpToTopOnPagerChange());
    assertEquals(Boolean.TRUE, crud.getSyncResponse2Query());
    assertEquals(Boolean.FALSE, crud.getKeepItemSelectionOnPageChange());
    assertEquals("${id}", crud.getLabelTpl());
    assertEquals(1, crud.getHeaderToolbar().size());
    assertEquals(1, crud.getFooterToolbar().size());
    assertEquals(Boolean.FALSE, crud.getAlwaysShowPagination());
    assertEquals(Boolean.TRUE, crud.getAffixHeader());
    assertEquals(Boolean.FALSE, crud.getAutoGenerateFilter());
    assertEquals(Boolean.FALSE, crud.getResetPageAfterAjaxItemAction());
  }
}
