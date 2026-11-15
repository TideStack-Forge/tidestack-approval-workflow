package com.ouroboros.web;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import io.vavr.control.Either;
import io.vavr.control.Try;

public class PagedWebApiResult<T> extends AbstractWebApiResult<PagedResult<T>> {
  public PagedWebApiResult(Integer code, String msg, PagedResult<T> data) {
    super(code, msg, data);
  }

  public PagedWebApiResult(Integer code, String msg) {
    super(code, msg);
  }

  public PagedWebApiResult(PagedResult<T> data) {
    super(data);
  }

  public PagedWebApiResult() {
    this(0, Collections.emptyList());
  }

  public PagedWebApiResult(Long total, List<T> items) {
    super(PagedResult.of(total, items));
  }

  public PagedWebApiResult(Integer total, List<T> items) {
    super(PagedResult.of(total, items));
  }

  public static <T> PagedWebApiResult<T> ok() {
    return new PagedWebApiResult<>();
  }

  public static <T> PagedWebApiResult<T> ok(PagedResult<T> data) {
    return new PagedWebApiResult<>(data);
  }

  public static <T> PagedWebApiResult<T> ok(Long total, List<T> items) {
    return new PagedWebApiResult<>(total, items);
  }

  public static <T> PagedWebApiResult<T> ok(Integer total, List<T> items) {
    return new PagedWebApiResult<>(total, items);
  }

  public static <T> PagedWebApiResult<T> success(PagedResult<T> data) {
    return new PagedWebApiResult<>(data);
  }

  public static <T> PagedWebApiResult<T> success(Long total, List<T> items) {
    return new PagedWebApiResult<>(total, items);
  }

  public static <T> PagedWebApiResult<T> success(Integer total, List<T> items) {
    return new PagedWebApiResult<>(total, items);
  }

  public static <T> PagedWebApiResult<T> error(String message) {
    return new PagedWebApiResult<>(-1, message);
  }

  public static <T> PagedWebApiResult<T> error(Integer code, String message) {
    return new PagedWebApiResult<>(code, message);
  }

  public static <T> PagedWebApiResult<T> fail(String message) {
    return new PagedWebApiResult<>(-1, message);
  }

  public static <T> PagedWebApiResult<T> fail(Integer code, String message) {
    return new PagedWebApiResult<>(code, message);
  }

  public static <T> PagedWebApiResult<T> build(Integer code, String message, PagedResult<T> data) {
    return new PagedWebApiResult<>(code, message, data);
  }

  public static <T> PagedWebApiResult<T> of(Either<? extends Throwable, PagedResult<T>> result) {
    return result.fold(exceptionMapper.getExceptionMapper(PagedWebApiResult::new), PagedWebApiResult::new);
  }

  public static <T> PagedWebApiResult<T> of(Integer code, Either<? extends Throwable, PagedResult<T>> result) {
    return result.fold(exceptionMapper.getExceptionMapper(PagedWebApiResult::new, code), PagedWebApiResult::new);
  }

  public static <T> PagedWebApiResult<T> of(String message, Either<? extends Throwable, PagedResult<T>> result) {
    return result.fold(exceptionMapper.getExceptionMapper(PagedWebApiResult::new, message), PagedWebApiResult::new);
  }

  public static <T> PagedWebApiResult<T> of(Integer code, String message, Either<? extends Throwable, PagedResult<T>> result) {
    return result.fold(
        e -> new PagedWebApiResult<>(code, message),
        PagedWebApiResult::new
    );
  }

  public static <T> PagedWebApiResult<T> of(Function<Throwable, PagedWebApiResult<T>> errorMapper, Either<? extends Throwable, PagedResult<T>> result) {
    return result.fold(
        errorMapper,
        PagedWebApiResult::new
    );
  }

  public static <T> PagedWebApiResult<T> of(Try<PagedResult<T>> result) {
    return result.fold(exceptionMapper.getExceptionMapper(PagedWebApiResult::new), PagedWebApiResult::new);
  }

  public static <T> PagedWebApiResult<T> of(Integer code, Try<PagedResult<T>> result) {
    return result.fold(exceptionMapper.getExceptionMapper(PagedWebApiResult::new, code), PagedWebApiResult::new);
  }

  public static <T> PagedWebApiResult<T> of(String message, Try<PagedResult<T>> result) {
    return result.fold(exceptionMapper.getExceptionMapper(PagedWebApiResult::new, message), PagedWebApiResult::new);
  }

  public static <T> PagedWebApiResult<T> of(Integer code, String message, Try<PagedResult<T>> result) {
    return result.fold(
        e -> new PagedWebApiResult<>(code, message),
        PagedWebApiResult::new
    );
  }

  public static <T> PagedWebApiResult<T> of(Function<Throwable, PagedWebApiResult<T>> errorMapper, Try<PagedResult<T>> result) {
    return result.fold(
        errorMapper,
        PagedWebApiResult::new
    );
  }

  public <T2> PagedWebApiResult<T2> map(Function<T, T2> mapper) {
    if (getCode() != DEFAULT_ERROR_CODE) {
      return new PagedWebApiResult<>(getCode(), getMsg());
    }
    return new PagedWebApiResult<>(getData().map(mapper));
  }
}
