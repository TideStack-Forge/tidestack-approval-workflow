package com.ouroboros.workflow.approval.flowable.contract;

import com.ouroboros.workflow.approval.flowable.testkit.FlowableEngineCapabilitySet;
import com.ouroboros.workflow.approval.flowable.testkit.FlowableEngineTestAdapter;
import com.ouroboros.workflow.approval.testkit.adapter.EngineCapability;
import com.ouroboros.workflow.approval.testkit.adapter.EngineTestAdapter;
import com.ouroboros.workflow.approval.testkit.contract.AbstractTaskEventContractTest;

class FlowableTaskEventContractTest extends AbstractTaskEventContractTest {
  private final FlowableEngineTestAdapter adapter = new FlowableEngineTestAdapter();

  @Override
  protected EngineTestAdapter adapter() {
    return adapter;
  }

  @Override
  protected EngineCapability capabilities() {
    return FlowableEngineCapabilitySet.DEFAULT;
  }
}
