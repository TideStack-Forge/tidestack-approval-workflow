package com.ouroboros.web;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.ouroboros.util.Converter;

/**
 * 分页结果
 *
 * @param <T>
 * @author songmingxu
 */
public class PagedResult<T> {
  List<T> items;
  Long total;

  public PagedResult(Long total, List<T> items) {
    this.total = total;
    this.items = items;
  }

  public PagedResult(Integer total, List<T> items) {
    this(Converter.toLong(total), items);
  }

  public static <T> PagedResult<T> of(Long total, List<T> items) {
    return new PagedResult<>(total, items);
  }

  public static <T> PagedResult<T> of(Integer total, List<T> items) {
    return new PagedResult<>(total, items);
  }

  public <T2> PagedResult<T2> map(Function<T, T2> mapper) {
    return new PagedResult<>(total, items.stream().map(mapper).collect(Collectors.toList()));
  }

  public WebApiResult<PagedResult<T>> toWebApiResult() {
    return WebApiResult.ok(this);
  }

  public List<T> getItems() {
    return items;
  }

  public Long getTotal() {
    return total;
  }
}
