package com.ouroboros.data;

import com.ouroboros.data.annotation.Field;
import com.ouroboros.data.annotation.Model;
import com.ouroboros.data.typed.runtime.TestDecoratorPlugin;

@Model
@TestDecoratorPlugin("enabled")
public class TestDecoratorModel {

  @Field
  private String id;
}
