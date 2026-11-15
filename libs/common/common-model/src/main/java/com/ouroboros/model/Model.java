package com.ouroboros.model;

public interface Model<M extends Model<M>> extends Cloneable {
  M clone();

  interface Builder<M extends Model<M>> {
    M build();
  }
}
