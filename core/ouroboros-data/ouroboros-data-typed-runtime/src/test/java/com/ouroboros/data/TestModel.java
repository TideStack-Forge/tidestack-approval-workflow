package com.ouroboros.data;

import com.ouroboros.data.annotation.Field;
import com.ouroboros.data.annotation.Model;
import com.ouroboros.data.annotation.PrimaryKey;

/**
 * @author liansz
 */
@Model
public class TestModel {

  @Field
  @PrimaryKey(generator = "snowflake")
  private String id;

  @Field
  private String name;

  @Field(nullable = true)
  private Integer age;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public Integer getAge() {
    return age;
  }

  public void setAge(Integer age) {
    this.age = age;
  }
}
