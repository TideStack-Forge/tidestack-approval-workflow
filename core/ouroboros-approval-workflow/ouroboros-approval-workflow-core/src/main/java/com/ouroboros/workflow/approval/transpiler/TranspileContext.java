package com.ouroboros.workflow.approval.transpiler;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.ouroboros.workflow.approval.definition.graph.StructuredFlowDefinition;
import com.ouroboros.workflow.approval.util.DefinitionUtil;
import com.ouroboros.workflow.approval.util.TreeGraphNode;

public class TranspileContext<M> {
  StructuredFlowDefinition structuredFlowDefinition;
  List<TreeGraphNode> treeGraphNodes;
  M flowModel;
  Map<String, String> entryPoints = new TreeMap<>(String::compareToIgnoreCase);
  Map<String, String> exitPoints = new TreeMap<>(String::compareToIgnoreCase);

  public TranspileContext(StructuredFlowDefinition flowDefinition, M flowModel) {
    this.structuredFlowDefinition = flowDefinition;
    this.flowModel = flowModel;
  }

  public M getModel() {
    return flowModel;
  }

  public StructuredFlowDefinition getStructuredFlowDefinition() {
    return structuredFlowDefinition;
  }

  public List<TreeGraphNode> getTreeGraphNodes() {
    if (treeGraphNodes == null) {
      treeGraphNodes = DefinitionUtil.traversePreOrder(structuredFlowDefinition);
    }
    return treeGraphNodes;
  }

  public void setEntryPoint(String nodeId, String entryPoint) {
    entryPoints.put(nodeId, entryPoint);
  }

  public String getEntryPoint(String nodeId) {
    return entryPoints.get(nodeId);
  }

  public void setExitPoint(String nodeId, String exitPoint) {
    exitPoints.put(nodeId, exitPoint);
  }

  public String getExitPoint(String nodeId) {
    return exitPoints.get(nodeId);
  }
}
