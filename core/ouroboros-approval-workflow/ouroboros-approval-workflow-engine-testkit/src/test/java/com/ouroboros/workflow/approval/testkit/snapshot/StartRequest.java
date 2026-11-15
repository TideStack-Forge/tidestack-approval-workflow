package com.ouroboros.workflow.approval.testkit.snapshot;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class StartRequest {
  private final String starter;
  private final Map<String, List<String>> selectedAssignees;
  private final Map<String, Object> instanceVariables;
  private final Map<String, Object> formData;

  public StartRequest(
      String starter,
      Map<String, List<String>> selectedAssignees,
      Map<String, Object> instanceVariables,
      Map<String, Object> formData) {
    this.starter = starter;
    this.selectedAssignees = unmodifiableCopy(selectedAssignees);
    this.instanceVariables = unmodifiableObjectCopy(instanceVariables);
    this.formData = unmodifiableObjectCopy(formData);
  }

  public String getStarter() {
    return starter;
  }

  public Map<String, List<String>> getSelectedAssignees() {
    return selectedAssignees;
  }

  public Map<String, Object> getInstanceVariables() {
    return instanceVariables;
  }

  public Map<String, Object> getFormData() {
    return formData;
  }

  private Map<String, List<String>> unmodifiableCopy(Map<String, List<String>> source) {
    LinkedHashMap<String, List<String>> copy = new LinkedHashMap<String, List<String>>();
    if (source != null) {
      copy.putAll(source);
    }
    return Collections.unmodifiableMap(copy);
  }

  private Map<String, Object> unmodifiableObjectCopy(Map<String, Object> source) {
    LinkedHashMap<String, Object> copy = new LinkedHashMap<String, Object>();
    if (source != null) {
      copy.putAll(source);
    }
    return Collections.unmodifiableMap(copy);
  }
}
