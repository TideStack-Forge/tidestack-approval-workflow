package com.ouroboros.workflow.approval.model;

import java.util.ArrayList;
import java.util.List;

import com.ouroboros.workflow.approval.definition.Candidate;

public class FlowNodeInfo {
  String definitionId;
  String name;
  String type;
  List<Candidate> Candidates = new ArrayList<>();
  Integer position;

  public String getDefinitionId() {
    return definitionId;
  }

  public void setDefinitionId(String definitionId) {
    this.definitionId = definitionId;
  }

  /**
   * 任务名
   */
  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  /**
   * 审批类型
   * <li> 单签 </li>
   * <li> 会签 </li>
   */
  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  /**
   * 候选人列表
   */
  public List<Candidate> getCandidates() {
    return Candidates;
  }

  public void setCandidates(List<Candidate> candidates) {
    Candidates = candidates;
  }

  public Integer getPosition() {
    return position;
  }

  public void setPosition(Integer position) {
    this.position = position;
  }
}
