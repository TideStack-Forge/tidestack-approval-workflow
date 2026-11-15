package com.ouroboros.workflow.approval.flowable.testkit;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ouroboros.workflow.approval.testkit.adapter.EngineCapability;
import org.junit.jupiter.api.Test;

class FlowableEngineCapabilitySetTest {
  @Test
  void defaultEnablesWorkflowEnhancementCapabilities() {
    assertSame(EngineCapability.CapabilityLevel.STABLE, FlowableEngineCapabilitySet.DEFAULT.taskTransferLevel());
    assertSame(EngineCapability.CapabilityLevel.STABLE, FlowableEngineCapabilitySet.DEFAULT.childSubflowParentMetadataLevel());
    assertSame(EngineCapability.CapabilityLevel.STABLE, FlowableEngineCapabilitySet.DEFAULT.taskTimeoutPolicyLevel());
    assertTrue(FlowableEngineCapabilitySet.DEFAULT.supportsTaskTransfer());
    assertTrue(FlowableEngineCapabilitySet.DEFAULT.supportsAddSign());
    assertTrue(FlowableEngineCapabilitySet.DEFAULT.supportsConfigurableRejectTarget());
    assertTrue(FlowableEngineCapabilitySet.DEFAULT.supportsFreeRejection());
    assertTrue(FlowableEngineCapabilitySet.DEFAULT.supportsFreeRecovery());
    assertTrue(FlowableEngineCapabilitySet.DEFAULT.supportsFreeApproval());
    assertTrue(FlowableEngineCapabilitySet.DEFAULT.supportsTaskTimeoutPolicy());
  }
}
