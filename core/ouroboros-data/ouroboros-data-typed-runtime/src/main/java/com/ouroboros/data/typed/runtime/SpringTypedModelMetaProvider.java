package com.ouroboros.data.typed.runtime;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import jakarta.annotation.Priority;

import com.ouroboros.data.annotation.DataModelScan;
import com.ouroboros.data.model.DataModelMeta;
import com.ouroboros.data.model.DataModelMetaProvider;

/**
 * Spring类型数据模型注册器
 * 主要用于扫描@DataModelScan注解包下标记@Model注解类
 *
 * @author liansz
 */
@Priority(10)
public class SpringTypedModelMetaProvider implements DataModelMetaProvider {
  private final Map<String, DataModelMeta> modelMetaMap = new HashMap<>();
  private Set<Class<?>> dataModelScanClasses = Set.of();

  private synchronized void refreshIfNeeded() {
    Set<Class<?>> scanAnnotationClasses = DataModelScanRegistry.getDataModelScanAnnotationClassSnapshot();
    if (dataModelScanClasses.equals(scanAnnotationClasses)) {
      return;
    }

    modelMetaMap.clear();
    // 遍历注解，获取注解值，扫描包下的所有类
    for (Class<?> scanAnnotation : scanAnnotationClasses) {
      DataModelScan dataModelScan = scanAnnotation.getAnnotation(DataModelScan.class);
      String[] basePackages = dataModelScan.value();
      if (basePackages.length == 0) {
        basePackages = new String[]{scanAnnotation.getPackage().getName()};
      }

      // 遍历所有类，判断是否有@Model注解，如果有，则将类转为数据模型元数据，放入registry
      for (String basePackage : basePackages) {
        modelMetaMap.putAll(TypedModelMetaHelper.scanModelMate(basePackage));
      }
    }
    dataModelScanClasses = scanAnnotationClasses;
  }

  @Override
  public Optional<DataModelMeta> getDataModelMeta(String name) {
    refreshIfNeeded();
    return Optional.ofNullable(modelMetaMap.get(name));
  }

  @Override
  public Map<String, DataModelMeta> getDataModelMetaMap() {
    refreshIfNeeded();
    return modelMetaMap;
  }
}
