package com.ouroboros.workflow.approval.definition;


import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ouroboros.workflow.approval.definition.condition.Condition;

@JsonIgnoreProperties(ignoreUnknown = true)
public class NodeBranch {
  String name;
  String id;
  Condition condition;
  String first;

  /*---------------------------------------------------------------------------------------------------------------*/
  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public Condition getCondition() {
    return condition;
  }

  @JsonIgnore
  public String getConditionStr() {
    return Optional.ofNullable(condition).map(Condition::toExpression).orElse("true");
  }

  public void setCondition(Condition condition) {
    this.condition = condition;
  }

  public String getFirst() {
    return first;
  }

  public void setFirst(String first) {
    this.first = first;
  }

}
