package com.ouroboros.workflow.approval.testkit.fixture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.ouroboros.workflow.approval.testkit.scenario.SharedSemanticScenario;

class SharedSemanticScenarioFixturesTest {

  @Test
  void loadsSimpleSingleAndSubflowScenariosByLogicalName() {
    SharedSemanticScenarioFixtures fixtures = new SharedSemanticScenarioFixtures();

    assertTrue(fixtures.json(SharedSemanticScenario.SIMPLE_SINGLE).contains("\"id\": \"flow-smoke-single\""));
    assertTrue(fixtures.json(SharedSemanticScenario.SUBFLOW_PARENT).contains("\"type\": \"子流程节点\""));
  }
}
