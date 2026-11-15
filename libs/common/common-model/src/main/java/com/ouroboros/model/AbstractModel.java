package com.ouroboros.model;

import java.util.function.Consumer;

public abstract class AbstractModel<M extends AbstractModel<M, B>, B extends AbstractModel.Builder<M>> implements Model<M> {

  @Override
  public M clone() {
    try {
      return (M) super.clone();
    } catch (CloneNotSupportedException e) {
      throw new IllegalStateException("模型克隆失败", e);
    }
  }

  abstract protected B createBuilder();

  @SafeVarargs
  public final M map(Consumer<B>... mappers) {
    var cloned = clone();
    var builder = (B) createBuilder();
    builder.setModel(cloned);
    for (var mapper : mappers) {
      mapper.accept(builder);
    }
    return builder.build();
  }

  public abstract static class Builder<M extends AbstractModel<M, ? extends Builder<M>>> implements Model.Builder<M> {
    protected M model;

    protected Builder() {
    }

    protected void setModel(Object model) {
      this.model = (M) model;
    }

    @Override
    public M build() {
      return model;
    }

  }
}
