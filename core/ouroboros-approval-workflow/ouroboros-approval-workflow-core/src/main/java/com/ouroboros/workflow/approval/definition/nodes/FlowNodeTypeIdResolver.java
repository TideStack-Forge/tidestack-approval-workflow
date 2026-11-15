package com.ouroboros.workflow.approval.definition.nodes;

import java.io.IOException;
import java.util.Objects;
import java.util.stream.Stream;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.DatabindContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.jsontype.impl.TypeIdResolverBase;
import com.ouroboros.util.SPI;
import com.ouroboros.workflow.approval.definition.FlowNode;
import com.ouroboros.workflow.approval.definition.FlowNodeSubClassResolver;

public class FlowNodeTypeIdResolver extends TypeIdResolverBase {
  private JavaType superType;

  @Override
  public void init(JavaType bt) {
    superType = bt;
  }

  @Override
  public String idFromValue(Object o) {
    return null;
  }

  @Override
  public String idFromValueAndType(Object o, Class<?> aClass) {
    return null;
  }

  @Override
  public JsonTypeInfo.Id getMechanism() {
    return JsonTypeInfo.Id.CUSTOM;
  }

  @Override
  public JavaType typeFromId(DatabindContext context, String id) throws IOException {
    Class<?> subType = Stream.concat(
        Stream.of(new BuildInFlowNodeSubClassResolver()),
        SPI.getCachedServiceStream(FlowNodeSubClassResolver.class))
        .map(provider -> provider.resolve(id))
        .filter(Objects::nonNull)
        .findFirst()
        .orElse((Class) FlowNode.class);
    return context.constructSpecializedType(superType, subType);
  }
}
