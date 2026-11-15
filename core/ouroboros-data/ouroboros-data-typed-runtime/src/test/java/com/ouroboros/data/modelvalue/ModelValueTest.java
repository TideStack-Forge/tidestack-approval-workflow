package com.ouroboros.data.modelvalue;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import io.vavr.control.Try;

import com.ouroboros.data.model.ModelValue;
import com.ouroboros.data.util.DataJson;

/**
 * @author liansz
 */
public class ModelValueTest {
  private static Model unwrapModel(Object value) {
    if (value instanceof Model model) {
      return model;
    }
    if (value instanceof Try<?> tryValue && tryValue.isSuccess() && tryValue.get() instanceof Model model) {
      return model;
    }
    throw new IllegalStateException("Cannot unwrap Model from value: " + value);
  }

  @Test
  public void serializerJson() {
    // 主键序列化
    Pack packPrimary = new Pack(ModelValue.primary("123"));
    assertEquals(DataJson.toJsonString(packPrimary), "{\"a\":\"123\"}");

    // 模型序列化
    Pack packModel = new Pack(ModelValue.model(new Model("123")));
    assertEquals(DataJson.toJsonString(packModel), "{\"a\":{\"id\":\"123\"}}");
  }

  @Test
  public void deserializerJson() {
    // 主键反序列化
    String packPrimaryJson = "{\"a\":\"123\"}";
    Pack packPrimary = DataJson.toBean(Pack.class, packPrimaryJson);
    assertEquals(packPrimary.getA().get(), "123");

    // 模型反序列化
    String packModelJson = "{\"a\":{\"id\":\"123\"}}";
    Pack packModel = DataJson.toBean(Pack.class, packModelJson);
    assertEquals(unwrapModel(packModel.getA().get()).getId(), "123");
  }
}
