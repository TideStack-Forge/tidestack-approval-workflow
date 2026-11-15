package com.ouroboros.uischema.schema;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Test;

import com.ouroboros.uischema.schema.decorator.WithMessages;
import com.ouroboros.uischema.wrapper.SchemaNodeWrapper;

public class TestLargePojoCoverage {

  @Test
  public void shouldCoverFormNodeAndWithCrudAndAutoRefresh() {
    CRUDTableNode crud = new CRUDTableNode();
    FormNode filter = new FormNode();
    filter.setTitle("Filter");

    crud.setLoadDataOnce(true);
    crud.setLoadDataOnceFetchOnFilter(true);
    crud.setFilter(filter);
    crud.setFilterTogglable(true);
    crud.setFilterDefaultVisible(true);
    crud.setStopAutoRefreshWhenModalIsOpen(true);
    crud.setSyncLocation(false);
    crud.setDraggable(true);
    crud.setResizable(true);
    crud.setItemDraggableOn("this.ok");
    crud.setSaveOrderApi("post:/save-order");
    crud.setQuickSaveApi(new ApiConfig("post:/quick-save"));
    crud.setQuickSaveItemApi("post:/quick-save-item");
    crud.setBulkActions(Collections.singletonList(new SchemaNode()));
    crud.setPrimaryField("id");
    crud.setPerPage(20);
    crud.setOrderBy("id");
    crud.setOrderDir("desc");
    Map<String, Object> defaultParams = new HashMap<String, Object>();
    defaultParams.put("x", 1);
    crud.setDefaultParams(defaultParams);
    crud.setPageField("page");
    crud.setPerPageField("perPage");
    crud.setPerPageAvailable(Arrays.asList(10, 20, 50));
    crud.setOrderField("order");
    crud.setHideQuickSaveBtn(false);
    crud.setAutoJumpToTopOnPagerChange(true);
    crud.setSyncResponse2Query(false);
    crud.setKeepItemSelectionOnPageChange(true);
    crud.setLabelTpl("${name}");
    crud.setHeaderToolbar(Arrays.asList((Object) "bulkActions"));
    crud.setFooterToolbar(Arrays.asList((Object) "pagination"));
    crud.setAlwaysShowPagination(true);
    crud.setAffixHeader(false);
    crud.setAutoGenerateFilter(true);
    crud.setResetPageAfterAjaxItemAction(true);

    crud.setInitFetch(true);
    crud.setInitFetchOn("this.start");
    crud.setInterval(30);
    crud.setSilentPolling(true);
    crud.setStopAutoRefreshWhen("this.stop");

    assertEquals(Boolean.TRUE, crud.getLoadDataOnce());
    assertNotNull(crud.getFilter());
    assertEquals("id", crud.getPrimaryField());
    assertEquals(Integer.valueOf(20), crud.getPerPage());
    assertEquals("${name}", crud.getLabelTpl());
    assertEquals(Boolean.TRUE, crud.getInitFetch());
    assertEquals("this.stop", crud.getStopAutoRefreshWhen());
  }

  @Test
  public void shouldCoverActionFormItemListItemAndPageNodes() {
    ActionNode action = new ActionNode();
    action.setActionType("dialog");
    action.setLevel("primary");
    action.setSize("md");
    action.setIcon("plus");
    action.setIconClassName("ic");
    action.setRightIcon("arrow");
    action.setRightIconClassName("ric");
    action.setActive(true);
    action.setActiveLevel("success");
    action.setActiveClassName("ac");
    action.setBlock(true);
    action.setConfirmText("sure?");
    action.setConfirmTitle("confirm");
    action.setReload("crud");
    action.setTooltip("tip");
    action.setDisabledTip("disabled");
    action.setTooltipPlacement("top");
    action.setClose(true);
    action.setRequired(Arrays.asList("f1", "f2"));
    assertEquals("dialog", action.getActionType());
    assertEquals(Boolean.TRUE, action.getBlock());
    assertEquals(2, action.getRequired().size());

    FormItemNode item = new FormItemNode();
    item.setRequired(true);
    item.setValue("v1");
    item.setDescription("d1");
    item.setInputClassName("ic");
    item.setLabelClassName("lc");
    Map<String, Object> validations = new LinkedHashMap<String, Object>();
    validations.put("isEmail", true);
    item.setValidations(validations);
    Map<String, String> errors = new LinkedHashMap<String, String>();
    errors.put("isEmail", "invalid");
    item.setValidationErrors(errors);
    item.setLabelAlign("left");
    item.setLabelRemark("remark");
    item.setInline(true);
    item.setSubmitOnChange(true);
    item.setDisabled(false);
    item.setDisabledOn("this.x");
    item.setRequiredOn("this.y");
    item.setValidateApi("/validate");
    FormItemNode.AutoFillConfig autoFill = new FormItemNode.AutoFillConfig();
    autoFill.setShowSuggestion(true);
    autoFill.setSilent(false);
    autoFill.setFillMapping(Collections.singletonMap("n", "name"));
    autoFill.setTrigger("focus");
    autoFill.setMode("dialog");
    autoFill.setLabelField("label");
    autoFill.setPosition("left-top");
    autoFill.setSize("lg");
    autoFill.setApi("get:/suggest");
    item.setAutoFill(autoFill);
    item.setStaticInputClassName("sic");
    item.setStaticLabelClassName("slc");
    item.setStaticSchema(new SchemaNode());
    assertEquals(Boolean.TRUE, item.getRequired());
    assertNotNull(item.getAutoFill());
    assertEquals("dialog", item.getAutoFill().getMode());

    ListItemConfig listItem = new ListItemConfig();
    listItem.setTitle("title");
    listItem.setTitleClassName("tc");
    listItem.setSubTitle("sub");
    listItem.setAvatar("a.png");
    listItem.setAvatarClassName("ac");
    listItem.setDesc("desc");
    listItem.setBody(Arrays.asList((Object) "b1", "b2"));
    listItem.setActionsPosition("right");
    listItem.setActions(Collections.singletonList(action));
    assertEquals("title", listItem.getTitle());
    assertEquals(2, listItem.getBody().size());
    assertEquals(1, listItem.getActions().size());

    PageNode page = new PageNode();
    page.setTitle("Page");
    page.setSubTitle("Sub");
    page.setAside(new SchemaNode());
    page.setAsideResizor(true);
    page.setAsideMinWidth(100);
    page.setAsideMaxWidth(300);
    page.setAsideSticky(false);
    page.setToolbar(Arrays.asList(new SchemaNode()));
    Map<String, Object> cssVars = new HashMap<String, Object>();
    cssVars.put("--x", "1");
    page.setCssVars(cssVars);
    PageNode.CssConfig css = new PageNode.CssConfig();
    css.setCss("root", Collections.singletonMap("color", "red"));
    page.setCss(css);
    page.setToolbarClassName("tb");
    page.setAsideClassName("as");
    PageNode.PullRefreshConfig pullRefresh = new PageNode.PullRefreshConfig();
    pullRefresh.setDisabled(true);
    pullRefresh.setPullingText("pull");
    pullRefresh.setLoosingText("loose");
    page.setPullRefresh(pullRefresh);
    assertEquals("Sub", page.getSubTitle());
    assertNotNull(page.getCssVars());
    assertNotNull(page.getCss());
    assertTrue(page.getPullRefresh().getDisabled());
  }

  @Test
  public void shouldCoverMessagesAndWrapper() {
    ApiConfig api = new ApiConfig();
    Map<String, String> msgs = new HashMap<String, String>();
    msgs.put("fetchSuccess", "ok");
    api.setMessages(msgs);
    WithMessages.Messages messages = api.getMessages();
    assertNotNull(messages);
    messages.setFetchFailed("ff");
    messages.setSaveFailed("sf");
    messages.setSaveSuccess("ss");
    messages.setFailed("f");
    messages.setSaveOrderFailed("sof");
    messages.setSaveOrderSuccess("sos");
    messages.setQuickSaveFailed("qf");
    messages.setQuickSaveSuccess("qs");
    assertEquals("ok", messages.getFetchSuccess());
    assertEquals("ff", messages.getFetchFailed());
    assertEquals("sf", messages.getSaveFailed());
    assertEquals("ss", messages.getSaveSuccess());
    assertEquals("f", messages.getFailed());
    assertEquals("sof", messages.getSaveOrderFailed());
    assertEquals("sos", messages.getSaveOrderSuccess());
    messages.getQuickSaveFailed();
    messages.getQuickSaveSuccess();

    SchemaNodeWrapper wrapper = new SchemaNodeWrapper();
    SchemaNode schema = new SchemaNode();
    wrapper.setName("n1");
    wrapper.setTitle("t1");
    wrapper.setSchema(schema);
    assertEquals("n1", wrapper.getName());
    assertEquals("t1", wrapper.getTitle());
    assertEquals(schema, wrapper.getSchema());
  }

  @Test
  public void shouldCoverRemainingTableActionAndPageMethods() {
    TableNode tableNode = new TableNode();
    tableNode.setTitle("table-title");
    tableNode.setSource("$items");
    tableNode.setHeaderClassName("h");
    tableNode.setFooterClassName("f");
    tableNode.setPlaceholder("empty");
    tableNode.setSelectable(true);
    tableNode.setMultiple(false);
    tableNode.setTableClassName("tc");
    tableNode.setToolbarClassName(new HashMap<String, Object>());
    tableNode.setCombineNum(2);
    tableNode.setItemActions(Collections.singletonList(new ActionNode()));
    tableNode.setItemCheckableOn("this.ok");
    tableNode.setCheckOnItemClick(true);
    tableNode.setRowClassName("row");
    tableNode.setRowClassNameExpr("rowExpr");
    tableNode.setPrefixRow(Collections.singletonList(new SchemaNode()));
    tableNode.setAffixRow(Collections.singletonList(new SchemaNode()));
    tableNode.setItemBadge(new SchemaNode());
    tableNode.setItemDraggableOn("itemDrag");
    tableNode.setAutoFillHeight("100%");
    tableNode.setResizable(true);
    assertEquals("table-title", tableNode.getTitle());
    assertEquals("$items", tableNode.getSource());
    assertEquals("h", tableNode.getHeaderClassName());
    assertEquals("f", tableNode.getFooterClassName());
    assertEquals("empty", tableNode.getPlaceholder());
    assertEquals(Boolean.TRUE, tableNode.getSelectable());
    assertEquals(Boolean.FALSE, tableNode.getMultiple());
    assertEquals(Integer.valueOf(2), tableNode.getCombineNum());
    assertEquals(1, tableNode.getItemActions().size());
    assertEquals("this.ok", tableNode.getItemCheckableOn());
    assertEquals(Boolean.TRUE, tableNode.getCheckOnItemClick());
    assertEquals("row", tableNode.getRowClassName());
    assertEquals("rowExpr", tableNode.getRowClassNameExpr());
    assertEquals(1, tableNode.getPrefixRow().size());
    assertEquals(1, tableNode.getAffixRow().size());
    assertNotNull(tableNode.getItemBadge());
    assertEquals("itemDrag", tableNode.getItemDraggableOn());
    assertEquals("100%", tableNode.getAutoFillHeight());
    assertEquals(Boolean.TRUE, tableNode.getResizable());

    ActionNode actionNode = new ActionNode();
    Map<String, Object> clazzMap = new HashMap<String, Object>();
    clazzMap.put("k", "v");
    actionNode.setIconClassName(clazzMap);
    actionNode.setRightIconClassName(clazzMap);
    actionNode.setActiveClassName(clazzMap);
    actionNode.setClose("drawer");
    assertTrue(actionNode.getIconClassName() instanceof Map);
    assertTrue(actionNode.getRightIconClassName() instanceof Map);
    assertTrue(actionNode.getActiveClassName() instanceof Map);
    assertEquals("drawer", actionNode.getClose());

    PageNode pageNode = new PageNode();
    Map<String, Object> toolbarClassMap = new HashMap<String, Object>();
    toolbarClassMap.put("color", "red");
    Map<String, Object> asideClassMap = new HashMap<String, Object>();
    asideClassMap.put("width", "100px");
    pageNode.setToolbarClassName(toolbarClassMap);
    pageNode.setAsideClassName(asideClassMap);
    pageNode.setHeaderClassName("ph");
    pageNode.setBodyClassName("pb");
    pageNode.setInitApi("get:/init");
    assertTrue(pageNode.getToolbarClassName() instanceof Map);
    assertTrue(pageNode.getAsideClassName() instanceof Map);
    assertEquals("ph", pageNode.getHeaderClassName());
    assertEquals("pb", pageNode.getBodyClassName());
    assertEquals("get:/init", pageNode.getInitApi());
  }
}
