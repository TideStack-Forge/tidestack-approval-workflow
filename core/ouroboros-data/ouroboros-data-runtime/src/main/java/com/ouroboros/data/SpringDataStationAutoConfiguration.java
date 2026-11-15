package com.ouroboros.data;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import com.ouroboros.data.config.DataSourceConfiguration;
import com.ouroboros.data.config.DefaultSnowflakeNodeIdProviderConfig;
import com.ouroboros.data.pkgenerator.FixLengthCodingGeneratorFactory;

/**
 * @author liansz
 */
@Configuration
@Import({
    SpringDataStationDefineProvider.class,
    DataSourceConfiguration.class,
    DefaultSnowflakeNodeIdProviderConfig.class,
    FixLengthCodingGeneratorFactory.class
})
public class SpringDataStationAutoConfiguration {
  private final static Logger logger = LoggerFactory.getLogger(SpringDataStationAutoConfiguration.class);

  @PostConstruct
  void init() {
    logger.info("Ouroboros Spring DataStation Auto Configuration loaded");
  }

}
