package com.ouroboros.workflow.approval.testkit.fixture;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.ouroboros.workflow.approval.testkit.scenario.SharedSemanticScenario;
import com.ouroboros.workflow.approval.testkit.snapshot.StartRequest;
import com.ouroboros.workflow.approval.testkit.support.ScenarioResourceLoader;

// Shared fixture loader keeps scenario resources and normalized start requests inside engine-testkit.
public class SharedSemanticScenarioFixtures {
  private final ScenarioResourceLoader resourceLoader = new ScenarioResourceLoader();

  public String json(SharedSemanticScenario scenario) {
    return resourceLoader.load(scenario.getResourcePath());
  }

  public Map<String, List<String>> selectedAssignees(SharedSemanticScenario scenario) {
    switch (scenario) {
      case SIMPLE_SINGLE:
        return candidateMap("approve-a", Arrays.asList("user-a"));
      case SIMPLE_HANDLE:
        return candidateMap("handle-a", Arrays.asList("user-a"));
      case SIMPLE_MULTIPLE_PARALLEL:
      case SIMPLE_MULTIPLE_SEQUENTIAL:
        return candidateMap("multiapproval", Arrays.asList("user-a", "user-b", "user-c"));
      case PARALLEL_DOUBLE_MULTIPLE:
        LinkedHashMap<String, List<String>> parallelDoubleMultiple = new LinkedHashMap<String, List<String>>();
        parallelDoubleMultiple.put("multi-a", Arrays.asList("user-a", "user-b"));
        parallelDoubleMultiple.put("multi-b", Arrays.asList("user-c", "user-d"));
        return Collections.unmodifiableMap(parallelDoubleMultiple);
      case SUBFLOW_PARENT:
        LinkedHashMap<String, List<String>> subflowParent = new LinkedHashMap<String, List<String>>();
        subflowParent.put("approve-a", Arrays.asList("user-a"));
        subflowParent.put("multi-a", Arrays.asList("user-b", "user-c"));
        subflowParent.put("child-approve", Arrays.asList("user-d"));
        return Collections.unmodifiableMap(subflowParent);
      case SUBFLOW_PARENT_NO_TASK_CHILD:
        LinkedHashMap<String, List<String>> subflowParentNoTaskChild = new LinkedHashMap<String, List<String>>();
        subflowParentNoTaskChild.put("approve-a", Arrays.asList("user-a"));
        subflowParentNoTaskChild.put("multi-a", Arrays.asList("user-b", "user-c"));
        return Collections.unmodifiableMap(subflowParentNoTaskChild);
      case SUBFLOW_CHILD:
        return candidateMap("child-approve", Arrays.asList("user-d"));
      default:
        return Collections.emptyMap();
    }
  }

  public StartRequest startRequest(SharedSemanticScenario scenario) {
    return new StartRequest("starter-1", selectedAssignees(scenario), Collections.<String, Object>emptyMap(),
        Collections.<String, Object>emptyMap());
  }

  private Map<String, List<String>> candidateMap(String nodeId, List<String> users) {
    LinkedHashMap<String, List<String>> candidates = new LinkedHashMap<String, List<String>>();
    candidates.put(nodeId, users);
    return Collections.unmodifiableMap(candidates);
  }
}
