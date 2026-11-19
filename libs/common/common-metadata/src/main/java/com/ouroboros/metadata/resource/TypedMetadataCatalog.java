package com.ouroboros.metadata.resource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public class TypedMetadataCatalog {
  private final MetadataSource metadataSource;

  public TypedMetadataCatalog(MetadataSource metadataSource) {
    this.metadataSource = Objects.requireNonNull(metadataSource, "metadataSource");
  }

  public <T> MetadataLoadResult<T> load(TypedMetadataSpec<T> spec) {
    Objects.requireNonNull(spec, "spec");
    Map<String, T> values = new TreeMap<>(String::compareToIgnoreCase);
    List<MetadataSourceTrace> traces = new ArrayList<>();
    List<MetadataDiagnostic> diagnostics = new ArrayList<>();
    Map<String, Integer> acceptedTraceIndexes = new HashMap<>();
    var duplicateOriginalsReported = new HashSet<String>();

    for (MetadataResource resource : metadataSource.findResources(spec)) {
      if (!spec.resourceNamePredicate().test(resource.resourceName())) {
        continue;
      }
      readResource(spec, resource, diagnostics)
          .ifPresent(model -> acceptResource(spec, resource, model, values, traces, diagnostics,
              acceptedTraceIndexes, duplicateOriginalsReported));
    }

    MetadataLoadResult<T> result = new MetadataLoadResult<>(values, traces, diagnostics);
    if (result.hasErrors()) {
      result.throwIfErrors();
    }
    return result;
  }

  private <T> java.util.Optional<T> readResource(TypedMetadataSpec<T> spec, MetadataResource resource,
      List<MetadataDiagnostic> diagnostics) {
    Throwable lastFailure = null;
    for (TypedMetadataSpec.MetadataResourceParser<? extends T> parser : spec.parsers()) {
      var result = parser.parse(resource);
      if (result.isSuccess()) {
        return java.util.Optional.of(result.get());
      }
      lastFailure = result.getCause();
    }
    String message = spec.metadataType().getSimpleName() + " 解析失败"
        + (lastFailure != null && lastFailure.getMessage() != null ? ": " + lastFailure.getMessage() : "");
    var diagnostic = spec.parseFailurePolicy() == TypedMetadataSpec.ParseFailurePolicy.ERROR
        ? MetadataDiagnostic.error(resource.resourceName(), message, lastFailure)
        : MetadataDiagnostic.warn(resource.resourceName(), message, lastFailure);
    diagnostics.add(diagnostic);
    return java.util.Optional.empty();
  }

  private <T> void acceptResource(TypedMetadataSpec<T> spec, MetadataResource resource, T model,
      Map<String, T> values, List<MetadataSourceTrace> traces, List<MetadataDiagnostic> diagnostics,
      Map<String, Integer> acceptedTraceIndexes, HashSet<String> duplicateOriginalsReported) {
    String key = spec.keyExtractor().apply(model);
    if (key == null || key.isBlank()) {
      handleBlankKey(spec, resource, traces, diagnostics);
      return;
    }

    if (!values.containsKey(key)) {
      values.put(key, model);
      acceptedTraceIndexes.put(key, traces.size());
      traces.add(MetadataSourceTrace.accepted(resource.resourceName(), key));
      return;
    }

    if (spec.mergeStrategy() == TypedMetadataSpec.MergeStrategy.FIRST_WINS) {
      var diagnostic = MetadataDiagnostic.warn(resource.resourceName(), "重复元数据键已忽略: " + key);
      diagnostics.add(diagnostic);
      traces.add(MetadataSourceTrace.skipped(resource.resourceName(), key, diagnostic));
      return;
    }

    if (spec.mergeStrategy() == TypedMetadataSpec.MergeStrategy.FAIL_ON_DUPLICATE) {
      addDuplicateDiagnostics(resource, key, traces, diagnostics, acceptedTraceIndexes, duplicateOriginalsReported);
      return;
    }

    Integer previousTraceIndex = acceptedTraceIndexes.get(key);
    if (previousTraceIndex != null) {
      traces.set(previousTraceIndex, traces.get(previousTraceIndex).overridden());
    }
    values.put(key, model);
    acceptedTraceIndexes.put(key, traces.size());
    traces.add(MetadataSourceTrace.accepted(resource.resourceName(), key));
  }

  private <T> void handleBlankKey(TypedMetadataSpec<T> spec, MetadataResource resource,
      List<MetadataSourceTrace> traces, List<MetadataDiagnostic> diagnostics) {
    String message = "元数据键为空，已跳过";
    MetadataDiagnostic diagnostic = spec.blankKeyPolicy() == TypedMetadataSpec.BlankKeyPolicy.ERROR
        ? MetadataDiagnostic.error(resource.resourceName(), message)
        : MetadataDiagnostic.warn(resource.resourceName(), message);
    diagnostics.add(diagnostic);
    traces.add(MetadataSourceTrace.skipped(resource.resourceName(), null, diagnostic));
  }

  private void addDuplicateDiagnostics(MetadataResource resource, String key, List<MetadataSourceTrace> traces,
      List<MetadataDiagnostic> diagnostics, Map<String, Integer> acceptedTraceIndexes, HashSet<String> duplicateOriginalsReported) {
    Integer previousTraceIndex = acceptedTraceIndexes.get(key);
    if (previousTraceIndex != null && duplicateOriginalsReported.add(key)) {
      MetadataSourceTrace previousTrace = traces.get(previousTraceIndex);
      diagnostics.add(MetadataDiagnostic.error(previousTrace.resourceName(), "重复元数据键: " + key));
    }
    var diagnostic = MetadataDiagnostic.error(resource.resourceName(), "重复元数据键: " + key);
    diagnostics.add(diagnostic);
    traces.add(MetadataSourceTrace.skipped(resource.resourceName(), key, diagnostic));
  }
}
