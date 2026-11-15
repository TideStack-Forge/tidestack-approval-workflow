package com.ouroboros.uischema.schema;

import java.util.Map;
import java.util.regex.Pattern;

import com.ouroboros.uischema.schema.decorator.WithMessages;

public class ApiConfig extends AbstractSchema implements WithMessages {
  static Pattern methodUrlPatten = Pattern.compile("^(?i)(?<method>get|post|put|delete|patch|head|options|trace):\\s*(?<url>.*)$");

  public ApiConfig() {
    super();
  }

  public ApiConfig(Map<String, ?> apiConfig) {
    super(apiConfig);
  }

  public ApiConfig(String apiStr) {
    super();
    var matcher = methodUrlPatten.matcher(apiStr);
    if (matcher.find()) {
      setMethod(matcher.group("method"));
      setUrl(matcher.group("url"));
    } else {
      setUrl(apiStr);
    }
  }

  public String getMethod() {
    return getProp(String.class, "method", null);
  }

  public void setMethod(String method) {
    setProp("method", method);
  }

  public String getUrl() {
    return getProp(String.class, "url", null);
  }

  public void setUrl(String url) {
    setProp("url", url);
  }

  public Object getData() {
    return getProp("data");
  }

  public void setData(String data) {
    setProp("data", data);
  }

  public void setData(Map<String, ?> data) {
    setProp("data", data);
  }

  public String getDataType() {
    return getProp(String.class, "dataType", null);
  }

  public void setDataType(String dataType) {
    setProp("dataType", dataType);
  }

  public Object getQsOptions() {
    return getProp("qsOptions");
  }

  public void setQsOptions(String qsOptions) {
    setProp("qsOptions", qsOptions);
  }

  public void setQsOptions(Map<String, Object> qsOptions) {
    setProp("qsOptions", qsOptions);
  }

  public Map<String, String> getHeaders() {
    return getProp(Map.class, "headers", null);
  }

  public void setHeaders(Map<String, String> headers) {
    setProp("headers", headers);
  }

  public String getSendOn() {
    return getProp(String.class, "sendOn", null);
  }

  public void setSendOn(String sendOn) {
    setProp("sendOn", sendOn);
  }

  public Integer getCache() {
    return getProp(Integer.class, "cache", null);
  }

  public void setCache(Integer cache) {
    setProp("cache", cache);
  }

  public String getRequestAdaptor() {
    return getProp(String.class, "requestAdaptor", null);
  }

  public void setRequestAdaptor(String requestAdaptor) {
    setProp("requestAdaptor", requestAdaptor);
  }

  public String getAdaptor() {
    return getProp(String.class, "adaptor", null);
  }

  public void setAdaptor(String adaptor) {
    setProp("adaptor", adaptor);
  }

  public Boolean getReplaceData() {
    return getProp(Boolean.class, "replaceData", null);
  }

  public void setReplaceData(Boolean replaceData) {
    setProp("replaceData", replaceData);
  }

  public String getResponseType() {
    return getProp(String.class, "responseType", null);
  }

  public void setResponseType(String responseType) {
    setProp("responseType", responseType);
  }

  public Boolean getAutoRefresh() {
    return getProp(Boolean.class, "autoRefresh", null);
  }

  public void setAutoRefresh(Boolean autoRefresh) {
    setProp("autoRefresh", autoRefresh);
  }

  public Map<String, String> getResponseData() {
    return getProp(Map.class, "responseData", null);
  }

  public void setResponseData(Map<String, ?> responseData) {
    setProp("responseData", responseData);
  }

  public String getTrackExpression() {
    return getProp(String.class, "trackExpression", null);
  }

  public void setTrackExpression(String trackExpression) {
    setProp("trackExpression", trackExpression);
  }

  public void setSuccessMessage(String message) {
    var messages = getMessages();
    if (messages == null) {
      messages = new Messages();
      setMessages(messages);
    }
    messages.setSuccess(message);
  }

  public void setFailedMessage(String message) {
    var messages = getMessages();
    if (messages == null) {
      messages = new Messages();
      setMessages(messages);
    }
    messages.setFailed(message);
  }
}
