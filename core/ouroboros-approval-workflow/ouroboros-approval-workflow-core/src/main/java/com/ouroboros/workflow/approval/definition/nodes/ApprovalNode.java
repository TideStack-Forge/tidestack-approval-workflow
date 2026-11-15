package com.ouroboros.workflow.approval.definition.nodes;

import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.ouroboros.workflow.approval.definition.FlowNode;

/**
 * @author songmingxu
 */
@JsonDeserialize(using = ApprovalNodeJsonDeserializer.class)
public abstract class ApprovalNode extends FlowNode {
  String subType;

  List<String> actions;

  Timeout timeout;

  public ApprovalNode() {
    super();
  }

  @Override
  public String getType() {
    return BuiltInFlowNodeTypes.APPROVAL;
  }

  NoCandidateMode noCandidateMode;

  List<String> reAssignUsers;

  boolean allowRejectToSelf = true;

  public enum NoCandidateMode {
    // 同意
    approved("approved"),
    // 拒绝
    rejected("rejected"),
    //转交给某人
    reAssignUser("reAssignUser");
    final private String mode;

    NoCandidateMode(String mode) {
      this.mode = mode;
    }

    @JsonCreator
    public static NoCandidateMode fromValue(String mode) {
      for (NoCandidateMode anEnum : NoCandidateMode.values()) {
        if (Objects.equals(anEnum.mode, mode)) {
          return anEnum;
        }
      }
      //这里设置为空时，自动拒绝
      return NoCandidateMode.rejected;
    }

    @Override
    @JsonValue
    public String toString() {
      return mode;
    }
  }

  public String getSubType() {
    return subType;
  }

  public void setSubType(String subType) {
    this.subType = subType;
  }

  public List<String> getActions() {
    return actions;
  }

  public void setActions(List<String> actions) {
    this.actions = actions;
  }

  public Timeout getTimeout() {
    return timeout;
  }

  public void setTimeout(Timeout timeout) {
    this.timeout = timeout;
  }

  public NoCandidateMode getNoCandidateMode() {
    return noCandidateMode;
  }

  public void setNoCandidateMode(NoCandidateMode noCandidateMode) {
    this.noCandidateMode = noCandidateMode;
  }

  public List<String> getReAssignUsers() {
    return reAssignUsers;
  }

  public void setReAssignUsers(List<String> reAssignUsers) {
    this.reAssignUsers = reAssignUsers;
  }

  public boolean isAllowRejectToSelf() {
    return allowRejectToSelf;
  }

  public void setAllowRejectToSelf(boolean allowRejectToSelf) {
    this.allowRejectToSelf = allowRejectToSelf;
  }

  public static class Timeout {
    String duration;
    String action;
    String transferTo;

    public String getDuration() {
      return duration;
    }

    public void setDuration(String duration) {
      this.duration = duration;
    }

    public String getAction() {
      return action;
    }

    public void setAction(String action) {
      this.action = action;
    }

    public String getTransferTo() {
      return transferTo;
    }

    public void setTransferTo(String transferTo) {
      this.transferTo = transferTo;
    }
  }
}
