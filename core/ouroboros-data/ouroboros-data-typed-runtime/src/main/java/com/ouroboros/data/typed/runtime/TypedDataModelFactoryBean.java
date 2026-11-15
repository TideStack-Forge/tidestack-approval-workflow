package com.ouroboros.data.typed.runtime;

import org.springframework.beans.factory.FactoryBean;

import com.ouroboros.data.model.DataModelCenter;
import com.ouroboros.data.model.DeferredModelRequestRegistry;
import com.ouroboros.data.model.TypedDataModel;

public class TypedDataModelFactoryBean<PK, M> implements FactoryBean<TypedDataModel<PK, M>> {

  private final DeferredModelRequestRegistry requests;
  private final String modelName;
  private final Class<PK> pkClass;
  private final Class<M> modelClass;

  public TypedDataModelFactoryBean(
      DeferredModelRequestRegistry requests,
      String modelName,
      Class<PK> pkClass,
      Class<M> modelClass
  ) {
    this.requests = requests;
    this.modelName = modelName;
    this.pkClass = pkClass;
    this.modelClass = modelClass;
  }

  @Override
  public TypedDataModel<PK, M> getObject() {
    return requests.resolveOrDefer(modelName, pkClass, modelClass, DataModelCenter::getDataModel);
  }

  @Override
  public Class<?> getObjectType() {
    return TypedDataModel.class;
  }

  @Override
  public boolean isSingleton() {
    return true;
  }
}
