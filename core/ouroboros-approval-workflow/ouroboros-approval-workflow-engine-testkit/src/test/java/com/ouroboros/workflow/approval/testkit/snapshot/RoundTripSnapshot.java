package com.ouroboros.workflow.approval.testkit.snapshot;

import java.util.Objects;

public class RoundTripSnapshot {
  private final String rawJsonRecovered;
  private final String normalizedDefinitionJson;
  private final TranspiledModelSnapshot transpiledModelSnapshot;

  public RoundTripSnapshot(String rawJsonRecovered, String normalizedDefinitionJson,
      TranspiledModelSnapshot transpiledModelSnapshot) {
    this.rawJsonRecovered = rawJsonRecovered;
    this.normalizedDefinitionJson = normalizedDefinitionJson;
    this.transpiledModelSnapshot = transpiledModelSnapshot;
  }

  public String getRawJsonRecovered() {
    return rawJsonRecovered;
  }

  public String getNormalizedDefinitionJson() {
    return normalizedDefinitionJson;
  }

  public TranspiledModelSnapshot getTranspiledModelSnapshot() {
    return transpiledModelSnapshot;
  }

  @Override
  public boolean equals(Object object) {
    if (this == object) {
      return true;
    }
    if (!(object instanceof RoundTripSnapshot)) {
      return false;
    }
    RoundTripSnapshot that = (RoundTripSnapshot) object;
    return Objects.equals(rawJsonRecovered, that.rawJsonRecovered)
        && Objects.equals(normalizedDefinitionJson, that.normalizedDefinitionJson)
        && Objects.equals(transpiledModelSnapshot, that.transpiledModelSnapshot);
  }

  @Override
  public int hashCode() {
    return Objects.hash(rawJsonRecovered, normalizedDefinitionJson, transpiledModelSnapshot);
  }
}
