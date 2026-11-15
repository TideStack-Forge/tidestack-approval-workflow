package com.ouroboros.data;

import com.ouroboros.data.annotation.AutoIncrement;
import com.ouroboros.data.annotation.Field;
import com.ouroboros.data.annotation.Model;
import com.ouroboros.data.annotation.PrimaryKey;

@Model
public class AutoIncrementTestModel {
  @Field
  @PrimaryKey
  @AutoIncrement
  private Integer id;

  @Field
  private String name;

  public Integer getId() {
    return id;
  }

  public void setId(Integer id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }
}
