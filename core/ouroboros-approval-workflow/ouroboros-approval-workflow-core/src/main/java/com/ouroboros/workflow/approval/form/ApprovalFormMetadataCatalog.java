package com.ouroboros.workflow.approval.form;

import java.util.List;
import java.util.TreeMap;

import com.ouroboros.exception.InternalException;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.metadata.resource.MetadataResource;
import com.ouroboros.metadata.resource.MetadataSource;
import com.ouroboros.metadata.resource.TypedMetadataCatalog;
import com.ouroboros.metadata.resource.TypedMetadataSpec;

import io.vavr.control.Try;

/** Uses the standard typed metadata loader; application resources replace entire JAR definitions. */
public final class ApprovalFormMetadataCatalog {
  private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(ApprovalFormMetadataCatalog.class);
  public static final String DIRECTORY = "approval-form";
  public static final String SUFFIX = ".approval-form.json";

  private ApprovalFormMetadataCatalog() {}

  public static List<ApprovalFormDefinition> load(MetadataSource jars, MetadataSource application) {
    var definitions = new TreeMap<String, ApprovalFormDefinition>(String::compareToIgnoreCase);
    definitions.putAll(loadLayer(jars, TypedMetadataSpec.MergeStrategy.LAST_WINS));
    definitions.putAll(loadLayer(application, TypedMetadataSpec.MergeStrategy.FAIL_ON_DUPLICATE));
    return List.copyOf(definitions.values());
  }

  private static java.util.Map<String, ApprovalFormDefinition> loadLayer(
      MetadataSource source, TypedMetadataSpec.MergeStrategy strategy) {
    var spec = TypedMetadataSpec.<ApprovalFormDefinition>builder(ApprovalFormDefinition.class)
        .typedSuffix(SUFFIX)
        .resourceParser(resource -> Try.of(() -> read(resource)))
        .keyExtractor(ApprovalFormDefinition::name)
        .mergeStrategy(strategy)
        .parseFailurePolicy(TypedMetadataSpec.ParseFailurePolicy.ERROR)
        .blankKeyPolicy(TypedMetadataSpec.BlankKeyPolicy.ERROR)
        .build();
    try {
      var result = new TypedMetadataCatalog(source).load(spec);
      result.traces().forEach(trace -> logger.debug("自定义审批表单资源 {} key={} status={}",
          trace.resourceName(), trace.key(), trace.status()));
      return result.values();
    } catch (IllegalStateException e) {
      throw new InternalException("加载自定义审批表单失败: " + e.getMessage(), e);
    }
  }

  private static ApprovalFormDefinition read(MetadataResource resource) {
    ApprovalFormDefinition definition = ApprovalFormDefinition.read(resource.content());
    String fileName = resource.resourceName().substring(resource.resourceName().lastIndexOf('/') + 1);
    if (!fileName.equals(definition.name() + SUFFIX)) {
      throw new BusinessException("自定义表单 name 与文件名不一致: " + resource.resourceName());
    }
    return definition;
  }
}
