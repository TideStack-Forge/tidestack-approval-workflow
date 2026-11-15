package com.ouroboros.workflow.approval.testkit.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ouroboros.workflow.approval.definition.FlowDefinition;
import com.ouroboros.workflow.approval.testkit.scenario.SharedSemanticScenario;

public abstract class AbstractFlowDefinitionContractTest extends AbstractEngineContractTest {
  @Test
  void loadDefinitionReadsDefinitionIdentityAndFormKey() {
    FlowDefinition definition = adapter().loadDefinition(json(SharedSemanticScenario.SIMPLE_SINGLE));

    assertEquals("flow-smoke-single", definition.getId());
    assertEquals("单签共享场景", definition.getName());
    assertEquals("runtime-form", definition.getFormKey());
  }

  @Test
  void getFlowNodesFollowsStructuredBranchOrder() {
    assertEquals(
        Arrays.asList("start-a", "approve-a", "approve-c", "approve-b"),
        flowNodeIds(adapter().flowNodes(json(SharedSemanticScenario.FORK_BRANCH)))
    );
  }

  @Test
  void getUserSelectCandidateApprovalNodesFollowSelectedBranch() {
    Map<String, Object> context = new LinkedHashMap<String, Object>();
    context.put("amount", 1500);

    assertEquals(
        Arrays.asList("approve-a", "approve-c"),
        approvalNodeIds(adapter().candidateApprovalNodes(json(SharedSemanticScenario.FORK_BRANCH), context))
    );
  }
}
