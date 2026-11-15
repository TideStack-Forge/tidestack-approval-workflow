package com.ouroboros.workflow.approval.testkit.snapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class TranspiledModelSnapshot {
  private final String processId;
  private final String processName;
  private final List<NodeSnapshot> nodes;
  private final List<EdgeSnapshot> edges;

  public TranspiledModelSnapshot(String processId, String processName, List<NodeSnapshot> nodes, List<EdgeSnapshot> edges) {
    this.processId = processId;
    this.processName = processName;
    this.nodes = immutableList(nodes);
    this.edges = immutableList(edges);
  }

  public String getProcessId() {
    return processId;
  }

  public String getProcessName() {
    return processName;
  }

  public List<NodeSnapshot> getNodes() {
    return nodes;
  }

  public List<EdgeSnapshot> getEdges() {
    return edges;
  }

  @Override
  public boolean equals(Object object) {
    if (this == object) {
      return true;
    }
    if (!(object instanceof TranspiledModelSnapshot)) {
      return false;
    }
    TranspiledModelSnapshot that = (TranspiledModelSnapshot) object;
    return Objects.equals(processId, that.processId)
        && Objects.equals(processName, that.processName)
        && Objects.equals(nodes, that.nodes)
        && Objects.equals(edges, that.edges);
  }

  @Override
  public int hashCode() {
    return Objects.hash(processId, processName, nodes, edges);
  }

  private <T> List<T> immutableList(List<T> source) {
    ArrayList<T> copy = new ArrayList<T>();
    if (source != null) {
      copy.addAll(source);
    }
    return Collections.unmodifiableList(copy);
  }

  public static class NodeSnapshot {
    private final String id;
    private final String type;
    private final String name;
    private final String subType;
    private final Map<String, Object> properties;

    public NodeSnapshot(String id, String type, String name, String subType, Map<String, Object> properties) {
      this.id = id;
      this.type = type;
      this.name = name;
      this.subType = subType;
      this.properties = immutableMap(properties);
    }

    public String getId() {
      return id;
    }

    public String getType() {
      return type;
    }

    public String getName() {
      return name;
    }

    public String getSubType() {
      return subType;
    }

    public Map<String, Object> getProperties() {
      return properties;
    }

    @Override
    public boolean equals(Object object) {
      if (this == object) {
        return true;
      }
      if (!(object instanceof NodeSnapshot)) {
        return false;
      }
      NodeSnapshot that = (NodeSnapshot) object;
      return Objects.equals(id, that.id)
          && Objects.equals(type, that.type)
          && Objects.equals(name, that.name)
          && Objects.equals(subType, that.subType)
          && Objects.equals(properties, that.properties);
    }

    @Override
    public int hashCode() {
      return Objects.hash(id, type, name, subType, properties);
    }

    private Map<String, Object> immutableMap(Map<String, Object> source) {
      LinkedHashMap<String, Object> copy = new LinkedHashMap<String, Object>();
      if (source != null) {
        copy.putAll(source);
      }
      return Collections.unmodifiableMap(copy);
    }
  }

  public static class EdgeSnapshot {
    private final String id;
    private final String type;
    private final String sourceId;
    private final String targetId;
    private final String name;
    private final String conditionExpression;

    public EdgeSnapshot(String id, String type, String sourceId, String targetId, String name, String conditionExpression) {
      this.id = id;
      this.type = type;
      this.sourceId = sourceId;
      this.targetId = targetId;
      this.name = name;
      this.conditionExpression = conditionExpression;
    }

    public String getId() {
      return id;
    }

    public String getType() {
      return type;
    }

    public String getSourceId() {
      return sourceId;
    }

    public String getTargetId() {
      return targetId;
    }

    public String getName() {
      return name;
    }

    public String getConditionExpression() {
      return conditionExpression;
    }

    @Override
    public boolean equals(Object object) {
      if (this == object) {
        return true;
      }
      if (!(object instanceof EdgeSnapshot)) {
        return false;
      }
      EdgeSnapshot that = (EdgeSnapshot) object;
      return Objects.equals(id, that.id)
          && Objects.equals(type, that.type)
          && Objects.equals(sourceId, that.sourceId)
          && Objects.equals(targetId, that.targetId)
          && Objects.equals(name, that.name)
          && Objects.equals(conditionExpression, that.conditionExpression);
    }

    @Override
    public int hashCode() {
      return Objects.hash(id, type, sourceId, targetId, name, conditionExpression);
    }
  }
}
