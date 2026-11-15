package com.ouroboros.workflow.approval.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ouroboros.data.model.DataModelMeta;
import com.ouroboros.data.typed.runtime.TypedModelMetaHelper;

/** Persistence metadata contract for the public approval case ledger. */
class ApprovalCaseMetadataTest {

  @Test
  void approvalCaseIsDiscoverableWithStableRouteConstraint() {
    Map<String, DataModelMeta> metas = TypedModelMetaHelper.scanModelMate(
        "com.ouroboros.workflow.approval.lifecycle");

    DataModelMeta meta = metas.get("ApprovalCase");
    assertThat(meta).isNotNull();
    assertThat(meta.getRawName()).isEqualTo("t_approval_workflow_case");
    assertThat(meta.getUniqueConstraints())
        .anySatisfy(constraint -> {
          assertThat(constraint.getName()).isEqualTo("uk_awf_case_route");
          assertThat(constraint.getFields()).containsExactly("caseRouteKey");
        });
  }
}
