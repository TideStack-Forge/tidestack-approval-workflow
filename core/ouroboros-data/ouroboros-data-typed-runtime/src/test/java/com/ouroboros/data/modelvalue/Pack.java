package com.ouroboros.data.modelvalue;

import com.ouroboros.data.model.ModelValue;

/**
 * @author liansz
 */
public class Pack {

  private ModelValue<String, Model> a;

  public Pack() {
  }

  public Pack(ModelValue<String, Model> a) {
    this.a = a;
  }

  public ModelValue<String, Model> getA() {
    return a;
  }

  public void setA(ModelValue<String, Model> a) {
    this.a = a;
  }
}
