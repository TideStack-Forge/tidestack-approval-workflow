package com.ouroboros.data;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import jakarta.annotation.PostConstruct;
import javax.sql.DataSource;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import com.ouroboros.data.config.DataSourceConfiguration;
import com.ouroboros.data.station.DataStationDefine;
import com.ouroboros.data.station.DataStationDefineProvider;

/**
 * @author liansz
 */
public class SpringDataStationDefineProvider implements DataStationDefineProvider {

  @Autowired
  DataSource dataSource;
  @Autowired
  DataSourceConfiguration dataSourceConfiguration;
  @Value("${spring.datasource.migration-strategy:#{null}}")
  private String migrationStrategy;
  @Value("${spring.datasource.dialect:auto}")
  private String sqlDialect;
  @Value("${ouroboros.data.sql.dialect:auto}")
  private String sqlDialect2;
  private Map<String, DataStationDefine> registry = new HashMap<>();

  public SpringDataStationDefineProvider() {
    Bridge.instance = this;
  }

  @PostConstruct
  private void init() {
    // 默认数据源
    {
      String name = "default";
      String dialect = StringUtils.isEmpty(sqlDialect) || "auto".equalsIgnoreCase(sqlDialect)
          ? StringUtils.isEmpty(sqlDialect2) ? "auto" : sqlDialect2
          : sqlDialect;
      DataStationDefine dataStationDefine = new DataStationDefine();
      dataStationDefine.setName(name);
      dataStationDefine.setType("sql");
      dataStationDefine.setProperty("springDataSource", dataSource);
      dataStationDefine.setProperty("migrationStrategy", migrationStrategy);
      dataStationDefine.setProperty("sqlDialect", dialect);

      registry.put(name, dataStationDefine);
    }

    // 扩展数据源
    if (dataSourceConfiguration == null) {
      return;
    }
    dataSourceConfiguration.getDataSourceProperties().forEach((name, properties) -> {
      DataStationDefine dataStationDefine = new DataStationDefine();
      dataStationDefine.setName(name);
      dataStationDefine.setType("sql");
      dataStationDefine.setProperties(properties);

      registry.put(name, dataStationDefine);
    });
  }

  @Override
  public Optional<DataStationDefine> getDataStationDefine(String name) {
    return Optional.ofNullable(registry.get(name));
  }

  @Override
  public Map<String, DataStationDefine> getDataStationDefineMap() {
    return Collections.unmodifiableMap(registry);
  }

  /**
   * Spring Datasource构建器
   */
  public static class DataSourceBuilder implements com.ouroboros.data.sql.DataSourceBuilder {

    @Override
    public Optional<DataSource> apply(DataStationDefine dataStationDefine) {
      return dataStationDefine.getProperty(DataSource.class, "springDataSource");
    }
  }

  /**
   * 桥接SPI与Spring
   */
  public static class Bridge implements DataStationDefineProvider {

    private static DataStationDefineProvider instance;

    @Override
    public Optional<DataStationDefine> getDataStationDefine(String name) {
      return Optional.ofNullable(instance)
          .flatMap(provider -> provider.getDataStationDefine(name));
    }

    @Override
    public Map<String, DataStationDefine> getDataStationDefineMap() {
      return Optional.ofNullable(instance)
          .map(DataStationDefineProvider::getDataStationDefineMap)
          .orElseGet(Collections::emptyMap);
    }
  }
}
