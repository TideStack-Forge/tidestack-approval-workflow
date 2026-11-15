package com.ouroboros.data.config;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author liansz
 */
@Configuration
public class DataSourceConfiguration {

  @Bean
  @ConfigurationProperties(prefix = "ouroboros.additional-datasources")
  public Map<String, Map<String, Object>> getDataSourceProperties() {
    return new HashMap<>(8);
  }
}
