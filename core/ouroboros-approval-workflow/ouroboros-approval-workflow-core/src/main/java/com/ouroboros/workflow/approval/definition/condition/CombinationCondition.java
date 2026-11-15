package com.ouroboros.workflow.approval.definition.condition;

import java.util.ArrayList;
import java.util.List;

public class CombinationCondition implements Condition {
  String conjunction;
  List<Condition> children = new ArrayList<Condition>();

  public String getConjunction() {
    return conjunction;
  }

  public Condition setConjunction(String conjunction) {
    this.conjunction = conjunction;
    return this;
  }

  public List<Condition> getChildren() {
    return children;
  }

  public CombinationCondition setChildren(List<Condition> children) {
    this.children = children;
    return this;
  }

  @Override
  public String toExpression() {
    if (children.size() == 0) {
      return "true";
    }

    if (children.size() == 1) {
      return children.get(0).toExpression();
    }
    var conj = "or".equalsIgnoreCase(conjunction) ? "||" : "&&";
    return "(" + children.stream().map(Condition::toExpression).reduce((a, b) -> a + " " + conj + " " + b).get() + ")";
  }
}
