package com.ouroboros.data.model;

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.fail;

import java.util.Collections;

import org.junit.Test;

import com.ouroboros.data.config.DefaultSnowflakeNodeIdProviderConfig;

public class SnowflakePrimaryKeyGeneratorFactoryTest {

  @Test
  public void generatorsForSameNodeShareSequenceState() {
    new DefaultSnowflakeNodeIdProviderConfig();
    SnowflakePrimaryKeyGeneratorFactory factory = new SnowflakePrimaryKeyGeneratorFactory();
    PrimaryKeyGenerator<?> first = factory.get("snowflake").orElseThrow();
    PrimaryKeyGenerator<?> second = factory.get("snowflake").orElseThrow();

    for (int attempt = 0; attempt < 100000; attempt++) {
      long before = System.currentTimeMillis();
      String firstId = String.valueOf(first.next(Collections.emptyMap()));
      String secondId = String.valueOf(second.next(Collections.emptyMap()));
      long after = System.currentTimeMillis();
      if (before == after) {
        assertNotEquals(firstId, secondId);
        return;
      }
    }

    fail("Could not generate two snowflake ids within the same millisecond");
  }
}
