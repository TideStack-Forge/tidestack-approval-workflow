package com.ouroboros.workflow.approval.definition.nodes;

import java.util.List;

import com.ouroboros.workflow.approval.definition.FlowNode;
import com.ouroboros.workflow.approval.definition.NodeBranch;

public class ForkNode extends FlowNode {
  List<NodeBranch> nodeBranches;
  String logic;

  public ForkNode() {
    super();
  }

  @Override
  public String getType() {
    return "分流节点";
  }

  public List<NodeBranch> getBranches() {
    return nodeBranches;
  }

  public void setBranches(List<NodeBranch> nodeBranches) {
    this.nodeBranches = nodeBranches;
  }

  public String getLogic() {
    return logic;
  }

  public void setLogic(String logic) {
    this.logic = logic;
  }
}
