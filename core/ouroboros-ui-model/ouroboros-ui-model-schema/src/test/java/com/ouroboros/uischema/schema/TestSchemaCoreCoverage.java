package com.ouroboros.uischema.schema;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.Test;

import com.ouroboros.uischema.schema.event.EventActionConfig;
import com.ouroboros.uischema.schema.event.EventActionsConfig;
import com.ouroboros.uischema.util.UISchemaUtils;
import com.ouroboros.util.Json;

public class TestSchemaCoreCoverage {

  @Test
  public void shouldCoverAbstractSchemaAndSchemaNodeBranches() {
    SchemaNode node = new SchemaNode();
    node.setType("input-text");
    node.setName("username");
    node.setId("id-1");
    node.setClassName("c1");
    node.setVisible(false);
    node.setHidden(true);
    node.setVisibleOn("a > 1");
    node.setHiddenOn("a < 1");

    Map<String, Object> moreProps = new HashMap<String, Object>();
    moreProps.put("x", 1);
    node.putProps(moreProps);

    assertEquals("input-text", node.getType());
    assertEquals("username", node.getName());
    assertEquals("id-1", node.getId());
    assertEquals(false, node.getVisible());
    assertEquals(true, node.getHidden());
    assertEquals("a > 1", node.getVisibleOn());
    assertEquals("a < 1", node.getHiddenOn());
    assertEquals(Integer.valueOf(1), node.getProp(Integer.class, "x", 0));
    assertEquals(Integer.valueOf(9), node.getProp(Integer.class, "missing", 9));
    Optional<String> nameOpt = node.getProp(String.class, "name");
    assertTrue(nameOpt.isPresent());
    assertFalse(node.getProp(Integer.class, "name").isPresent());

    EventActionConfig click = new EventActionConfig();
    click.setActionType("reload");
    node.addOnEvent("click", click);
    node.addOnEvent("click", Arrays.asList(click));
    EventActionsConfig actionsConfig = new EventActionsConfig();
    actionsConfig.addAction(click);
    node.addOnEvent("click", actionsConfig);
    assertNotNull(node.getOnEvent());
    assertTrue(node.getOnEvent().getOnEventMap().containsKey("click"));
    assertTrue(node.getOnEvent().getOnEventMap().get("click").getActions().size() >= 3);

    Map<String, Object> onEventMap = new LinkedHashMap<String, Object>();
    Map<String, Object> clickMap = new LinkedHashMap<String, Object>();
    List<Map<String, Object>> actionMaps = new ArrayList<Map<String, Object>>();
    Map<String, Object> action = new LinkedHashMap<String, Object>();
    action.put("actionType", "dialog");
    actionMaps.add(action);
    clickMap.put("actions", actionMaps);
    onEventMap.put("click", clickMap);
    node.setOnEvent(onEventMap);
    assertNotNull(node.getOnEvent());
    assertEquals(1, node.getOnEvent().getOnEventMap().get("click").getActions().size());

    node.setOnEvent("invalid");
    assertNull(node.getOnEvent());
  }

  @Test
  public void shouldCoverApiConfigAndEventHelpers() {
    ApiConfig api1 = new ApiConfig("post: /api/user");
    assertEquals("post", api1.getMethod());
    assertEquals("/api/user", api1.getUrl());

    ApiConfig api2 = new ApiConfig("/api/no-method");
    assertNull(api2.getMethod());
    assertEquals("/api/no-method", api2.getUrl());

    api1.setData("{\"a\":1}");
    Map<String, Object> dataMap = new HashMap<String, Object>();
    dataMap.put("k", "v");
    api1.setData(dataMap);
    api1.setDataType("json");
    api1.setQsOptions("a=1");
    Map<String, Object> qs = new HashMap<String, Object>();
    qs.put("arrayFormat", "indices");
    api1.setQsOptions(qs);
    Map<String, String> headers = new HashMap<String, String>();
    headers.put("h", "v");
    api1.setHeaders(headers);
    api1.setSendOn("this.a");
    api1.setCache(60);
    api1.setRequestAdaptor("return api;");
    api1.setAdaptor("return payload;");
    api1.setReplaceData(true);
    api1.setResponseType("json");
    api1.setAutoRefresh(false);
    Map<String, Object> responseData = new HashMap<String, Object>();
    responseData.put("ok", true);
    api1.setResponseData(responseData);
    api1.setTrackExpression("id");
    api1.setSuccessMessage("ok");
    api1.setFailedMessage("fail");
    assertEquals("ok", api1.getMessages().getSuccess());
    assertEquals("fail", api1.getMessages().getFailed());

    EventActionConfig actionConfig = new EventActionConfig();
    actionConfig.setActionType("ajax");
    actionConfig.setComponentId("c1");
    actionConfig.setComponentName("name");
    actionConfig.putArg("k", "v");
    Map<String, Object> args = new HashMap<String, Object>();
    args.put("x", 2);
    actionConfig.setArgs(args);
    Map<String, Object> data = new HashMap<String, Object>();
    data.put("name", "n1");
    actionConfig.setData(data);
    actionConfig.setDataMergeMode("override");
    actionConfig.setPreventDefault(true);
    actionConfig.setStopPropagation(false);
    actionConfig.setExpression("true");
    actionConfig.setOutputVar("out");
    assertEquals("ajax", actionConfig.getActionType());
    assertNull(actionConfig.getArgs().get("k"));
    assertEquals(Integer.valueOf(2), actionConfig.getArgs().get("x"));

    Map<String, Object> eventActionsMap = new HashMap<String, Object>();
    eventActionsMap.put("actions", Collections.singletonList(actionConfig.getProps()));
    EventActionsConfig eventActionsConfig = new EventActionsConfig(eventActionsMap);
    assertEquals(1, eventActionsConfig.getActions().size());
    eventActionsConfig.addAction(actionConfig);
    eventActionsConfig.addActions(Collections.singletonList(actionConfig));
    assertTrue(eventActionsConfig.getActions().size() >= 3);
  }

  @Test
  public void shouldCoverUtilsAndSimpleNodes() {
    FormNode formByString = UISchemaUtils.createFormNode("t1", "get:/x");
    assertEquals("t1", formByString.getTitle());
    assertEquals("get:/x", formByString.getApi());

    ApiConfig api = UISchemaUtils.createApiConfig("get", "/x", "{}");
    FormNode formByApi = UISchemaUtils.createFormNode("t2", api);
    assertEquals("t2", formByApi.getTitle());
    assertTrue(formByApi.getApi() instanceof ApiConfig);

    FormItemNode itemNode = UISchemaUtils.createFormItemNode("input-text", "f1", "F1", "ph");
    assertEquals("input-text", itemNode.getType());
    assertEquals("f1", itemNode.getName());
    assertEquals("F1", itemNode.getLabel());
    assertEquals("ph", itemNode.getPlaceholder());

    List<Integer> items = Arrays.asList(1, 2, 3, 4, 5);
    List<Integer> groups = UISchemaUtils.groupByWindowSize(items, Integer.valueOf(2), list -> Integer.valueOf(list.size()));
    assertEquals(Arrays.asList(2, 2, 1), groups);

    TplNode tplNode = new TplNode("hello");
    assertEquals("tpl", tplNode.getType());
    assertEquals("hello", tplNode.getProp("tpl"));

    RemarkNode remarkNode = new RemarkNode();
    remarkNode.setContent("c");
    remarkNode.setPlacement("top");
    remarkNode.setTrigger("hover");
    remarkNode.setIcon("i");
    remarkNode.setShape("circle");
    String remarkJson = Json.toPrettyJsonString(remarkNode);
    assertNotNull(Json.toBean(RemarkNode.class, remarkJson));
  }
}
