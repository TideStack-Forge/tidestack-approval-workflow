package com.ouroboros.web;

import java.util.Collection;
import java.util.Collections;
import java.util.function.Function;

import io.vavr.control.Either;
import io.vavr.control.Try;

public class ItemsWebApiResult<T> extends AbstractWebApiResult<ItemsResult<T>> {
  public ItemsWebApiResult() {
    this(Collections.emptyList());
  }

  public ItemsWebApiResult(T item) {
    this(Collections.singletonList(item));
  }

  public ItemsWebApiResult(Collection<T> items) {
    super(ItemsResult.of(items));
  }

  public ItemsWebApiResult(ItemsResult<T> data) {
    super(data);
  }

  public ItemsWebApiResult(Integer code, String message) {
    super(code, message);
  }

  public ItemsWebApiResult(Integer code, String message, Collection<T> items) {
    super(code, message, ItemsResult.of(items));
  }

  public ItemsWebApiResult(Integer code, String message, ItemsResult<T> items) {
    super(code, message, items);
  }

  public static <T> ItemsWebApiResult<T> ok() {
    return new ItemsWebApiResult<>();
  }

  public static <T> ItemsWebApiResult<T> ok(T item) {
    return new ItemsWebApiResult<>(item);
  }

  public static <T> ItemsWebApiResult<T> ok(Collection<T> items) {
    return new ItemsWebApiResult<>(items);
  }

  public static <T> ItemsWebApiResult<T> ok(ItemsResult<T> data) {
    return new ItemsWebApiResult<>(data);
  }

  public static <T> ItemsWebApiResult<T> success(Collection<T> items) {
    return new ItemsWebApiResult<>(items);
  }

  public static <T> ItemsWebApiResult<T> success(ItemsResult<T> data) {
    return new ItemsWebApiResult<>(data);
  }

  public static <T> ItemsWebApiResult<T> error(String message) {
    return new ItemsWebApiResult<>(-1, message);
  }

  public static <T> ItemsWebApiResult<T> error(Integer code, String message) {
    return new ItemsWebApiResult<>(code, message);
  }

  public static <T> ItemsWebApiResult<T> fail(String message) {
    return new ItemsWebApiResult<>(-1, message);
  }

  public static <T> ItemsWebApiResult<T> fail(Integer code, String message) {
    return new ItemsWebApiResult<>(code, message);
  }

  public static <T> ItemsWebApiResult<T> build(Integer code, String message, Collection<T> items) {
    return new ItemsWebApiResult<>(code, message, items);
  }

  public static <T> ItemsWebApiResult<T> build(Integer code, String message, ItemsResult<T> data) {
    return new ItemsWebApiResult<>(code, message, data);
  }

  public static <T> ItemsWebApiResult<T> of(Either<? extends Throwable, ? extends Collection<T>> result) {
    return result.fold(
        exceptionMapper.getExceptionMapper(ItemsWebApiResult::new),
        ItemsWebApiResult::new
    );
  }

  public static <T> ItemsWebApiResult<T> of(Integer code, Either<? extends Throwable, ? extends Collection<T>> result) {
    return result.fold(
        exceptionMapper.getExceptionMapper(ItemsWebApiResult::new, code),
        ItemsWebApiResult::new
    );
  }

  public static <T> ItemsWebApiResult<T> of(String message, Either<? extends Throwable, ? extends Collection<T>> result) {
    return result.fold(
        exceptionMapper.getExceptionMapper(ItemsWebApiResult::new, message),
        ItemsWebApiResult::new
    );
  }

  public static <T> ItemsWebApiResult<T> of(Integer code, String message, Either<? extends Throwable, ? extends Collection<T>> result) {
    return result.fold(
        e -> new ItemsWebApiResult<>(code, message),
        ItemsWebApiResult::new
    );
  }

  public static <T> ItemsWebApiResult<T> of(Try<? extends Collection<T>> result) {
    return result.fold(
        exceptionMapper.getExceptionMapper(ItemsWebApiResult::new),
        ItemsWebApiResult::new
    );
  }

  public static <T> ItemsWebApiResult<T> of(Integer code, Try<? extends Collection<T>> result) {
    return result.fold(
        exceptionMapper.getExceptionMapper(ItemsWebApiResult::new, code),
        ItemsWebApiResult::new
    );
  }

  public static <T> ItemsWebApiResult<T> of(String message, Try<? extends Collection<T>> result) {
    return result.fold(
        exceptionMapper.getExceptionMapper(ItemsWebApiResult::new, message),
        ItemsWebApiResult::new
    );
  }

  public static <T> ItemsWebApiResult<T> of(Integer code, String message, Try<? extends Collection<T>> result) {
    return result.fold(
        e -> new ItemsWebApiResult<>(code, message),
        ItemsWebApiResult::new
    );
  }

  public <T2> ItemsWebApiResult<T2> map(Function<T, T2> mapper) {
    if (getCode() != DEFAULT_ERROR_CODE) {
      return new ItemsWebApiResult<>(getCode(), getMsg());
    }
    return new ItemsWebApiResult<>(getData().map(mapper));
  }
}
