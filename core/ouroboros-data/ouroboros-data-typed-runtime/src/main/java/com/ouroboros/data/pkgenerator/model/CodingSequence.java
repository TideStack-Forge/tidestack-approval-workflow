package com.ouroboros.data.pkgenerator.model;

import java.time.LocalDateTime;

import com.ouroboros.data.annotation.Field;
import com.ouroboros.data.annotation.Model;
import com.ouroboros.data.annotation.PrimaryKey;

@Model(rawName = "sys_coding_sequence", label = "主键编码序列表")
@SuppressWarnings({"unused", "UnusedReturnValue"})
public class CodingSequence {
  @Field
  @PrimaryKey
  private String name;
  @Field
  private Long lastNumber;
  @Field
  private LocalDateTime lastGenerateTime;

  public String getName() {
    return name;
  }

  public CodingSequence setName(String name) {
    this.name = name;
    return this;
  }

  public Long getLastNumber() {
    return lastNumber;
  }

  public CodingSequence setLastNumber(Long lastNumber) {
    this.lastNumber = lastNumber;
    return this;
  }

  public LocalDateTime getLastGenerateTime() {
    return lastGenerateTime;
  }

  public CodingSequence setLastGenerateTime(LocalDateTime lastGenerateTime) {
    this.lastGenerateTime = lastGenerateTime;
    return this;
  }
}
