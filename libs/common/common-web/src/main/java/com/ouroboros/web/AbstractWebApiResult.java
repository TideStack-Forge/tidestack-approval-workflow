package com.ouroboros.web;

import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.ouroboros.util.SPI;

public abstract class AbstractWebApiResult<T> {
  protected static final WebApiResultExceptionMapper exceptionMapper = SPI
      .getReversedServiceStream(WebApiResultExceptionMapper.class)
      .findFirst()
      .orElseGet(DefaultWebApiResultExceptionMapper::new);
  static final Integer DEFAULT_ERROR_CODE = -1;
  static final Integer DEFAULT_SUCCESS_CODE = 0;
  static final String DEFAULT_NOT_FOUND_MESSAGE = "未找到指定数据";
  /**
   * 状态：0正常、否则为异常，-1为默认异常码，其他为自定义异常码
   */
  private int code;

  /**
   * 错误信息，正常为空
   */
  private String msg;

  /**
   * 数据
   */
  private T data;

  private Object errors;

  private Map<String, Object> extra = new LinkedHashMap<>();

  public AbstractWebApiResult(Integer code, String msg, T data) {
    this.code = code;
    this.msg = msg;
    this.data = data;
  }

  public AbstractWebApiResult(Integer code, String msg) {
    this(code, msg, null);
  }

  public AbstractWebApiResult(T data) {
    this(DEFAULT_SUCCESS_CODE, null, data);
  }

  public AbstractWebApiResult() {
    this(null);
  }

  public int getCode() {
    return code;
  }

  public void setCode(int code) {
    this.code = code;
  }

  public String getMsg() {
    return msg;
  }

  public void setMsg(String msg) {
    this.msg = msg;
  }

  public Object getErrors() {
    return errors;
  }

  public void setErrors(Object errors) {
    this.errors = errors;
  }

  public T getData() {
    return data;
  }

  public void setData(T data) {
    this.data = data;
  }

  @JsonAnyGetter
  public Map<String, Object> getExtra() {
    return extra;
  }

  public void setExtra(Map<String, Object> extra) {
    this.extra = extra;
  }

  @JsonAnySetter
  public void setExtra(String key, Object value) {
    this.extra.put(key, value);
  }
}
