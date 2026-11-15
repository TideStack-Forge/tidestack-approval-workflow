package com.ouroboros.workflow.approval.testkit.assertion;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.Test;

import com.ouroboros.workflow.approval.testkit.snapshot.TranspiledModelSnapshot;

class SemanticSnapshotAssertionsTest {

  @Test
  void comparesSnapshotsByNormalizedSemanticContent() {
    TranspiledModelSnapshot left = new TranspiledModelSnapshot(
        "flow-a",
        "采购审批",
        Arrays.asList(
            new TranspiledModelSnapshot.NodeSnapshot(
                "start-a",
                "发起节点",
                "发起节点",
                null,
                Collections.<String, Object>emptyMap())),
        Collections.<TranspiledModelSnapshot.EdgeSnapshot>emptyList());

    TranspiledModelSnapshot right = new TranspiledModelSnapshot(
        "flow-a",
        "采购审批",
        Arrays.asList(
            new TranspiledModelSnapshot.NodeSnapshot(
                "start-a",
                "发起节点",
                "发起节点",
                null,
                Collections.<String, Object>emptyMap())),
        Collections.<TranspiledModelSnapshot.EdgeSnapshot>emptyList());

    assertDoesNotThrow(() -> SemanticSnapshotAssertions.assertSameModel(left, right));
  }
}
