package com.ouroboros.workflow.approval.testkit.contract;

import org.junit.jupiter.api.Test;

import com.ouroboros.workflow.approval.testkit.assertion.SemanticSnapshotAssertions;
import com.ouroboros.workflow.approval.testkit.scenario.SharedSemanticScenario;

public abstract class AbstractTranspilerContractTest extends AbstractEngineContractTest {
  @Test
  void transpileSimpleSingleScenarioToNormalizedSnapshot() {
    SemanticSnapshotAssertions.assertSameModel(
        expectedSnapshot(SharedSemanticScenario.SIMPLE_SINGLE),
        adapter().transpile(json(SharedSemanticScenario.SIMPLE_SINGLE))
    );
  }

  @Test
  void transpileParallelMultipleScenarioToNormalizedSnapshot() {
    SemanticSnapshotAssertions.assertSameModel(
        expectedSnapshot(SharedSemanticScenario.SIMPLE_MULTIPLE_PARALLEL),
        adapter().transpile(json(SharedSemanticScenario.SIMPLE_MULTIPLE_PARALLEL))
    );
  }

  @Test
  void transpileSubFlowScenarioToNormalizedSnapshot() {
    SemanticSnapshotAssertions.assertSameModel(
        expectedSnapshot(SharedSemanticScenario.SUBFLOW_PARENT),
        adapter().transpile(json(SharedSemanticScenario.SUBFLOW_PARENT))
    );
  }
}
