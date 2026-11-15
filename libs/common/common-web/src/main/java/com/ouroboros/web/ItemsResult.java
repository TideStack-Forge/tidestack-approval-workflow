package com.ouroboros.web;

import java.util.Collection;
import java.util.Collections;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 列表结果
 *
 * @param <T>
 * @author songmingxu
 */
public class ItemsResult<T> {
  private final Collection<T> items;

  public ItemsResult(Collection<T> items) {
    this.items = items;
  }

  public ItemsResult(T item) {
    this(Collections.singletonList(item));
  }

  public static <T> ItemsResult<T> of(Collection<T> items) {
    return new ItemsResult<>(items);
  }

  public static <T> ItemsResult<T> of(T item) {
    return new ItemsResult<>(item);
  }

  public <T2> ItemsResult<T2> map(Function<T, T2> mapper) {
    return new ItemsResult<>(items.stream().map(mapper).collect(Collectors.toList()));
  }

  public WebApiResult<ItemsResult<T>> toWebApiResult() {
    return WebApiResult.ok(this);
  }

  public Collection<T> getItems() {
    return items;
  }
}
