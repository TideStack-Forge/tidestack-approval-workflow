package com.ouroboros.data.model;

import java.util.Optional;

import com.ouroboros.util.SPI;
import com.ouroboros.util.Snowflakes;

/**
 * 雪花算法主键生成器工厂
 *
 * @author liansz
 */
public class SnowflakePrimaryKeyGeneratorFactory implements PrimaryKeyGeneratorFactory {
  private final static SnowflakeNodeIdProvider DEFAULT_SNOWFLAKE_CONFIG_SUPPLIER = Optional.ofNullable(SPI.getPrimaryService(SnowflakeNodeIdProvider.class))
      .orElseGet(() -> new SnowflakeNodeIdProvider() {
        @Override
        public int getWorkerId() {
          return 0;
        }

        @Override
        public int getDatacenterId() {
          return 0;
        }
      });

  @Override
  public Optional<PrimaryKeyGenerator<?>> get(String s) {
    if (!"snowflake".equalsIgnoreCase(s)) {
      return Optional.empty();
    }

    var dataCenterId = DEFAULT_SNOWFLAKE_CONFIG_SUPPLIER.getDatacenterId();
    var workerId = DEFAULT_SNOWFLAKE_CONFIG_SUPPLIER.getWorkerId();
    var snowflake = Snowflakes.get(workerId, dataCenterId);

    PrimaryKeyGenerator<String> generator = record -> String.valueOf(snowflake.nextId());
    return Optional.of(generator);
  }
}
