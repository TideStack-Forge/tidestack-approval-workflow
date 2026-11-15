package com.ouroboros.web;

import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

@ControllerAdvice
public class WebApiResultHttpStatusAdvice implements ResponseBodyAdvice<Object> {
  @Override
  public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
    return AbstractWebApiResult.class.isAssignableFrom(returnType.getParameterType());
  }

  @Override
  public Object beforeBodyWrite(
      Object body,
      MethodParameter returnType,
      MediaType selectedContentType,
      Class<? extends HttpMessageConverter<?>> selectedConverterType,
      ServerHttpRequest request,
      ServerHttpResponse response
  ) {
    if (!(body instanceof AbstractWebApiResult<?>)) {
      return body;
    }
    if (hasExplicitResponseStatus(returnType) || hasNonDefaultHttpStatus(response)) {
      return body;
    }

    response.setStatusCode(HttpStatus.valueOf(
        WebApiResultHttpStatus.resolve(((AbstractWebApiResult<?>) body).getCode())));
    return body;
  }

  private boolean hasExplicitResponseStatus(MethodParameter returnType) {
    return AnnotatedElementUtils.findMergedAnnotation(returnType.getMethod(), ResponseStatus.class) != null
        || AnnotatedElementUtils.findMergedAnnotation(returnType.getContainingClass(), ResponseStatus.class) != null;
  }

  private boolean hasNonDefaultHttpStatus(ServerHttpResponse response) {
    if (!(response instanceof ServletServerHttpResponse)) {
      return false;
    }
    int status = ((ServletServerHttpResponse) response).getServletResponse().getStatus();
    return status > 0 && status != HttpStatus.OK.value();
  }

}
