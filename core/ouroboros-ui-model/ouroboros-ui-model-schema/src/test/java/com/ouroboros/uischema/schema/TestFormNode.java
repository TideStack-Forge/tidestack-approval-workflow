package com.ouroboros.uischema.schema;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.Test;

import com.ouroboros.util.Json;

public class TestFormNode {
  @Test
  public void testFormNodeToJson() {
    var form = new FormNode();
    form.setStatic(true);
    var api = new ApiConfig();
    api.setMethod("get");
    api.setUrl("http://localhost:8080/api/v1/xxx");
    form.setApi("http://localhost:8080/api/v1/xxx");
    form.setInitApi(api);
    form.setProp("xxx", "xxxValue");
    var jsonString = Json.toPrettyJsonString(form);
    var form2 = Json.toBean(FormNode.class, jsonString);
    assertNotNull(form2);
  }

  @Test
  public void testPageNodeFromJson() {
    var pageNodeString = """
        {
          "type": "page",
          "id": "u:4519870b36b5",
          "asideResizor": false,
          "style": {
            "boxShadow": " 0px 0px 0px 0px transparent"
          },
          "pullRefresh": {
            "disabled": true
          },
          "body": [
            {
              "type": "crud",
              "syncLocation": false,
              "api": {
                "method": "get",
                "url": ""
              },
              "columns": [
                {
                  "name": "id",
                  "label": "ID",
                  "type": "text",
                  "id": "u:b97d38fdbd84"
                },
                {
                  "name": "engine",
                  "label": "渲染引擎",
                  "type": "text",
                  "id": "u:52d1bf017aa0"
                }
              ],
              "bulkActions": [
              ],
              "itemActions": [
              ],
              "id": "u:03b8e3adbaf1"
            }
          ]
        }
        """;
    var pageNode = Json.tryToBean(PageNode.class, pageNodeString);
    assertTrue(pageNode.isSuccess());
  }
}
