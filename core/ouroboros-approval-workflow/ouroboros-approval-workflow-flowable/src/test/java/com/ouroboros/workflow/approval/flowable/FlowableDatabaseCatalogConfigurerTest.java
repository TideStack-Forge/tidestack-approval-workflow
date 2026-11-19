package com.ouroboros.workflow.approval.flowable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.flowable.spring.boot.EngineConfigurationConfigurer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.ResolvableType;

import com.ouroboros.workflow.approval.lifecycle.ApprovalCaseService;

/**
 * {@link FlowableDatabaseCatalogConfigurer} 的行为：只有 MySQL 系数据库、且使用方没有显式指定 catalog 时，
 * 才把 catalog 限定为当前库；任何取不到 catalog 的异常路径都必须安静降级，不能影响启动。
 */
class FlowableDatabaseCatalogConfigurerTest {

  @Test
  void shouldLimitCatalogToCurrentDatabaseOnMySql() throws Exception {
    SpringProcessEngineConfiguration configuration = new SpringProcessEngineConfiguration();

    new FlowableDatabaseCatalogConfigurer(dataSource("MySQL", "app_db")).configure(configuration);

    assertEquals("app_db", configuration.getDatabaseCatalog());
  }

  @Test
  void shouldLimitCatalogToCurrentDatabaseOnMariaDb() throws Exception {
    SpringProcessEngineConfiguration configuration = new SpringProcessEngineConfiguration();

    new FlowableDatabaseCatalogConfigurer(dataSource("MariaDB", "app_db")).configure(configuration);

    assertEquals("app_db", configuration.getDatabaseCatalog());
  }

  @Test
  void shouldKeepFlowableDefaultOnNonCrossCatalogDatabases() throws Exception {
    SpringProcessEngineConfiguration configuration = new SpringProcessEngineConfiguration();

    new FlowableDatabaseCatalogConfigurer(dataSource("PostgreSQL", "app_db")).configure(configuration);

    assertEquals("", configuration.getDatabaseCatalog());
  }

  @Test
  void shouldNotOverrideExplicitlyConfiguredCatalog() throws Exception {
    DataSource dataSource = mock(DataSource.class);
    SpringProcessEngineConfiguration configuration = new SpringProcessEngineConfiguration();
    configuration.setDatabaseCatalog("explicit_db");

    new FlowableDatabaseCatalogConfigurer(dataSource).configure(configuration);

    assertEquals("explicit_db", configuration.getDatabaseCatalog());
    verifyNoInteractions(dataSource);
  }

  @Test
  void shouldKeepFlowableDefaultWhenConnectionReportsNoCatalog() throws Exception {
    SpringProcessEngineConfiguration configuration = new SpringProcessEngineConfiguration();

    new FlowableDatabaseCatalogConfigurer(dataSource("MySQL", null)).configure(configuration);

    assertEquals("", configuration.getDatabaseCatalog());
  }

  @Test
  void shouldKeepFlowableDefaultWhenDatabaseProductNameIsMissing() throws Exception {
    SpringProcessEngineConfiguration configuration = new SpringProcessEngineConfiguration();

    new FlowableDatabaseCatalogConfigurer(dataSource(null, "app_db")).configure(configuration);

    assertEquals("", configuration.getDatabaseCatalog());
  }

  @Test
  void shouldDegradeQuietlyWhenDataSourceIsUnavailable() throws Exception {
    DataSource dataSource = mock(DataSource.class);
    when(dataSource.getConnection()).thenThrow(new SQLException("connection refused"));
    SpringProcessEngineConfiguration configuration = new SpringProcessEngineConfiguration();

    new FlowableDatabaseCatalogConfigurer(dataSource).configure(configuration);

    assertEquals("", configuration.getDatabaseCatalog());
  }

  @Test
  void shouldRegisterConfigurerThatAppliesCatalogFromDataSource() {
    DataSource dataSource = dataSourceUnchecked("MySQL", "app_db");

    new ApplicationContextRunner()
        .withBean(ApprovalCaseService.class, () -> mock(ApprovalCaseService.class))
        .withConfiguration(AutoConfigurations.of(ApprovalWorkflowFlowableAutoConfiguration.class))
        .withBean("dataSource", DataSource.class, () -> dataSource)
        .withBean(TaskService.class, () -> mock(TaskService.class))
        .withBean(RuntimeService.class, () -> mock(RuntimeService.class))
        .withBean(RepositoryService.class, () -> mock(RepositoryService.class))
        .withBean(HistoryService.class, () -> mock(HistoryService.class))
        .withBean(SpringProcessEngineConfiguration.class, () -> mock(SpringProcessEngineConfiguration.class))
        .run(context -> {
          assertNull(context.getStartupFailure());

          ObjectProvider<EngineConfigurationConfigurer<SpringProcessEngineConfiguration>> provider =
              context.getBeanFactory().getBeanProvider(ResolvableType.forClassWithGenerics(
                  EngineConfigurationConfigurer.class, SpringProcessEngineConfiguration.class));
          EngineConfigurationConfigurer<SpringProcessEngineConfiguration> configurer = provider.getIfAvailable();
          assertNotNull(configurer);

          SpringProcessEngineConfiguration configuration = new SpringProcessEngineConfiguration();
          configurer.configure(configuration);
          assertEquals("app_db", configuration.getDatabaseCatalog());
        });
  }

  private static DataSource dataSource(String productName, String catalog) throws SQLException {
    DataSource dataSource = mock(DataSource.class);
    Connection connection = mock(Connection.class);
    DatabaseMetaData metaData = mock(DatabaseMetaData.class);
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.getMetaData()).thenReturn(metaData);
    when(metaData.getDatabaseProductName()).thenReturn(productName);
    when(connection.getCatalog()).thenReturn(catalog);
    return dataSource;
  }

  /**
   * {@link org.springframework.boot.test.context.runner.ApplicationContextRunner#withBean} 的 lambda 不允许抛出受检异常，
   * 这里把受检异常收敛成 {@link IllegalStateException}。
   */
  private static DataSource dataSourceUnchecked(String productName, String catalog) {
    try {
      return dataSource(productName, catalog);
    } catch (SQLException e) {
      throw new IllegalStateException("构造测试 DataSource 失败", e);
    }
  }
}
