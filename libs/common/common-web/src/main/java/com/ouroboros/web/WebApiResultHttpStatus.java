package com.ouroboros.web;

import org.springframework.http.HttpStatus;

/** WebApiResult 错误码到 HTTP 状态的共享映射，供 MVC 和函数式 HTTP 适配器使用。 */
public final class WebApiResultHttpStatus {
  private WebApiResultHttpStatus() {
  }

  public static int resolve(int code) {
    int candidate = code;
    if (code >= 1000) {
      candidate = Integer.parseInt(String.valueOf(code).substring(0, 3));
    }
    return candidate >= 100 && candidate <= 599 && HttpStatus.resolve(candidate) != null
        ? candidate : HttpStatus.OK.value();
  }
}
