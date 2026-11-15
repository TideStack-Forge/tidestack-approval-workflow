package com.ouroboros.workflow.approval.definition.candidates;

import java.io.IOException;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.DatabindContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.jsontype.impl.TypeIdResolverBase;
import com.ouroboros.util.SPI;
import com.ouroboros.workflow.approval.definition.Candidate;
import com.ouroboros.workflow.approval.definition.CandidateSubClassResolver;

public class CandidateTypeIdResolver extends TypeIdResolverBase {
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
    return JsonTypeInfo.Id.NAME;
  }

  @Override
  public JavaType typeFromId(DatabindContext context, String id) throws IOException {
    Class<?> subType = SPI.getCachedServiceStream(CandidateSubClassResolver.class)
        .map(provider -> provider.resolve(id))
        .filter(Objects::nonNull)
        .findFirst()
        .orElse((Class) Candidate.class);
    return context.constructSpecializedType(superType, subType);
  }
}
