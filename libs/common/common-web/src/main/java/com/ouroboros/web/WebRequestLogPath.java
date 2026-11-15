package com.ouroboros.web;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;

/** 请求日志中的敏感路径段使用模板表示，不记录令牌。 */
public final class WebRequestLogPath {
  private static final String REFRESH_TOKEN_PATH = "/api/auth/refresh-token/";

  private WebRequestLogPath() {
  }

  public static String resolve(HttpServletRequest request) {
    Object errorPath = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
    String path = errorPath instanceof String value ? value : request.getRequestURI();
    int tokenIndex = path.indexOf(REFRESH_TOKEN_PATH);
    return tokenIndex < 0 ? path : path.substring(0, tokenIndex) + REFRESH_TOKEN_PATH + "{token}";
  }
}
