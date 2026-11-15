package com.ouroboros.data.config;

import jakarta.annotation.Priority;

import org.springframework.beans.factory.annotation.Value;

import com.ouroboros.data.model.SnowflakeNodeIdProvider;

/**
 * 从配置文件中读取雪花算法的 workerId 和 datacenterId
 *
 * @author liansz
 */
public class DefaultSnowflakeNodeIdProviderConfig {

  @Value("${ouroboros.data-model.snowflake.worker-id:0}")
  private Integer workerId;

  @Value("${ouroboros.data-model.snowflake.datacenter-id:0}")
  private Integer datacenterId;

  public DefaultSnowflakeNodeIdProviderConfig() {
    Proxy.supplier = this;
  }

  @Priority(0)
  public static class Proxy implements SnowflakeNodeIdProvider {

    private static DefaultSnowflakeNodeIdProviderConfig supplier;

    @Override
    public int getWorkerId() {
      return supplier.workerId == null ? 0 : supplier.workerId;
    }

    @Override
    public int getDatacenterId() {
      return supplier.datacenterId == null ? 0 : supplier.datacenterId;
    }
  }
}
