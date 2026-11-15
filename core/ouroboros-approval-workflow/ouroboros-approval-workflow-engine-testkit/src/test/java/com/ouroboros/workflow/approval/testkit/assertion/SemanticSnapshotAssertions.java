package com.ouroboros.workflow.approval.testkit.assertion;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.ouroboros.workflow.approval.testkit.snapshot.RoundTripSnapshot;
import com.ouroboros.workflow.approval.testkit.snapshot.TranspiledModelSnapshot;

// Semantic snapshots compare normalized approval semantics instead of engine-private object graphs.
public final class SemanticSnapshotAssertions {
  private SemanticSnapshotAssertions() {
  }

  public static void assertSameModel(TranspiledModelSnapshot expected, TranspiledModelSnapshot actual) {
    assertEquals(expected, actual, "Transpiled model snapshots should be semantically identical");
  }

  public static void assertSameRoundTrip(RoundTripSnapshot expected, RoundTripSnapshot actual) {
    assertEquals(expected, actual, "Round-trip snapshots should be semantically identical");
  }
}
