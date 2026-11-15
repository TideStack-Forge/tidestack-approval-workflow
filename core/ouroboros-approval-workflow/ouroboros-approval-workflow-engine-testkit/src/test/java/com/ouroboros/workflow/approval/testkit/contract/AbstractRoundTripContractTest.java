package com.ouroboros.workflow.approval.testkit.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.Test;

import com.ouroboros.workflow.approval.testkit.assertion.SemanticSnapshotAssertions;
import com.ouroboros.workflow.approval.testkit.scenario.SharedSemanticScenario;
import com.ouroboros.workflow.approval.testkit.snapshot.RoundTripSnapshot;

public abstract class AbstractRoundTripContractTest extends AbstractEngineContractTest {
  @Test
  void roundTripRecoversParseableDefinitionJson() {
    assumeTrue(capabilities().supportsSemanticRoundTrip());

    RoundTripSnapshot snapshot = adapter().roundTrip(json(SharedSemanticScenario.SIMPLE_SINGLE));
    var recovered = structuredDefinition(snapshot.getRawJsonRecovered());
    var original = structuredDefinition(SharedSemanticScenario.SIMPLE_SINGLE);

    assertNotNull(snapshot.getRawJsonRecovered());
    assertFalse(snapshot.getRawJsonRecovered().trim().isEmpty());
    assertEquals(original.getId(), recovered.getId());
    assertEquals(original.getName(), recovered.getName());
    assertEquals(original.getFormKey(), recovered.getFormKey());
  }

  @Test
  void roundTripKeepsNormalizedDefinitionAndSemanticSnapshotStable() {
    assumeTrue(capabilities().supportsSemanticRoundTrip());

    RoundTripSnapshot snapshot = adapter().roundTrip(json(SharedSemanticScenario.SUBFLOW_PARENT));

    assertEquals(
        normalizedDefinitionObject(SharedSemanticScenario.SUBFLOW_PARENT),
        normalizedJsonObject(snapshot.getNormalizedDefinitionJson())
    );
    SemanticSnapshotAssertions.assertSameModel(
        expectedSnapshot(SharedSemanticScenario.SUBFLOW_PARENT),
        snapshot.getTranspiledModelSnapshot()
    );
  }
}
