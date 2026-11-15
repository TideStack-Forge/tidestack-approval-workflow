package com.ouroboros.uischema.util;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import com.ouroboros.uischema.schema.ApiConfig;
import com.ouroboros.uischema.schema.FormItemNode;
import com.ouroboros.uischema.schema.FormNode;

@SuppressWarnings("unused")
public final class UISchemaUtils {
  private UISchemaUtils() {
  }

  public static FormNode createFormNode(String title, String api) {
    FormNode formNode = new FormNode();
    formNode.setTitle(title);
    formNode.setApi(api);
    return formNode;
  }

  public static FormNode createFormNode(String title, ApiConfig api) {
    FormNode formNode = new FormNode();
    formNode.setTitle(title);
    formNode.setApi(api);
    return formNode;
  }

  public static FormItemNode createFormItemNode(String type, String name, String label) {
    FormItemNode formItemNode = new FormItemNode();
    formItemNode.setType(type);
    formItemNode.setName(name);
    formItemNode.setLabel(label);
    return formItemNode;
  }

  public static FormItemNode createFormItemNode(String type, String name, String label, String placeholder) {
    FormItemNode formItemNode = new FormItemNode();
    formItemNode.setType(type);
    formItemNode.setName(name);
    formItemNode.setLabel(label);
    formItemNode.setPlaceholder(placeholder);
    return formItemNode;
  }

  public static ApiConfig createApiConfig(String method, String url) {
    ApiConfig apiConfig = new ApiConfig();
    apiConfig.setMethod(method);
    apiConfig.setUrl(url);
    return apiConfig;
  }

  public static ApiConfig createApiConfig(String method, String url, String data) {
    ApiConfig apiConfig = new ApiConfig();
    apiConfig.setMethod(method);
    apiConfig.setUrl(url);
    apiConfig.setData(data);
    return apiConfig;
  }

  public static <T> List<T> groupByWindowSize(List<?> items, Integer windowSize, Function<List<?>, T> groupWrapper) {
    var groups = new ArrayList<T>();
    var groupCount = items.size() % windowSize == 0 ? items.size() / windowSize : items.size() / windowSize + 1;
    for (int i = 0; i < groupCount; i++) {
      var groupBody = new ArrayList<>();
      for (int j = 0; j < windowSize; j++) {
        var index = i * windowSize + j;
        if (index >= items.size()) {
          break;
        }
        groupBody.add(items.get(index));
      }
      var group = groupWrapper.apply(groupBody);
      groups.add(group);
    }
    return groups;
  }
}
