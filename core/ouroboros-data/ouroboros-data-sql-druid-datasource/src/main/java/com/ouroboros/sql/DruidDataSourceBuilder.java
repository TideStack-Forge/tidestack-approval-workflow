package com.ouroboros.sql;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;

import javax.sql.DataSource;

import io.vavr.control.Try;

import com.alibaba.druid.pool.DruidDataSource;
import com.ouroboros.data.sql.DataSourceBuilder;
import com.ouroboros.data.station.DataStationDefine;
import com.ouroboros.util.CheckedBiConsumer;

public class DruidDataSourceBuilder implements DataSourceBuilder {
  Map<String, CheckedBiConsumer<DruidDataSource, String>> optionalStringProperties = new HashMap<>() {{
    put("validationQuery", DruidDataSource::setValidationQuery);
    put("connectionProperties", DruidDataSource::setConnectionProperties);
    put("filters", DruidDataSource::setFilters);
    put("statLoggerClassName", DruidDataSource::setStatLoggerClassName);
    put("passwordCallbackClassName", DruidDataSource::setPasswordCallbackClassName);
    put("dbType", DruidDataSource::setDbType);
    put("defaultCatalog", DruidDataSource::setDefaultCatalog);
    put("exceptionSorter", DruidDataSource::setExceptionSorter);
  }};
  Map<String, BiConsumer<DruidDataSource, Integer>> optionalIntegerProperties = new HashMap<>() {{
    put("initialSize", DruidDataSource::setInitialSize);
    put("minIdle", DruidDataSource::setMinIdle);
    put("maxActive", DruidDataSource::setMaxActive);
    put("maxPoolPreparedStatementPerConnectionSize", DruidDataSource::setMaxPoolPreparedStatementPerConnectionSize);
    put("validationQueryTimeout", DruidDataSource::setValidationQueryTimeout);
    put("transactionQueryTimeout", DruidDataSource::setTransactionQueryTimeout);
    put("connectionErrorRetryAttempts", DruidDataSource::setConnectionErrorRetryAttempts);
    put("queryTimeout", DruidDataSource::setQueryTimeout);
    put("onFatalErrorMaxActive", DruidDataSource::setOnFatalErrorMaxActive);
    put("maxCreateTaskCount", DruidDataSource::setMaxCreateTaskCount);
    put("maxWaitThreadCount", DruidDataSource::setMaxWaitThreadCount);
    put("notFullTimeoutRetryCount", DruidDataSource::setNotFullTimeoutRetryCount);
    put("defaultTransactionIsolation", DruidDataSource::setDefaultTransactionIsolation);
    put("maxOpenPreparedStatements", DruidDataSource::setMaxOpenPreparedStatements);
  }};
  Map<String, BiConsumer<DruidDataSource, Long>> optionalLongProperties = new HashMap<>() {{
    put("timeBetweenEvictionRunsMillis", DruidDataSource::setTimeBetweenEvictionRunsMillis);
    put("minEvictableIdleTimeMillis", DruidDataSource::setMinEvictableIdleTimeMillis);
    put("maxEvictableIdleTimeMillis", DruidDataSource::setMaxEvictableIdleTimeMillis);
    put("timeBetweenLogStatsMillis", DruidDataSource::setTimeBetweenLogStatsMillis);
    put("maxWait", DruidDataSource::setMaxWait);
    put("removeAbandonedTimeoutMillis", DruidDataSource::setRemoveAbandonedTimeoutMillis);
    put("transactionThresholdMillis", DruidDataSource::setTransactionThresholdMillis);
    put("phyTimeoutMillis", DruidDataSource::setPhyTimeoutMillis);
    put("phyMaxUseCount", DruidDataSource::setPhyMaxUseCount);
  }};
  Map<String, BiConsumer<DruidDataSource, Boolean>> optionalBooleanProperties = new HashMap<>() {{
    put("poolPreparedStatements", DruidDataSource::setPoolPreparedStatements);
    put("testOnBorrow", DruidDataSource::setTestOnBorrow);
    put("testOnReturn", DruidDataSource::setTestOnReturn);
    put("testWhileIdle", DruidDataSource::setTestWhileIdle);
    put("keepAlive", DruidDataSource::setKeepAlive);
    put("removeAbandoned", DruidDataSource::setRemoveAbandoned);
    put("logAbandoned", DruidDataSource::setLogAbandoned);
    put("asyncInit", DruidDataSource::setAsyncInit);
    put("useUnfairLock", DruidDataSource::setUseUnfairLock);
    put("useGlobalDataSourceStat", DruidDataSource::setUseGlobalDataSourceStat);
    put("useLocalSessionState", DruidDataSource::setUseLocalSessionState);
    put("sharePreparedStatements", DruidDataSource::setSharePreparedStatements);
    put("resetStatEnable", DruidDataSource::setResetStatEnable);
    put("failFast", DruidDataSource::setFailFast);
    put("breakAfterAcquireFailure", DruidDataSource::setBreakAfterAcquireFailure);
    put("oracle", DruidDataSource::setOracle);
    put("useOracleImplicitCache", DruidDataSource::setUseOracleImplicitCache);
    put("initVariants", DruidDataSource::setInitVariants);
    put("initGlobalVariants", DruidDataSource::setInitGlobalVariants);
    put("accessToUnderlyingConnectionAllowed", DruidDataSource::setAccessToUnderlyingConnectionAllowed);
    put("asyncCloseConnectionEnable", DruidDataSource::setAsyncCloseConnectionEnable);
    put("clearFiltersEnable", DruidDataSource::setClearFiltersEnable);
    put("defaultAutoCommit", DruidDataSource::setDefaultAutoCommit);
    put("defaultReadOnly", DruidDataSource::setDefaultReadOnly);
    put("dupCloseLogEnable", DruidDataSource::setDupCloseLogEnable);
    put("initExceptionThrow", DruidDataSource::setInitExceptionThrow);
  }};

  @Override
  public Optional<DataSource> apply(DataStationDefine dataStationDefine) {
    var url = dataStationDefine.getProperty(String.class, "url");
    var username = dataStationDefine.getProperty(String.class, "username");
    var password = dataStationDefine.getProperty(String.class, "password");
    var driverClassName = dataStationDefine.getProperty(String.class, "driverClassName");
    var name = Optional.of(dataStationDefine.getName());
    if (!(url.isPresent() && username.isPresent() && password.isPresent() && driverClassName.isPresent() && name.isPresent())) {
      return Optional.empty();
    }
    var dataSource = new DruidDataSource();
    dataSource.setName(name.get());
    dataSource.setUrl(url.get());
    dataSource.setUsername(username.get());
    dataSource.setPassword(password.get());
    dataSource.setDriverClassName(driverClassName.get());
    setOptionalProperties(dataSource, dataStationDefine);
    return Optional.of(dataSource);
  }

  private void setOptionalProperties(DruidDataSource dataSource, DataStationDefine dataStationDefine) {
    optionalStringProperties.forEach((key, setter) -> dataStationDefine.getProperty(String.class, key).ifPresent(value -> {
      Try.run(() -> setter.accept(dataSource, value));
    }));
    optionalIntegerProperties.forEach((key, setter) -> dataStationDefine.getProperty(Integer.class, key).ifPresent(value -> {
      setter.accept(dataSource, value);
    }));
    optionalLongProperties.forEach((key, setter) -> dataStationDefine.getProperty(Long.class, key).ifPresent(value -> {
      setter.accept(dataSource, value);
    }));
    optionalBooleanProperties.forEach((key, setter) -> dataStationDefine.getProperty(Boolean.class, key).ifPresent(value -> {
      setter.accept(dataSource, value);
    }));
  }
}
