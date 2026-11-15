package com.ouroboros.workflow.approval.testkit.adapter;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EngineCapabilityTest {
  @Test
  void allEnabledIncludesWorkflowEnhancementCapabilities() {
    EngineCapability capability = EngineCapability.allEnabled();

    assertSame(EngineCapability.CapabilityLevel.STABLE, capability.semanticRoundTripLevel());
    assertTrue(capability.supportsChildSubflowParentMetadata());
    assertTrue(capability.supportsTaskTransfer());
    assertTrue(capability.supportsAddSign());
    assertTrue(capability.supportsConfigurableRejectTarget());
    assertTrue(capability.supportsFreeRejection());
    assertTrue(capability.supportsFreeRecovery());
    assertTrue(capability.supportsFreeApproval());
    assertTrue(capability.supportsTaskTimeoutPolicy());
  }

  @Test
  void noneEnabledDisablesWorkflowEnhancementCapabilities() {
    EngineCapability capability = EngineCapability.noneEnabled();

    assertSame(EngineCapability.CapabilityLevel.DEFERRED, capability.semanticRoundTripLevel());
    assertFalse(capability.supportsChildSubflowParentMetadata());
    assertFalse(capability.supportsTaskTransfer());
    assertFalse(capability.supportsAddSign());
    assertFalse(capability.supportsConfigurableRejectTarget());
    assertFalse(capability.supportsFreeRejection());
    assertFalse(capability.supportsFreeRecovery());
    assertFalse(capability.supportsFreeApproval());
    assertFalse(capability.supportsTaskTimeoutPolicy());
  }

  @Test
  void compatibleLevelStillParticipatesInSharedContracts() {
    EngineCapability capability = new EngineCapability.DefaultEngineCapability(
        EngineCapability.CapabilityLevel.COMPATIBLE,
        EngineCapability.CapabilityLevel.COMPATIBLE,
        EngineCapability.CapabilityLevel.COMPATIBLE,
        EngineCapability.CapabilityLevel.COMPATIBLE,
        EngineCapability.CapabilityLevel.COMPATIBLE,
        EngineCapability.CapabilityLevel.COMPATIBLE,
        EngineCapability.CapabilityLevel.COMPATIBLE,
        EngineCapability.CapabilityLevel.COMPATIBLE,
        EngineCapability.CapabilityLevel.COMPATIBLE,
        EngineCapability.CapabilityLevel.COMPATIBLE,
        EngineCapability.CapabilityLevel.DEFERRED,
        EngineCapability.CapabilityLevel.DEFERRED,
        EngineCapability.CapabilityLevel.DEFERRED,
        EngineCapability.CapabilityLevel.DEFERRED,
        EngineCapability.CapabilityLevel.DEFERRED,
        EngineCapability.CapabilityLevel.DEFERRED,
        EngineCapability.CapabilityLevel.COMPATIBLE,
        EngineCapability.CapabilityLevel.DEFERRED
    );

    assertSame(EngineCapability.CapabilityLevel.COMPATIBLE, capability.taskServiceRuntimeLevel());
    assertSame(EngineCapability.CapabilityLevel.COMPATIBLE, capability.childSubflowParentMetadataLevel());
    assertTrue(capability.supportsTaskServiceRuntime());
    assertTrue(capability.supportsChildSubflowParentMetadata());
    assertFalse(capability.supportsTaskTransfer());
    assertFalse(capability.supportsTaskTimeoutPolicy());
  }
}
