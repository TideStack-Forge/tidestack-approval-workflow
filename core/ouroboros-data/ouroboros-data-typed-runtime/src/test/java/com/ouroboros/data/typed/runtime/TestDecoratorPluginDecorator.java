package com.ouroboros.data.typed.runtime;

import com.ouroboros.data.model.DataModelMeta;
import com.ouroboros.data.model.TypedAnnotationDecoratorContext;
import com.ouroboros.data.model.TypedModelAnnotationDecorator;

public class TestDecoratorPluginDecorator implements TypedModelAnnotationDecorator<TestDecoratorPlugin> {

  @Override
  public Class<TestDecoratorPlugin> annotationType() {
    return TestDecoratorPlugin.class;
  }

  @Override
  public DataModelMeta decorate(
      Class<?> modelClass,
      TestDecoratorPlugin annotation,
      DataModelMeta modelMeta,
      TypedAnnotationDecoratorContext context
  ) {
    DataModelMeta decorated = modelMeta.deepCopy();
    decorated.setExtraProp("testDecoratorPlugin", annotation.value());
    return decorated;
  }
}
