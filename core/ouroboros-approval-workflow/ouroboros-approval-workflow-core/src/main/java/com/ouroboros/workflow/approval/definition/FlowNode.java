package com.ouroboros.workflow.approval.definition;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonTypeIdResolver;
import com.ouroboros.workflow.approval.definition.nodes.FlowNodeTypeIdResolver;

@JsonTypeInfo(use = JsonTypeInfo.Id.CUSTOM, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonTypeIdResolver(FlowNodeTypeIdResolver.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class FlowNode {
  String type;
  String name;
  String id;
  List<Candidate> candidates;
  List<NodeFormFieldPermission> permission;
  String next;
  Boolean notify;

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

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

  public List<Candidate> getCandidates() {
    return candidates;
  }

  public void setCandidates(List<Candidate> candidates) {
    this.candidates = candidates;
  }

  public List<NodeFormFieldPermission> getPermission() {
    return permission;
  }

  public void setPermission(List<NodeFormFieldPermission> permission) {
    this.permission = permission;
  }

  public String getNext() {
    return next;
  }

  public void setNext(String next) {
    this.next = next;
  }

  public Boolean getNotify() {
    return notify;
  }

  public void setNotify(Boolean notify) {
    this.notify = notify;
  }

  @JsonIgnore
  public boolean isNotifyEnabled() {
    return notify == null || notify;
  }
}
