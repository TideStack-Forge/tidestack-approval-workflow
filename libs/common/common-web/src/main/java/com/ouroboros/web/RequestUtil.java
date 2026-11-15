package com.ouroboros.web;

import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

import io.vavr.Tuple;
import io.vavr.Tuple2;

import com.ouroboros.util.Converter;
import com.ouroboros.util.Json;
import com.ouroboros.util.Maps;

public class RequestUtil {
  final static String[] OMIT_WHERE_FIELDS = new String[]{"page", "perPage", "pageSize", "skip", "limit", "offset", "sort", "order"};
  final static int DEFAULT_LIMIT = 10;

  static public Tuple2<Integer, Integer> getSkipLimit(Map<String, Object> params) {
    var pagination = Maps.pick(params, "page", "perPage", "pageSize", "skip", "limit", "offset");
    var skip = pagination.getOrDefault("skip", pagination.get("offset"));
    var limit = pagination.getOrDefault("limit", pagination.getOrDefault("pageSize", pagination.get("perPage")));
    var page = pagination.getOrDefault("page", 1);
    if (limit == null) {
      limit = DEFAULT_LIMIT;
    }
    if (skip == null) {
      skip = (Converter.toInteger(page) - 1) * Converter.toInteger(limit);
    }
    return Tuple.of(Converter.toInteger(skip), Converter.toInteger(limit));
  }

  static public Map<String, String> getOrder(Map<String, Object> params) {
    var orderMap = Maps.pick(params, "sort", "order");
    var order = orderMap.getOrDefault("sort", orderMap.get("order"));
    if (order == null) {
      return Collections.emptyMap();
    }
    var orderStr = order.toString();
    if (orderStr.startsWith("{")) {
      return Json.toMap(orderStr).entrySet().stream()
          .map(e -> Tuple.of(e.getKey(), e.getValue().toString()))
          .collect(Collectors.toMap(Tuple2::_1, Tuple2::_2, (v1, v2) -> v2));
    }
    if (orderStr.contains(" ")) {
      var orderPair = orderStr.split(" ");
      return Collections.singletonMap(orderPair[0], orderPair[1]);
    }
    return Collections.singletonMap(orderStr, "asc");
  }

  static public Map<String, Object> getWhere(Map<String, Object> params) {
    var where = Maps.omit(params, OMIT_WHERE_FIELDS).entrySet().stream()
        .filter(e -> e.getValue() != null && !e.getValue().toString().isEmpty())
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (v1, v2) -> v2));
    return Maps.Hierarchical.build(where);
  }
}
