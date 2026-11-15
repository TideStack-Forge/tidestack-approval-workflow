package com.ouroboros.workflow.approval.definition;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonTypeIdResolver;
import com.ouroboros.workflow.approval.definition.candidates.CandidateTypeIdResolver;

/**
 * 候选人
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonTypeIdResolver(CandidateTypeIdResolver.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class Candidate {
  String type;

  /**
   * 候选人类型
   * <li> User       用户 </li>
   * <li> Role       角色 </li>
   * <li> Position   岗位 </li>
   * <li> UserSelect 自选 </li>
   * <li> Starter    发起人自己 </li>
   * <li> FormField  表单字段 </li>
   */
  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }
}
