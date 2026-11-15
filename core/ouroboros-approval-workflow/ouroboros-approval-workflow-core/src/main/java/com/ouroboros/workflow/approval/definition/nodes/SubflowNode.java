package com.ouroboros.workflow.approval.definition.nodes;

import java.util.Collections;
import java.util.List;

import com.ouroboros.workflow.approval.definition.FlowNode;
import com.ouroboros.workflow.approval.definition.IOParameterSetting;

public class SubflowNode extends FlowNode {
  String subFlowKey;
  String rejectAction;
  List<IOParameterSetting> inParameters;
  List<IOParameterSetting> outParameters;

  public SubflowNode() {
    super();
  }

  @Override
  public String getType() {
    return BuiltInFlowNodeTypes.SUBFLOW;
  }

  public String getSubFlowKey() {
    return subFlowKey;
  }

  public void setSubFlowKey(String subFlowKey) {
    this.subFlowKey = subFlowKey;
  }

  public List<IOParameterSetting> getInParameters() {
    return inParameters == null ? Collections.emptyList() : inParameters;
  }

  public void setInParameters(List<IOParameterSetting> inParameters) {
    this.inParameters = inParameters;
  }

  public List<IOParameterSetting> getOutParameters() {
    return outParameters == null ? Collections.emptyList() : outParameters;
  }

  public void setOutParameters(List<IOParameterSetting> outParameters) {
    this.outParameters = outParameters;
  }

  public String getRejectAction() {
    return rejectAction;
  }

  public void setRejectAction(String rejectAction) {
    this.rejectAction = rejectAction;
  }
}
