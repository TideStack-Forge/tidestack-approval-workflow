package com.ouroboros.workflow.approval.flowable;

import javax.sql.DataSource;

import jakarta.annotation.PostConstruct;

import org.flowable.spring.SpringProcessEngineConfiguration;
import org.flowable.spring.boot.EngineConfigurationConfigurer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import com.ouroboros.workflow.approval.ApprovalWorkflowAutoConfiguration;

@Configuration
@ConditionalOnProperty(name = "ouroboros.workflow.approval.engine", havingValue = "flowable", matchIfMissing = true)
@Import(ApprovalWorkflowAutoConfiguration.class)
@ComponentScan
public class ApprovalWorkflowFlowableAutoConfiguration {
  private static final Logger logger = LoggerFactory.getLogger(ApprovalWorkflowFlowableAutoConfiguration.class);

  @PostConstruct
  public void init() {
    logger.info("Approval Workflow Flowable engine Auto Configuration loaded");
  }

  /**
   * 在 Flowable 构建引擎之前补齐 database catalog，详见 {@link FlowableDatabaseCatalogConfigurer}。
   *
   * <p>没有 DataSource 时不注册配置器（例如只验证自动配置装配关系的测试上下文）。
   */
  @Bean
  public EngineConfigurationConfigurer<SpringProcessEngineConfiguration> flowableDatabaseCatalogConfigurer(
      ObjectProvider<DataSource> dataSourceProvider) {
    return configuration -> {
      DataSource dataSource = dataSourceProvider.getIfAvailable();
      if (dataSource == null) {
        return;
      }
      new FlowableDatabaseCatalogConfigurer(dataSource).configure(configuration);
    };
  }
}
