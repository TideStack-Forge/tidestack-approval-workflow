package com.ouroboros.data.typed.runtime;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

import com.ouroboros.data.annotation.Model;
import com.ouroboros.data.exception.ModelMetadataException;
import com.ouroboros.data.model.TypedAnnotationDecoratorContext;

class DefaultTypedAnnotationDecoratorContext implements TypedAnnotationDecoratorContext {
  private final String modelName;
  private final Set<Class<?>> scannedModelClasses;
  private final Map<String, String> descriptorOwners = new HashMap<>();

  DefaultTypedAnnotationDecoratorContext(String modelName, Set<Class<?>> scannedModelClasses) {
    this.modelName = Objects.requireNonNull(modelName, "modelName");
    this.scannedModelClasses = Set.copyOf(scannedModelClasses);
  }

  @Override
  public String modelName() {
    return modelName;
  }

  @Override
  public String resolveModelName(Class<?> modelClass) {
    if (modelClass == null || Void.class.equals(modelClass)) {
      throw metadataException("目标模型 class 不能为空");
    }
    Model modelAnnotation = modelClass.getAnnotation(Model.class);
    if (modelAnnotation == null) {
      throw metadataException("目标模型 class 必须标注 @Model: " + modelClass.getName());
    }
    if (!scannedModelClasses.contains(modelClass)) {
      throw metadataException("目标模型 class 必须在当前 typed model 扫描结果中: " + modelClass.getName());
    }
    return StringUtils.isNotBlank(modelAnnotation.fullName())
        ? modelAnnotation.fullName()
        : modelClass.getSimpleName();
  }

  @Override
  public void claimPluginDescriptor(String pluginName, String ownerAnnotation) {
    if (StringUtils.isBlank(pluginName)) {
      throw metadataException("插件名称不能为空");
    }
    if (StringUtils.isBlank(ownerAnnotation)) {
      throw metadataException("插件 descriptor owner 不能为空: " + pluginName);
    }
    String normalizedPluginName = pluginName.toLowerCase(Locale.ROOT);
    String existingOwner = descriptorOwners.putIfAbsent(normalizedPluginName, ownerAnnotation);
    if (existingOwner != null && !existingOwner.equals(ownerAnnotation)) {
      throw metadataException(String.format(
          "插件 descriptor 冲突: plugin=%s, owner=%s, conflict=%s",
          pluginName,
          existingOwner,
          ownerAnnotation
      ));
    }
  }

  @Override
  public ModelMetadataException metadataException(String message) {
    return new ModelMetadataException(
        String.format("模型: %s 的 typed annotation 配置错误: %s", modelName(), message),
        modelName()
    );
  }

  @Override
  public ModelMetadataException metadataException(String message, Throwable cause) {
    return new ModelMetadataException(
        String.format("模型: %s 的 typed annotation 配置错误: %s", modelName(), message),
        modelName(),
        cause
    );
  }
}
