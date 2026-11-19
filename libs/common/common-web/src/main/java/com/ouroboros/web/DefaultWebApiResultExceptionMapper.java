package com.ouroboros.web;

import jakarta.annotation.Priority;

import org.apache.commons.lang3.ObjectUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import io.vavr.Tuple;
import io.vavr.Tuple3;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.exception.ValidationException;

@Priority(0)
public class DefaultWebApiResultExceptionMapper implements WebApiResultExceptionMapper {
  public static final int INTERNAL_ERROR_CODE = 50000;
  public static final String INTERNAL_ERROR_MESSAGE = "服务暂时不可用，请稍后重试";

  @Override
  public Tuple3<Integer, String, Object> map(Throwable throwable) {
    if (throwable instanceof ValidationException e) {
      var errors = e.getErrors();
      Object finalErrors = ObjectUtils.isEmpty(errors) ? e.getMessage() : errors;
      return Tuple.of(e.getErrorCode(), "", finalErrors);
    }
    if (throwable instanceof HttpRequestMethodNotSupportedException) {
      return Tuple.of(405, "请求方法不支持", null);
    }
    if (throwable instanceof MethodArgumentTypeMismatchException) {
      return Tuple.of(400, "请求参数格式错误", null);
    }
    if (throwable instanceof HttpMessageNotReadableException) {
      return Tuple.of(400, "请求体格式错误", null);
    }
    if (throwable instanceof ErrorResponse errorResponse && errorResponse.getStatusCode().is4xxClientError()) {
      int status = errorResponse.getStatusCode().value();
      HttpStatus httpStatus = HttpStatus.resolve(status);
      return Tuple.of(status, httpStatus == null ? "请求处理失败" : httpStatus.getReasonPhrase(), null);
    }
    if (throwable instanceof BusinessException e) {
      return Tuple.of(e.getErrorCode(), e.getMessage(), null);
    }
    return Tuple.of(INTERNAL_ERROR_CODE, INTERNAL_ERROR_MESSAGE, null);
  }
}
