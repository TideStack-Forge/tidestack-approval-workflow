package com.ouroboros.workflow.approval.util;

import java.util.ArrayList;
import java.util.List;

import com.ouroboros.workflow.approval.definition.FlowNode;

/**
 * 集合了图和树特性的节点
 * 这是一种非常规的结构，半图半树，我们的FlowDefinition定义有问题，导致我们需要这样的结构
 * 需要评估是否需要重构FlowDefinition，主要是ForNode的子节点定义
 */
public class TreeGraphNode {
  FlowNode flowNode;
  TreeGraphNode prevNode;
  TreeGraphNode nextNode;
  TreeGraphNode parentNode;
  List<TreeGraphNode> children = new ArrayList<>();

  public TreeGraphNode(FlowNode flowNode) {
    this.flowNode = flowNode;
  }

  public FlowNode getFlowNode() {
    return flowNode;
  }

  public TreeGraphNode getPrevNode() {
    return prevNode;
  }

  public void setPrevNode(TreeGraphNode prevNode) {
    this.prevNode = prevNode;
  }

  public TreeGraphNode getNextNode() {
    return nextNode;
  }

  public void setNextNode(TreeGraphNode nextNode) {
    if (this.nextNode != null) {
      throw new UnsupportedOperationException("nextNode already set");
    }
    this.nextNode = nextNode;
    nextNode.setPrevNode(this);
  }

  public TreeGraphNode getParentNode() {
    return parentNode;
  }

  public void setParentNode(TreeGraphNode parentNode) {
    if (this.parentNode != null) {
      throw new UnsupportedOperationException("parentNode already set");
    }
    parentNode.addChildNode(this);
    this.parentNode = parentNode;
  }

  public void addChildNode(TreeGraphNode childNode) {
    children.add(childNode);
  }

  public List<TreeGraphNode> getChildren() {
    return children;
  }

  public Boolean isStartNode() {
    return parentNode == null && prevNode == null;
  }

  public Boolean isEndNode() {
    return parentNode == null && nextNode == null;
  }
}
