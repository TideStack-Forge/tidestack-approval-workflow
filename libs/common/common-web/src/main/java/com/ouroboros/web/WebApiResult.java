package com.ouroboros.web;

import java.util.Optional;
import java.util.function.Function;

import io.vavr.control.Either;
import io.vavr.control.Try;

/**
 * @param <T> 数据类型
 * @author liansz
 */
public class WebApiResult<T> extends AbstractWebApiResult<T> {
  public WebApiResult(Integer code, String msg, T data) {
    super(code, msg, data);
  }

  public WebApiResult(Integer code, String msg) {
    super(code, msg);
  }

  public WebApiResult(T data) {
    super(data);
  }

  public WebApiResult() {
    super();
  }

  public static <T> WebApiResult<T> ok() {
    return new WebApiResult<>();
  }

  public static <T> WebApiResult<T> ok(T data) {
    return new WebApiResult<>(data);
  }

  public static <T> WebApiResult<T> success() {
    return new WebApiResult<>();
  }

  public static <T> WebApiResult<T> success(T data) {
    return new WebApiResult<>(data);
  }

  public static <T> WebApiResult<T> error(String msg) {
    return new WebApiResult<>(-1, msg);
  }

  public static <T> WebApiResult<T> error(int code, String msg) {
    return new WebApiResult<>(code, msg);
  }

  public static <T> WebApiResult<T> fail(String msg) {
    return error(msg);
  }

  public static <T> WebApiResult<T> fail(int code, String msg) {
    return error(code, msg);
  }

  public static <T> WebApiResult<T> build(int code, String msg, T data) {
    return new WebApiResult<>(code, msg, data);
  }

  public static WebApiResult<?> of(Throwable exception) {
    return exceptionMapper.getExceptionMapper(WebApiResult::new).apply(exception);
  }

  public static <T> WebApiResult<T> of(Try<T> result) {
    return result.fold(exceptionMapper.getExceptionMapper(WebApiResult::new), WebApiResult::new);
  }

  public static <T> WebApiResult<T> of(Integer code, Try<T> result) {
    return result.fold(exceptionMapper.getExceptionMapper(WebApiResult::new, code), WebApiResult::new);
  }

  public static <T> WebApiResult<T> of(Integer code, String message, Try<T> result) {
    return result.fold(err -> new WebApiResult<>(code, message), WebApiResult::new);
  }

  public static <T> WebApiResult<T> of(String message, Try<T> result) {
    return result.fold(exceptionMapper.getExceptionMapper(WebApiResult::new, message), WebApiResult::new);
  }

  public static <T> WebApiResult<T> of(Function<Throwable, WebApiResult<T>> errorMapper, Try<T> result) {
    return result.fold(errorMapper, WebApiResult::new);
  }

  public static <T> WebApiResult<T> of(Either<? extends Throwable, T> result) {
    return result.fold(exceptionMapper.getExceptionMapper(WebApiResult::new), WebApiResult::new);
  }

  public static <T> WebApiResult<T> of(Integer code, Either<? extends Throwable, T> result) {
    return result.fold(exceptionMapper.getExceptionMapper(WebApiResult::new, code), WebApiResult::new);
  }

  public static <T> WebApiResult<T> of(String message, Either<? extends Throwable, T> result) {
    return result.fold(exceptionMapper.getExceptionMapper(WebApiResult::new, message), WebApiResult::new);
  }

  public static <T> WebApiResult<T> of(Integer code, String message, Either<? extends Throwable, T> result) {
    return result.fold(err -> new WebApiResult<>(code, message), WebApiResult::new);
  }

  public static <T> WebApiResult<T> of(Function<Throwable, WebApiResult<T>> errorMapper, Either<? extends Throwable, T> result) {
    return result.fold(errorMapper, WebApiResult::new);
  }

  public static <T> WebApiResult<T> of(Optional<T> result) {
    return result.map(WebApiResult::new)
        .orElseGet(() -> new WebApiResult<>(DEFAULT_ERROR_CODE, DEFAULT_NOT_FOUND_MESSAGE));
  }

  public static <T> WebApiResult<T> of(Integer code, Optional<T> result) {
    return result.map(WebApiResult::new)
        .orElseGet(() -> new WebApiResult<>(code, DEFAULT_NOT_FOUND_MESSAGE));
  }

  public static <T> WebApiResult<T> of(String message, Optional<T> result) {
    return result.map(WebApiResult::new)
        .orElseGet(() -> new WebApiResult<>(DEFAULT_ERROR_CODE, message));
  }

  public static <T> WebApiResult<T> of(Integer code, String message, Optional<T> result) {
    return result.map(WebApiResult::new)
        .orElseGet(() -> new WebApiResult<>(code, message));
  }

  public <T2> WebApiResult<T2> map(Function<? super T, ? extends T2> mapper) {
    if (this.getCode() != DEFAULT_SUCCESS_CODE) {
      return new WebApiResult<>(this.getCode(), this.getMsg());
    }
    return new WebApiResult<>(mapper.apply(this.getData()));
  }
}
