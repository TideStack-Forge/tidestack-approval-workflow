package com.ouroboros.data.model;

/**
 * 雪花算法参数提供者
 * 通过 SPI 机制提供 workerId 和 datacenterId
 */
public interface SnowflakeNodeIdProvider {
  int getWorkerId();

  int getDatacenterId();
}
