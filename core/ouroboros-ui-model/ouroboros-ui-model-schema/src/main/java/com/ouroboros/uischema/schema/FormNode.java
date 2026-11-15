package com.ouroboros.uischema.schema;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.ouroboros.uischema.schema.decorator.*;

public class FormNode extends SchemaNode implements WithBody, WithTitle, WithStatic, WithInitAPI,
    WithAPI, WithAutoRefresh, WithActions, WithMessages {
  public FormNode() {
    super();
    setType("form");
  }

  public FormNode(SchemaNode node) {
    super(node);
    setBody(node.getProp("body"));
  }

  public FormNode(Map<String, ?> props) {
    super(props);
    setBody(props.get("body"));
  }

  public String getMode() {
    return getProp(String.class, "mode", null);
  }

  public void setMode(String mode) {
    setProp("mode", mode);
  }

  public HorizontalConfig getHorizontal() {
    return getProp(HorizontalConfig.class, "horizontal", null);
  }

  @JsonSetter
  public void setHorizontal(HorizontalConfig horizontal) {
    setProp("horizontal", horizontal);
  }

  public String getLabelAlign() {
    return getProp(String.class, "labelAlign", null);
  }

  public void setLabelAlign(String labelAlign) {
    setProp("labelAlign", labelAlign);
  }

  public Object getLabelWidth() {
    return getProp("labelWidth");
  }

  public void setLabelWidth(Integer labelWidth) {
    setProp("labelWidth", labelWidth);
  }

  public void setLabelWidth(String labelWidth) {
    setProp("labelWidth", labelWidth);
  }

  public String getSubmitText() {
    return getProp(String.class, "submitText", null);
  }

  public void setSubmitText(String submitText) {
    setProp("submitText", submitText);
  }

  public Boolean getWrapWithPanel() {
    return getProp(Boolean.class, "wrapWithPanel", null);
  }

  public void setWrapWithPanel(Boolean wrapWithPanel) {
    setProp("wrapWithPanel", wrapWithPanel);
  }

  public Object getPanelClassName() {
    return getProp("panelClassName");
  }

  public void setPanelClassName(String panelClassName) {
    setProp("panelClassName", panelClassName);
  }

  public void setPanelClassName(Map<String, Object> panelClassName) {
    setProp("panelClassName", panelClassName);
  }

  public List<FormRule> getRules() {
    return getProp(List.class, "rules", null);
  }

  @JsonSetter
  public void setRules(List<FormRule> rules) {
    setProp("rules", rules);
  }

  public Object getInitAsyncApi() {
    return getProp("initAsyncApi");
  }

  public void setInitAsyncApi(String initAsyncApi) {
    setProp("initAsyncApi", initAsyncApi);
  }

  public void setInitAsyncApi(ApiConfig initAsyncApi) {
    setProp("initAsyncApi", initAsyncApi);
  }

  @JsonSetter
  @JsonDeserialize(using = APIDeserializer.class)
  public void setInitAsyncApi(Object initAsyncApi) {
    setProp("initAsyncApi", initAsyncApi);
  }

  public Integer getInitCheckInterval() {
    return getProp(Integer.class, "initCheckInterval", null);
  }

  public void setInitCheckInterval(Integer initCheckInterval) {
    setProp("initCheckInterval", initCheckInterval);
  }

  public String getInitFinishedField() {
    return getProp(String.class, "initFinishedField", null);
  }

  public void setInitFinishedField(String initFinishedField) {
    setProp("initFinishedField", initFinishedField);
  }

  public Object getAsyncApi() {
    return getProp("asyncApi");
  }

  public void setAsyncApi(String asyncApi) {
    setProp("asyncApi", asyncApi);
  }

  public void setAsyncApi(ApiConfig asyncApi) {
    setProp("asyncApi", asyncApi);
  }

  @JsonSetter
  @JsonDeserialize(using = APIDeserializer.class)
  public void setAsyncApi(Object asyncApi) {
    setProp("asyncApi", asyncApi);
  }

  public Integer getCheckInterval() {
    return getProp(Integer.class, "checkInterval", null);
  }

  public void setCheckInterval(Integer checkInterval) {
    setProp("checkInterval", checkInterval);
  }

  public String getFinishedField() {
    return getProp(String.class, "finishedField", null);
  }

  public void setFinishedField(String finishedField) {
    setProp("finishedField", finishedField);
  }

  public String getPrimaryField() {
    return getProp(String.class, "primaryField", null);
  }

  public void setPrimaryField(String primaryField) {
    setProp("primaryField", primaryField);
  }

  public Boolean getSubmitOnChange() {
    return getProp(Boolean.class, "submitOnChange", null);
  }

  public void setSubmitOnChange(Boolean submitOnChange) {
    setProp("submitOnChange", submitOnChange);
  }

  public Boolean getSubmitOnInit() {
    return getProp(Boolean.class, "submitOnInit", null);
  }

  public void setSubmitOnInit(Boolean submitOnInit) {
    setProp("submitOnInit", submitOnInit);
  }

  public Boolean getResetAfterSubmit() {
    return getProp(Boolean.class, "resetAfterSubmit", null);
  }

  public void setResetAfterSubmit(Boolean resetAfterSubmit) {
    setProp("resetAfterSubmit", resetAfterSubmit);
  }

  public String getTarget() {
    return getProp(String.class, "target", null);
  }

  public void setTarget(String target) {
    setProp("target", target);
  }

  public String getRedirect() {
    return getProp(String.class, "redirect", null);
  }

  public void setRedirect(String redirect) {
    setProp("redirect", redirect);
  }

  public String getReload() {
    return getProp(String.class, "reload", null);
  }

  public void setReload(String reload) {
    setProp("reload", reload);
  }

  public Boolean getAutoFocus() {
    return getProp(Boolean.class, "autoFocus", null);
  }

  public void setAutoFocus(Boolean autoFocus) {
    setProp("autoFocus", autoFocus);
  }

  public Boolean getCanAccessSuperData() {
    return getProp(Boolean.class, "canAccessSuperData", null);
  }

  public void setCanAccessSuperData(Boolean canAccessSuperData) {
    setProp("canAccessSuperData", canAccessSuperData);
  }

  public String getPersistData() {
    return getProp(String.class, "persistData", null);
  }

  public void setPersistData(String persistData) {
    setProp("persistData", persistData);
  }

  public List<String> getPersistDataKeys() {
    return getProp(List.class, "persistDataKeys", null);
  }

  public void setPersistDataKeys(List<String> persistDataKeys) {
    setProp("persistDataKeys", persistDataKeys);
  }

  public Boolean getPreventEnterSubmit() {
    return getProp(Boolean.class, "preventEnterSubmit", null);
  }

  public void setPreventEnterSubmit(Boolean preventEnterSubmit) {
    setProp("preventEnterSubmit", preventEnterSubmit);
  }

  public Integer getColumnCount() {
    return getProp(Integer.class, "columnCount", null);
  }

  public void setColumnCount(Integer columnCount) {
    setProp("columnCount", columnCount);
  }

  public Boolean getInheritData() {
    return getProp(Boolean.class, "inheritData", null);
  }

  public void setInheritData(Boolean inheritData) {
    setProp("inheritData", inheritData);
  }

  public static class FormRule {
    private String rule;
    private String message;
    private List<String> name;

    public String getRule() {
      return rule;
    }

    public void setRule(String rule) {
      this.rule = rule;
    }

    public String getMessage() {
      return message;
    }

    public void setMessage(String message) {
      this.message = message;
    }

    public List<String> getName() {
      return name;
    }

    public void setName(List<String> name) {
      this.name = name;
    }
  }

  public static class HorizontalConfig {
    private Integer left;
    private Integer right;
    private Integer offset;
    private Boolean leftFixed;
    private Boolean justify;

    public Integer getLeft() {
      return left;
    }

    public void setLeft(Integer left) {
      this.left = left;
    }

    public Integer getRight() {
      return right;
    }

    public void setRight(Integer right) {
      this.right = right;
    }

    public Integer getOffset() {
      return offset;
    }

    public void setOffset(Integer offset) {
      this.offset = offset;
    }

    public Boolean getLeftFixed() {
      return leftFixed;
    }

    public void setLeftFixed(Boolean leftFixed) {
      this.leftFixed = leftFixed;
    }

    public Boolean getJustify() {
      return justify;
    }

    public void setJustify(Boolean justify) {
      this.justify = justify;
    }
  }
}
