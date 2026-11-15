package com.ouroboros.workflow.approval.definition;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class IOParameterSetting {
  String source;
  String sourceExpression;
  String target;

  public IOParameterSetting() {
  }

  public IOParameterSetting(String source, String sourceExpression, String target) {
    this.source = source;
    this.sourceExpression = sourceExpression;
    this.target = target;
  }

  /*---------------------------------------------------------------------------------------------------------------*/
  public String getSource() {
    return source;
  }

  public void setSource(String source) {
    this.source = source;
  }

  public String getExpression() {
    return sourceExpression;
  }

  public void setExpression(String expression) {
    this.sourceExpression = expression;
  }

  public String getTarget() {
    return target;
  }

  public void setTarget(String target) {
    this.target = target;
  }
}
