package com.ouroboros.data;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import com.ouroboros.data.model.DeferredModelRequestRegistry;
import com.ouroboros.data.pkgenerator.DBCodingSequencer;
import com.ouroboros.data.typed.runtime.DataModelScanRegistry;
import com.ouroboros.data.typed.runtime.DeferredModelRequestRegistryLifecycle;
import com.ouroboros.data.typed.runtime.ModelRefreshedListener;
import com.ouroboros.data.typed.runtime.TypedRuntimeModelScanConfiguration;

/**
 * @author liansz
 */
@Configuration
@Import({
    TypedRuntimeModelScanConfiguration.class,
    DeferredModelRequestRegistryLifecycle.class,
    ModelRefreshedListener.class,
    DBCodingSequencer.class
})
public class TypedDataModelAutoConfiguration {

  private final static Logger logger = LoggerFactory.getLogger(TypedDataModelAutoConfiguration.class);

  @PostConstruct
  public void init() {
    logger.info("Ouroboros Typed Data Model Auto Configuration loaded");
  }

  @Bean
  public static DataModelScanRegistry dataModelScanRegistry() {
    return new DataModelScanRegistry();
  }

  @Bean
  public DeferredModelRequestRegistry deferredModelRequestRegistry() {
    return new DeferredModelRequestRegistry();
  }
}
