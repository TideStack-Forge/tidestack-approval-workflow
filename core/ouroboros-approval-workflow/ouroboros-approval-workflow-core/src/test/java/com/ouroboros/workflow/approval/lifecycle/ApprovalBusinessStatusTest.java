package com.ouroboros.workflow.approval.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ApprovalBusinessStatusTest {

  @Test
  void exposesStableStatusMapKeysAndTerminality() {
    assertThat(ApprovalBusinessStatus.DRAFT.statusMapKey()).isEqualTo("draft");
    assertThat(ApprovalBusinessStatus.SUBMITTED.statusMapKey()).isEqualTo("submitted");
    assertThat(ApprovalBusinessStatus.PENDING.statusMapKey()).isEqualTo("pending");
    assertThat(ApprovalBusinessStatus.PROCESSING.statusMapKey()).isEqualTo("processing");
    assertThat(ApprovalBusinessStatus.SUSPENDED.statusMapKey()).isEqualTo("suspended");
    assertThat(ApprovalBusinessStatus.WITHDRAWN.statusMapKey()).isEqualTo("withdrawn");
    assertThat(ApprovalBusinessStatus.APPROVED.statusMapKey()).isEqualTo("approved");
    assertThat(ApprovalBusinessStatus.VETO.statusMapKey()).isEqualTo("veto");
    assertThat(ApprovalBusinessStatus.CANCELED.statusMapKey()).isEqualTo("canceled");
    assertThat(ApprovalBusinessStatus.TERMINATED.statusMapKey()).isEqualTo("terminated");

    assertThat(ApprovalBusinessStatus.values())
        .filteredOn(ApprovalBusinessStatus::isTerminal)
        .containsExactlyInAnyOrder(
            ApprovalBusinessStatus.APPROVED,
            ApprovalBusinessStatus.VETO,
            ApprovalBusinessStatus.CANCELED,
            ApprovalBusinessStatus.TERMINATED);
  }

  @Test
  void allowsOnlyLifecycleTransitionsDefinedByThePublicContract() {
    assertThat(ApprovalBusinessStatus.DRAFT.canTransitionTo(ApprovalBusinessStatus.SUBMITTED)).isTrue();
    assertThat(ApprovalBusinessStatus.SUBMITTED.canTransitionTo(ApprovalBusinessStatus.DRAFT)).isTrue();
    assertThat(ApprovalBusinessStatus.SUBMITTED.canTransitionTo(ApprovalBusinessStatus.PENDING)).isTrue();
    assertThat(ApprovalBusinessStatus.PENDING.canTransitionTo(ApprovalBusinessStatus.PROCESSING)).isTrue();
    assertThat(ApprovalBusinessStatus.PENDING.canTransitionTo(ApprovalBusinessStatus.DRAFT)).isTrue();
    assertThat(ApprovalBusinessStatus.PENDING.canTransitionTo(ApprovalBusinessStatus.CANCELED)).isTrue();
    assertThat(ApprovalBusinessStatus.PROCESSING.canTransitionTo(ApprovalBusinessStatus.SUSPENDED)).isTrue();
    assertThat(ApprovalBusinessStatus.PROCESSING.canTransitionTo(ApprovalBusinessStatus.WITHDRAWN)).isTrue();
    assertThat(ApprovalBusinessStatus.SUSPENDED.canTransitionTo(ApprovalBusinessStatus.PROCESSING)).isTrue();
    assertThat(ApprovalBusinessStatus.WITHDRAWN.canTransitionTo(ApprovalBusinessStatus.DRAFT)).isTrue();
    assertThat(ApprovalBusinessStatus.WITHDRAWN.canTransitionTo(ApprovalBusinessStatus.SUBMITTED)).isTrue();
    assertThat(ApprovalBusinessStatus.WITHDRAWN.canTransitionTo(ApprovalBusinessStatus.PENDING)).isTrue();

    assertThat(ApprovalBusinessStatus.APPROVED.canTransitionTo(ApprovalBusinessStatus.DRAFT)).isFalse();
    assertThat(ApprovalBusinessStatus.VETO.canTransitionTo(ApprovalBusinessStatus.PROCESSING)).isFalse();
    assertThat(ApprovalBusinessStatus.CANCELED.canTransitionTo(ApprovalBusinessStatus.PROCESSING)).isFalse();
    assertThat(ApprovalBusinessStatus.TERMINATED.canTransitionTo(ApprovalBusinessStatus.WITHDRAWN)).isFalse();
    assertThat(ApprovalBusinessStatus.PROCESSING.canTransitionTo(ApprovalBusinessStatus.DRAFT)).isFalse();
    assertThat(ApprovalBusinessStatus.SUSPENDED.canTransitionTo(ApprovalBusinessStatus.APPROVED)).isFalse();
    assertThat(ApprovalBusinessStatus.DRAFT.canTransitionTo(null)).isFalse();

    assertThat(ApprovalBusinessStatus.PROCESSING.canTransitionTo(ApprovalBusinessStatus.PROCESSING)).isTrue();
    assertThat(ApprovalBusinessStatus.APPROVED.canTransitionTo(ApprovalBusinessStatus.APPROVED)).isTrue();
  }
}
