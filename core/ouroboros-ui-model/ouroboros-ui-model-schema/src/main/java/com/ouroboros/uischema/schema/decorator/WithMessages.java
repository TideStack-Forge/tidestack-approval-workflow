package com.ouroboros.uischema.schema.decorator;

import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.*;
import com.ouroboros.uischema.schema.Schema;

public interface WithMessages extends Schema {

  default Messages getMessages() {
    var messages = getProp("messages");
    if (messages instanceof Map<?, ?> messagesMap) {
      var newMessages = new Messages((Map<String, String>) messagesMap);
      setProp("messages", newMessages);
      return newMessages;
    }
    if (messages instanceof Messages msg) {
      return msg;
    }
    return null;
  }

  @JsonSetter
  default void setMessages(Messages messages) {
    setProp("messages", messages);
  }

  default void setMessages(Map<String, String> messages) {
    setProp("messages", new Messages(messages));
  }

  @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, setterVisibility = JsonAutoDetect.Visibility.NONE)
  class Messages {
    @JsonIgnore
    private final Map<String, String> messages;

    public Messages() {
      this.messages = new LinkedHashMap<>();
    }

    public Messages(Map<String, String> messages) {
      this.messages = messages;
    }

    @JsonAnyGetter
    public Map<String, String> getMessages() {
      return messages;
    }

    public void setMessages(Map<String, String> messages) {
      this.messages.putAll(messages);
    }

    @JsonAnySetter
    public void setMessage(String key, String value) {
      messages.put(key, value);
    }

    public String getFetchSuccess() {
      return messages.get("fetchSuccess");
    }

    public String getSuccess() {
      return messages.get("success");
    }

    public void setSuccess(String success) {
      messages.put("success", success);
    }

    public String getFetchFailed() {
      return messages.get("fetchFailed");
    }

    public void setFetchFailed(String fetchFailed) {
      messages.put("fetchFailed", fetchFailed);
    }

    public String getSaveFailed() {
      return messages.get("saveFailed");
    }

    public void setSaveFailed(String saveFailed) {
      messages.put("saveFailed", saveFailed);
    }

    public String getSaveSuccess() {
      return messages.get("saveSuccess");
    }

    public void setSaveSuccess(String saveSuccess) {
      messages.put("saveSuccess", saveSuccess);
    }

    public String getFailed() {
      return messages.get("failed");
    }

    public void setFailed(String failed) {
      messages.put("failed", failed);
    }

    public String getSaveOrderFailed() {
      return messages.get("saveOrderFailed");
    }

    public void setSaveOrderFailed(String saveOrderFailed) {
      messages.put("saveOrderFailed", saveOrderFailed);
    }

    public String getSaveOrderSuccess() {
      return messages.get("saveOrderSuccess");
    }

    public void setSaveOrderSuccess(String saveOrderSuccess) {
      messages.put("saveOrderSuccess", saveOrderSuccess);
    }

    public void getQuickSaveFailed() {
      messages.get("quickSaveFailed");
    }

    public void setQuickSaveFailed(String quickSaveFailed) {
      messages.put("quickSaveFailed", quickSaveFailed);
    }

    public void getQuickSaveSuccess() {
      messages.get("quickSaveSuccess");
    }

    public void setQuickSaveSuccess(String quickSaveSuccess) {
      messages.put("quickSaveSuccess", quickSaveSuccess);
    }
  }
}
