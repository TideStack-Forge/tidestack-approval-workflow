package com.ouroboros.metadata.resource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public record MetadataLoadResult<T>(Map<String, T> values,
                                    List<MetadataSourceTrace> traces,
                                    List<MetadataDiagnostic> diagnostics) {
  public MetadataLoadResult {
    var valuesCopy = new TreeMap<String, T>(String::compareToIgnoreCase);
    valuesCopy.putAll(values);
    values = Collections.unmodifiableMap(valuesCopy);
    traces = Collections.unmodifiableList(new ArrayList<>(traces));
    diagnostics = Collections.unmodifiableList(new ArrayList<>(diagnostics));
  }

  public boolean hasErrors() {
    return diagnostics.stream().anyMatch(diagnostic -> diagnostic.level() == MetadataDiagnostic.Level.ERROR);
  }

  public void throwIfErrors() {
    if (!hasErrors()) {
      return;
    }
    throw new IllegalStateException(diagnostics.stream()
        .filter(diagnostic -> diagnostic.level() == MetadataDiagnostic.Level.ERROR)
        .map(diagnostic -> diagnostic.resourceName() + ": " + diagnostic.message())
        .reduce((left, right) -> left + "; " + right)
        .orElse("元数据加载失败"));
  }
}
