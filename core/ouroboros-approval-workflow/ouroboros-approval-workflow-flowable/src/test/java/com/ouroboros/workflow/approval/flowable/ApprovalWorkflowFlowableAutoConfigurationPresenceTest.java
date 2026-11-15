package com.ouroboros.workflow.approval.flowable;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurationImportFilter;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.support.SpringFactoriesLoader;

class ApprovalWorkflowFlowableAutoConfigurationPresenceTest {

  @Test
  void autoConfigurationClassShouldExist() {
    assertNotNull(new ApprovalWorkflowFlowableAutoConfiguration());
  }

  @Test
  void autoConfigurationShouldActivateOnlyForFlowableEngine() {
    ConditionalOnProperty annotation = ApprovalWorkflowFlowableAutoConfiguration.class.getAnnotation(ConditionalOnProperty.class);

    assertNotNull(annotation);
    assertArrayEquals(new String[]{"ouroboros.workflow.approval.engine"}, annotation.name());
    assertEquals("flowable", annotation.havingValue());
    assertTrue(annotation.matchIfMissing());
  }

  @Test
  void autoConfigurationShouldNotRegisterVendorAutoConfigurationFilter() {
    List<String> filterNames = SpringFactoriesLoader.loadFactoryNames(
        AutoConfigurationImportFilter.class,
        Thread.currentThread().getContextClassLoader());

    assertFalse(filterNames.contains("com.ouroboros.workflow.approval.flowable.config.FlowableAutoConfigurationFilter"));
  }

  @Test
  void autoConfigurationShouldBeDiscoverableThroughSpringFactories() {
    List<String> factoryNames = SpringFactoriesLoader.loadFactoryNames(
        EnableAutoConfiguration.class,
        Thread.currentThread().getContextClassLoader());

    assertTrue(factoryNames.contains(ApprovalWorkflowFlowableAutoConfiguration.class.getName()));
  }
}
