package com.ouroboros.uischema.schema.event;

import java.util.LinkedHashMap;
import java.util.Map;

import com.ouroboros.uischema.schema.AbstractSchema;

public class EventActionConfig extends AbstractSchema {

  public EventActionConfig() {
  }

  public EventActionConfig(Map<String, ?> props) {
    super(props);
  }

  public String getActionType() {
    return getProp(String.class, "actionType", null);
  }

  public void setActionType(String actionType) {
    setProp("actionType", actionType);
  }

  public String getComponentId() {
    return getProp(String.class, "componentId", null);
  }

  public void setComponentId(String componentId) {
    setProp("componentId", componentId);
  }

  public String getComponentName() {
    return getProp(String.class, "componentName", null);
  }

  public void setComponentName(String componentName) {
    setProp("componentName", componentName);
  }

  public Map<String, Object> getArgs() {
    return getProp(Map.class, "args", null);
  }

  public void setArgs(Map<String, Object> args) {
    setProp("args", args);
  }

  public void putArg(String key, Object value) {
    var args = getArgs();
    if (args == null) {
      args = new LinkedHashMap<>();
      setArgs(args);
    }
    args.put(key, value);
  }

  public Map<String, Object> getData() {
    return getProp(Map.class, "data", null);
  }

  public void setData(Map<String, Object> data) {
    setProp("data", data);
  }

  public String getDataMergeMode() {
    return getProp(String.class, "dataMergeMode", null);
  }

  public void setDataMergeMode(String dataMergeMode) {
    setProp("dataMergeMode", dataMergeMode);
  }

  public Object getPreventDefault() {
    return getProp("preventDefault");
  }

  public void setPreventDefault(Object preventDefault) {
    setProp("preventDefault", preventDefault);
  }

  public Object getStopPropagation() {
    return getProp("stopPropagation");
  }

  public void setStopPropagation(Object stopPropagation) {
    setProp("stopPropagation", stopPropagation);
  }

  public Object getExpression() {
    return getProp("expression");
  }

  public void setExpression(Object expression) {
    setProp("expression", expression);
  }

  public String getOutputVar() {
    return getProp(String.class, "outputVar", null);
  }

  public void setOutputVar(String outputVar) {
    setProp("outputVar", outputVar);
  }
}
