package com.ouroboros.workflow.approval.testkit.selfproof;

import com.ouroboros.workflow.approval.testkit.adapter.EngineCapability;
import com.ouroboros.workflow.approval.testkit.adapter.EngineTestAdapter;
import com.ouroboros.workflow.approval.testkit.contract.AbstractMultipleApprovalRuntimeContractTest;

class SelfProofMultipleApprovalRuntimeContractTest extends AbstractMultipleApprovalRuntimeContractTest {
  private final SelfProofEngineTestAdapter adapter = new SelfProofEngineTestAdapter();

  @Override
  protected EngineTestAdapter adapter() {
    return adapter;
  }

  @Override
  protected EngineCapability capabilities() {
    return EngineCapability.allEnabled();
  }
}
