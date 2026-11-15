package com.ouroboros.workflow.approval.flowable;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.Locale;

import javax.sql.DataSource;

import org.flowable.spring.SpringProcessEngineConfiguration;
import org.flowable.spring.boot.EngineConfigurationConfigurer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import com.ouroboros.model.annotation.AllowDirectJDBC;

/**
 * 给 Flowable 引擎补上 database catalog。
 *
 * <p>背景：Flowable 6.8.1 判断“表是否已存在”用的是 {@code DatabaseMetaData#getTables}，而
 * {@code DbSqlSession} 的两参构造器不会填充 {@code connectionMetadataDefaultCatalog}，
 * 导致排查表时传入的 catalog 恒为 {@code null}。MySQL / MariaDB 的 JDBC 元数据在 catalog 为空时
 * <b>会跨库返回同名表</b>（schema 参数被忽略），因此只要同一个 MySQL 实例里还有别的库存在
 * {@code ACT_*} 表，空库就会被误判成“表已存在”：
 *
 * <ul>
 *   <li>{@code CommonDbSchemaManager#schemaUpdate} 走升级分支，在 {@code ACT_GE_PROPERTY} 上执行
 *       {@code insert}/{@code select}，报 {@code Table 'xxx.ACT_GE_PROPERTY' doesn't exist}；</li>
 *   <li>{@code ProcessDbSchemaManager#schemaUpdate} 认为 {@code ACT_RU_EXECUTION} 已存在，跳过建表。</li>
 * </ul>
 *
 * <p>把 catalog 显式设成当前连接的库名即可让元数据查询限定在本库，Flowable 自带的
 * “空库建表”逻辑（{@code schemaUpdate} → {@code schemaCreate}）就能正常执行。catalog 会经
 * {@code AbstractEngineConfigurator#initDbProperties} 传递给 eventregistry / idm 等子引擎。
 *
 * <p>只在 MySQL / MariaDB 上生效：其他数据库的元数据天然限定在当前库，多设 catalog 反而可能
 * 影响连接行为。
 */
public class FlowableDatabaseCatalogConfigurer implements EngineConfigurationConfigurer<SpringProcessEngineConfiguration> {

  private static final Logger logger = LoggerFactory.getLogger(FlowableDatabaseCatalogConfigurer.class);

  private final DataSource dataSource;

  public FlowableDatabaseCatalogConfigurer(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  @Override
  public void configure(SpringProcessEngineConfiguration configuration) {
    if (StringUtils.hasText(configuration.getDatabaseCatalog())) {
      // 使用方已显式指定（如 flowable.database-catalog），不覆盖
      return;
    }

    String catalog = resolveCurrentCatalog();
    if (!StringUtils.hasText(catalog)) {
      return;
    }

    configuration.setDatabaseCatalog(catalog);
    logger.info("Flowable database catalog 已限定为当前库 [{}]：避免 MySQL 跨库元数据把其他库的 ACT_* 表"
        + "误判为本库表，导致空库无法自动建表", catalog);
  }

  /**
   * 取当前连接所属的库名（catalog）。取不到、或不是 MySQL 系数据库时返回 {@code null}。
   */
  @AllowDirectJDBC("metadata introspection for Flowable catalog isolation; no business data access")
  private String resolveCurrentCatalog() {
    try (Connection connection = dataSource.getConnection()) {
      if (!isCrossCatalogMetadata(connection.getMetaData())) {
        return null;
      }
      return connection.getCatalog();
    } catch (SQLException e) {
      logger.warn("读取数据库 catalog 失败，Flowable 将沿用默认（不限定 catalog）的元数据查询", e);
      return null;
    }
  }

  /**
   * 该数据库的 JDBC 元数据查询是否会在 catalog 为空时跨库匹配。目前已知 MySQL / MariaDB 如此。
   */
  @AllowDirectJDBC("database product metadata detection for Flowable catalog isolation")
  static boolean isCrossCatalogMetadata(DatabaseMetaData metaData) throws SQLException {
    String productName = metaData.getDatabaseProductName();
    if (!StringUtils.hasText(productName)) {
      return false;
    }
    String normalized = productName.toLowerCase(Locale.ROOT);
    return normalized.contains("mysql") || normalized.contains("mariadb");
  }
}
