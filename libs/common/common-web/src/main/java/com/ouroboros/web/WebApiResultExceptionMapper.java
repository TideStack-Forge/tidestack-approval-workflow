package com.ouroboros.web;

import java.util.function.BiFunction;
import java.util.function.Function;

import io.vavr.Tuple3;

public interface WebApiResultExceptionMapper {
  Tuple3<Integer, String, Object> map(Throwable throwable);

  default <T extends AbstractWebApiResult<R>, R> Function<Throwable, T> getExceptionMapper(BiFunction<Integer, String, T> constructor) {
    return throwable -> {
      var tuple = map(throwable);
      var result = constructor.apply(tuple._1, tuple._2);
      if (tuple._3 != null) {
        result.setErrors(tuple._3);
      }
      return result;
    };
  }

  default <T extends AbstractWebApiResult<R>, R> Function<Throwable, T> getExceptionMapper(BiFunction<Integer, String, T> constructor, Integer code) {
    return throwable -> {
      var tuple = map(throwable);
      var result = constructor.apply(code, tuple._2);
      if (tuple._3 != null) {
        result.setErrors(tuple._3);
      }
      return result;
    };
  }

  default <T extends AbstractWebApiResult<R>, R> Function<Throwable, T> getExceptionMapper(BiFunction<Integer, String, T> constructor, String message) {
    return throwable -> {
      var tuple = map(throwable);
      var result = constructor.apply(tuple._1, message);
      if (tuple._3 != null) {
        result.setErrors(tuple._3);
      }
      return result;
    };
  }
}
