package com.ouroboros.uischema.schema;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.ouroboros.util.Json;

public class TestNodeConversionCoverage {

  @Test
  public void shouldCoverWithBodyAndDeserializerPathsThroughJson() {
    String objectBodyJson = "{\"type\":\"page\",\"title\":\"P\",\"body\":{\"type\":\"input-text\",\"name\":\"a\"}}";
    PageNode pageFromObject = Json.toBean(PageNode.class, objectBodyJson);
    assertNotNull(pageFromObject.getBody());
    assertEquals(1, pageFromObject.getBody().size());
    assertEquals("input-text", pageFromObject.getBody().get(0).getType());

    String stringBodyJson = "{\"type\":\"page\",\"body\":\"plain text\"}";
    PageNode pageFromString = Json.toBean(PageNode.class, stringBodyJson);
    assertEquals("tpl", pageFromString.getBody().get(0).getType());

    String arrayBodyJson = "{\"type\":\"page\",\"body\":[{\"type\":\"input-text\",\"name\":\"b\"},\"tpl value\"]}";
    PageNode pageFromArray = Json.toBean(PageNode.class, arrayBodyJson);
    assertEquals(2, pageFromArray.getBody().size());
    assertTrue(pageFromArray.getBody().get(0) instanceof SchemaNode);

    PageNode page = new PageNode();
    page.setBody((Object) null);
    assertNull(page.getBody());
    page.setBody("body-string");
    assertEquals("tpl", page.getBody().get(0).getType());

    Map<String, Object> bodyMap = new LinkedHashMap<String, Object>();
    bodyMap.put("type", "input-text");
    bodyMap.put("name", "fromMap");
    page.setBody(bodyMap);
    assertEquals("fromMap", page.getBody().get(0).getName());

    List<Object> bodyList = new ArrayList<Object>();
    bodyList.add(bodyMap);
    bodyList.add(Integer.valueOf(3));
    page.setBody(bodyList);
    assertEquals(2, page.getBody().size());

    SchemaNode node = new SchemaNode();
    node.setType("tpl");
    page.addBody(node);
    assertTrue(page.getBody().size() >= 3);
  }

  @Test
  public void shouldCoverTableAndCrudColumnConversionBranches() {
    TableNode table = new TableNode();
    List<Object> columns = new ArrayList<Object>();

    TableColumnNode c1 = new TableColumnNode();
    c1.setLabel("L1");
    columns.add(c1);

    SchemaNode schemaColumn = new SchemaNode();
    schemaColumn.setType("text");
    schemaColumn.setName("name1");
    columns.add(schemaColumn);

    Map<String, Object> mapColumn = new HashMap<String, Object>();
    mapColumn.put("name", "name2");
    mapColumn.put("type", "text");
    columns.add(mapColumn);

    columns.add(Integer.valueOf(1));
    table.setColumns(columns);
    assertEquals(5, table.getColumns().size());
    assertEquals("ERROR!!! Columns的元素类型不正确", table.getColumns().get(4).getLabel());

    table.setColumns("not-collection");
    assertNotNull(table.getColumns());
    assertEquals(0, table.getColumns().size());

    table.addColumn(c1);
    assertEquals(1, table.getColumns().size());

    CRUDTableNode crud = new CRUDTableNode();
    crud.setColumns(Arrays.asList(new TableColumnNode(), new CrudTableColumnNode()));
    assertEquals(2, crud.getColumns().size());
    assertTrue(crud.getColumns().get(0) instanceof CrudTableColumnNode);

    List<Object> crudColumns = new ArrayList<Object>();
    crudColumns.add(new CrudTableColumnNode());
    crudColumns.add(new TableColumnNode());
    crudColumns.add(mapColumn);
    crudColumns.add("invalid");
    crud.setColumns(crudColumns);
    assertEquals(4, crud.getColumns().size());
    assertEquals("ERROR!!! Columns的元素类型不正确", crud.getColumns().get(3).getLabel());

    crud.setColumns(Integer.valueOf(7));
    assertNotNull(crud.getColumns());
    assertEquals(0, crud.getColumns().size());

    CrudTableColumnNode crudTableColumnNode = new CrudTableColumnNode();
    crud.addColumn(crudTableColumnNode);
    assertEquals(1, crud.getColumns().size());
  }

  @Test
  public void shouldCoverCardListAndColumnSpecificBranches() {
    Map<String, Object> copyableMap = new HashMap<String, Object>();
    copyableMap.put("icon", "copy");
    copyableMap.put("content", "copy this");

    TableColumnNode columnNode = new TableColumnNode();
    columnNode.setProp("copyable", copyableMap);
    Object copyable = columnNode.getCopyable();
    assertTrue(copyable instanceof TableColumnNode.Copyable);
    assertEquals("copy", ((TableColumnNode.Copyable) copyable).getIcon());
    assertNull(new TableColumnNode().getCopyable());

    FormNode form = new FormNode();
    form.setApi("post:/save");
    form.setInitAsyncApi("get:/job/1");
    form.setAsyncApi("get:/job/2");
    String formJson = Json.toPrettyJsonString(form);
    FormNode formFromJson = Json.toBean(FormNode.class, formJson);
    assertTrue(formFromJson.getApi() instanceof ApiConfig);
    assertTrue(formFromJson.getInitAsyncApi() instanceof ApiConfig);
    assertTrue(formFromJson.getAsyncApi() instanceof ApiConfig);

    CardNode cardNode = new CardNode();
    cardNode.setType("xxx");
    assertEquals("card", cardNode.getType());

    CardsNode cardsNode = new CardsNode();
    cardsNode.setItemClassName("c");
    cardsNode.setSelectable(true);
    cardsNode.setMultiple(false);
    cardsNode.setCheckOnItemClick(true);
    cardsNode.setCard(cardNode);
    assertEquals("c", cardsNode.getItemClassName());
    assertEquals(cardNode, cardsNode.getCard());

    CRUDCardsNode crudCardsNode = new CRUDCardsNode();
    CRUDListNode crudListNode = new CRUDListNode();
    assertEquals("crud", crudCardsNode.getType());
    assertEquals("cards", crudCardsNode.getMode());
    assertEquals("crud", crudListNode.getType());
    assertEquals("list", crudListNode.getMode());

    ListNode listNode = new ListNode();
    ListItemConfig listItemConfig = new ListItemConfig();
    listItemConfig.setTitle("title");
    listNode.setListItem(listItemConfig);
    assertEquals("title", listNode.getListItem().getTitle());

    PopOverConfig popOverConfig = new PopOverConfig();
    PopOverConfig.Offset offset = new PopOverConfig.Offset();
    offset.setTop(1);
    offset.setLeft(2);
    offset.setRight(3);
    offset.setBottom(4);
    popOverConfig.setOffset(offset);
    popOverConfig.setBody(new SchemaNode());
    assertEquals(Integer.valueOf(1), popOverConfig.getOffset().getTop());
    assertNotNull(popOverConfig.getBody());
  }
}
