package com.ouroboros.uischema.schema;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.ouroboros.uischema.schema.decorator.WithAPI;
import com.ouroboros.uischema.schema.decorator.WithLabel;
import com.ouroboros.uischema.schema.decorator.WithPlaceholder;
import com.ouroboros.uischema.schema.decorator.WithStatic;

@SuppressWarnings("unused")
public class FormItemNode extends SchemaNode implements WithLabel, WithPlaceholder, WithStatic {
  public FormItemNode() {
    super();
  }

  public FormItemNode(SchemaNode node) {
    super(node);
  }

  public Boolean getRequired() {
    return getProp(Boolean.class, "required", null);
  }

  public void setRequired(Boolean required) {
    setProp("required", required);
  }

  public Object getValue() {
    return getProp("value");
  }

  public void setValue(Object value) {
    setProp("value", value);
  }

  public String getDescription() {
    return getProp(String.class, "description", null);
  }

  public void setDescription(String description) {
    setProp("description", description);
  }

  public Object getInputClassName() {
    return getProp("inputClassName");
  }

  public void setInputClassName(String inputClassName) {
    setProp("inputClassName", inputClassName);
  }

  public void setInputClassName(Map<String, Object> inputClassName) {
    setProp("inputClassName", inputClassName);
  }

  public Object getLabelClassName() {
    return getProp("labelClassName");
  }

  public void setLabelClassName(String labelClassName) {
    setProp("labelClassName", labelClassName);
  }

  public void setLabelClassName(Map<String, Object> labelClassName) {
    setProp("labelClassName", labelClassName);
  }

  @SuppressWarnings("unchecked")
  public Map<String, Object> getValidations() {
    return getProp(Map.class, "validations", null);
  }

  public void setValidations(Map<String, Object> validations) {
    setProp("validations", validations);
  }

  @SuppressWarnings("unchecked")
  public Map<String, String> getValidationErrors() {
    return getProp(Map.class, "validationErrors", null);
  }

  public void setValidationErrors(Map<String, String> validationErrors) {
    setProp("validationErrors", validationErrors);
  }

  public String getLabelAlign() {
    return getProp(String.class, "labelAlign", null);
  }

  public void setLabelAlign(String labelAlign) {
    setProp("labelAlign", labelAlign);
  }

  public Object getLabelRemark() {
    return getProp("labelRemark");
  }

  public void setLabelRemark(String labelRemark) {
    setProp("labelRemark", labelRemark);
  }

  public void setLabelRemark(RemarkNode labelRemark) {
    setProp("labelRemark", labelRemark);
  }

  public Boolean getInline() {
    return getProp(Boolean.class, "inline", null);
  }

  public void setInline(Boolean inline) {
    setProp("inline", inline);
  }

  public Boolean getSubmitOnChange() {
    return getProp(Boolean.class, "submitOnChange", null);
  }

  public void setSubmitOnChange(Boolean submitOnChange) {
    setProp("submitOnChange", submitOnChange);
  }

  public Boolean getDisabled() {
    return getProp(Boolean.class, "disabled", null);
  }

  public void setDisabled(Boolean disabled) {
    setProp("disabled", disabled);
  }

  public String getDisabledOn() {
    return getProp(String.class, "disabledOn", null);
  }

  public void setDisabledOn(String disabledOn) {
    setProp("disabledOn", disabledOn);
  }

  public String getRequiredOn() {
    return getProp(String.class, "requiredOn", null);
  }

  public void setRequiredOn(String requiredOn) {
    setProp("requiredOn", requiredOn);
  }

  public String getValidateApi() {
    return getProp(String.class, "validateApi", null);
  }

  public void setValidateApi(String validateApi) {
    setProp("validateApi", validateApi);
  }

  public AutoFillConfig getAutoFill() {
    return getProp(AutoFillConfig.class, "autoFill", null);
  }

  @JsonSetter
  public void setAutoFill(AutoFillConfig autoFill) {
    setProp("autoFill", autoFill);
  }

  public void setAutoFill(Object autoFill) {
    setProp("autoFill", autoFill);
  }

  public Object getStaticInputClassName() {
    return getProp("staticInputClassName");
  }

  public void setStaticInputClassName(String staticInputClassName) {
    setProp("staticInputClassName", staticInputClassName);
  }

  public void setStaticInputClassName(Map<String, Object> staticInputClassName) {
    setProp("staticInputClassName", staticInputClassName);
  }

  public Object getStaticLabelClassName() {
    return getProp("staticLabelClassName");
  }

  public void setStaticLabelClassName(String staticLabelClassName) {
    setProp("staticLabelClassName", staticLabelClassName);
  }

  public void setStaticLabelClassName(Map<String, Object> staticLabelClassName) {
    setProp("staticLabelClassName", staticLabelClassName);
  }

  public SchemaNode getStaticSchema() {
    return getProp(SchemaNode.class, "staticSchema", null);
  }

  @JsonSetter
  public void setStaticSchema(SchemaNode staticSchema) {
    setProp("staticSchema", staticSchema);
  }

  public static class AutoFillConfig extends SchemaNode implements WithAPI {
    public Boolean getShowSuggestion() {
      return getProp(Boolean.class, "showSuggestion", null);
    }

    public void setShowSuggestion(Boolean showSuggestion) {
      setProp("showSuggestion", showSuggestion);
    }

    public Boolean getSilent() {
      return getProp(Boolean.class, "silent", null);
    }

    public void setSilent(Boolean silent) {
      setProp("silent", silent);
    }

    @SuppressWarnings("unchecked")
    public Map<String, String> getFillMapping() {
      return getProp(Map.class, "fillMapping", null);
    }

    public void setFillMapping(Map<String, String> fillMapping) {
      setProp("fillMapping", fillMapping);
    }

    public String getTrigger() {
      return getProp(String.class, "trigger", null);
    }

    public void setTrigger(String trigger) {
      setProp("trigger", trigger);
    }

    public String getMode() {
      return getProp(String.class, "mode", null);
    }

    public void setMode(String mode) {
      setProp("mode", mode);
    }

    public String getLabelField() {
      return getProp(String.class, "labelField", null);
    }

    public void setLabelField(String labelField) {
      setProp("labelField", labelField);
    }

    public String getPosition() {
      return getProp(String.class, "position", null);
    }

    public void setPosition(String position) {
      setProp("position", position);
    }

    public String getSize() {
      return getProp(String.class, "size", null);
    }

    public void setSize(String size) {
      setProp("size", size);
    }

    @SuppressWarnings("unchecked")
    public List<TableColumnNode> getColumns() {
      return getProp(List.class, "columns", null);
    }

    @JsonSetter
    public void setColumns(List<TableColumnNode> columns) {
      setProp("columns", columns);
    }

    public FormNode getFilter() {
      return getProp(FormNode.class, "filter", null);
    }

    @JsonSetter
    public void setFilter(FormNode filter) {
      setProp("filter", filter);
    }
  }
}
