package com.ouroboros.workflow.approval.definition.graph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.lang3.ObjectUtils;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.ouroboros.workflow.approval.definition.FlowDefinition.Notification;
import com.ouroboros.workflow.approval.definition.FlowNode;
import com.ouroboros.workflow.approval.definition.nodes.StartNode;
import com.ouroboros.workflow.approval.definition.v2.FlowEdgeType;
import com.ouroboros.workflow.approval.definition.v2.FlowEdgeV2;

public class StructuredFlowDefinition {
  Integer formatVersion = 1;
  String id;
  String name;
  String category = "";
  String icon = "";
  String description = "";
  String formType = "";
  String formKey = "";
  Notification notification;
  @JsonInclude(JsonInclude.Include.NON_NULL)
  JsonNode editorLayout;
  List<FlowNode> nodes = new ArrayList<FlowNode>();
  List<FlowEdgeV2> edges = new ArrayList<FlowEdgeV2>();

  @JsonIgnore
  Map<String, FlowNode> nodeMap;
  @JsonIgnore
  Map<String, List<FlowEdgeV2>> outgoingEdgeMap;
  @JsonIgnore
  Map<String, List<FlowEdgeV2>> incomingEdgeMap;

  public Integer getFormatVersion() {
    return formatVersion;
  }

  public void setFormatVersion(Integer formatVersion) {
    this.formatVersion = formatVersion;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getCategory() {
    return category;
  }

  public void setCategory(String category) {
    this.category = category;
  }

  public String getIcon() {
    return icon;
  }

  public void setIcon(String icon) {
    this.icon = icon;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getFormType() {
    return formType;
  }

  public void setFormType(String formType) {
    this.formType = formType;
  }

  public String getFormKey() {
    return formKey;
  }

  public void setFormKey(String formKey) {
    this.formKey = formKey;
  }

  public Notification getNotification() {
    return notification;
  }

  public void setNotification(Notification notification) {
    this.notification = notification;
  }

  public JsonNode getEditorLayout() {
    return editorLayout;
  }

  public void setEditorLayout(JsonNode editorLayout) {
    this.editorLayout = editorLayout;
  }

  public List<FlowNode> getNodes() {
    return nodes;
  }

  public void setNodes(List<FlowNode> nodes) {
    this.nodes = nodes;
    this.nodeMap = null;
  }

  public List<FlowEdgeV2> getEdges() {
    return edges;
  }

  public void setEdges(List<FlowEdgeV2> edges) {
    this.edges = edges;
    this.outgoingEdgeMap = null;
    this.incomingEdgeMap = null;
  }

  public FlowNode getNode(String nodeId) {
    return getNodeMap().get(nodeId);
  }

  public List<FlowEdgeV2> getOutgoingEdges(String sourceId) {
    return getOutgoingEdgeMap().getOrDefault(sourceId, Collections.<FlowEdgeV2>emptyList());
  }

  public List<FlowEdgeV2> getIncomingEdges(String targetId) {
    return getIncomingEdgeMap().getOrDefault(targetId, Collections.<FlowEdgeV2>emptyList());
  }

  /**
   * Legacy helper for V1 fork compatibility. Stable V2 semantics should read ordinary outgoing
   * {@link FlowEdgeType#SEQUENCE} edges instead of branch edges.
   */
  @Deprecated
  public List<FlowEdgeV2> getBranchEdges(String sourceId) {
    return getOutgoingEdges(sourceId).stream()
        .filter(edge -> edge.getType() == FlowEdgeType.BRANCH)
        .collect(Collectors.toList());
  }


  public Optional<FlowNode> getStartFlowNode() {
    return ObjectUtils.defaultIfNull(getNodes(), Collections.<FlowNode>emptyList())
        .stream()
        .filter(StartNode.class::isInstance)
        .findFirst();
  }

  Map<String, FlowNode> getNodeMap() {
    if (nodeMap == null) {
      nodeMap = ObjectUtils.defaultIfNull(getNodes(), Collections.<FlowNode>emptyList())
          .stream()
          .collect(Collectors.toMap(
              FlowNode::getId,
              node -> node,
              (left, right) -> right,
              LinkedHashMap::new
          ));
    }
    return nodeMap;
  }

  Map<String, List<FlowEdgeV2>> getOutgoingEdgeMap() {
    if (outgoingEdgeMap == null) {
      outgoingEdgeMap = buildEdgeMap(true);
    }
    return outgoingEdgeMap;
  }

  Map<String, List<FlowEdgeV2>> getIncomingEdgeMap() {
    if (incomingEdgeMap == null) {
      incomingEdgeMap = buildEdgeMap(false);
    }
    return incomingEdgeMap;
  }

  private Map<String, List<FlowEdgeV2>> buildEdgeMap(boolean outgoing) {
    Comparator<FlowEdgeV2> comparator = Comparator
        .comparingInt(this::edgeTypeOrder)
        .thenComparing(edge -> edge.getOrder() == null ? Integer.MAX_VALUE : edge.getOrder())
        .thenComparing(edge -> edge.getId() == null ? "" : edge.getId());

    Map<String, List<FlowEdgeV2>> result = new LinkedHashMap<String, List<FlowEdgeV2>>();
    ObjectUtils.defaultIfNull(getEdges(), Collections.<FlowEdgeV2>emptyList())
        .stream()
        .sorted(comparator)
        .forEach(edge -> {
          String key = outgoing ? edge.getSourceId() : edge.getTargetId();
          result.computeIfAbsent(key, ignored -> new ArrayList<FlowEdgeV2>()).add(edge);
        });
    return result;
  }

  private int edgeTypeOrder(FlowEdgeV2 edge) {
    // Preserve legacy V1 fork ordering for compatibility helpers.
    if (edge.getType() == FlowEdgeType.BRANCH) {
      return 0;
    }
    if (edge.getType() == FlowEdgeType.SEQUENCE) {
      return 1;
    }
    return 2;
  }
}
