package com.ouroboros.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import com.ouroboros.data.config.DataSourceConfiguration;
import com.ouroboros.data.config.DefaultSnowflakeNodeIdProviderConfig;
import com.ouroboros.data.pkgenerator.FixLengthCodingGeneratorFactory;
import com.ouroboros.data.station.DataStationDefine;

class SpringDataStationDefineProviderTest {

  @Test
  void initBuildsDefaultAndAdditionalDataStationsAndBridgeWorks() throws Exception {
    var provider = new SpringDataStationDefineProvider();
    var dataSource = createH2DataSource("station_provider_main");
    provider.dataSource = dataSource;

    var configuration = new DataSourceConfiguration() {
      @Override
      public Map<String, Map<String, Object>> getDataSourceProperties() {
        var map = new HashMap<String, Map<String, Object>>();
        var reporting = new HashMap<String, Object>();
        reporting.put("url", "jdbc:h2:mem:reporting");
        map.put("reporting", reporting);
        return map;
      }
    };
    provider.dataSourceConfiguration = configuration;
    setField(provider, "migrationStrategy", "auto");
    setField(provider, "sqlDialect", "mysql");

    invokeInit(provider);

    var defaultDefine = provider.getDataStationDefine("default").get();
    assertEquals("default", defaultDefine.getName());
    assertEquals("sql", defaultDefine.getType());
    assertSame(dataSource, defaultDefine.getProperty(DataSource.class, "springDataSource").get());
    assertEquals("auto", defaultDefine.getProperty("migrationStrategy").get());
    assertEquals("mysql", defaultDefine.getProperty("sqlDialect").get());

    var reportingDefine = provider.getDataStationDefine("reporting").get();
    assertEquals("reporting", reportingDefine.getName());
    assertEquals("jdbc:h2:mem:reporting", reportingDefine.getProperty("url").get());

    var defineMap = provider.getDataStationDefineMap();
    assertEquals(2, defineMap.size());
    assertThrows(UnsupportedOperationException.class, () -> defineMap.put("x", new DataStationDefine()));

    var bridge = new SpringDataStationDefineProvider.Bridge();
    assertTrue(bridge.getDataStationDefine("default").isPresent());
    assertEquals(2, bridge.getDataStationDefineMap().size());
  }

  @Test
  void initWithoutAdditionalConfigurationStillRegistersDefault() throws Exception {
    var provider = new SpringDataStationDefineProvider();
    provider.dataSource = createH2DataSource("station_provider_default_only");
    provider.dataSourceConfiguration = null;

    invokeInit(provider);

    assertTrue(provider.getDataStationDefine("default").isPresent());
    assertFalse(provider.getDataStationDefine("missing").isPresent());
  }

  @Test
  void dataSourceBuilderAndBridgeEmptyFallback() {
    var builder = new SpringDataStationDefineProvider.DataSourceBuilder();
    var dataSource = (DriverManagerDataSource) createH2DataSource("builder");

    var define = new DataStationDefine();
    define.setProperties(Collections.<String, Object>singletonMap("springDataSource", dataSource));
    assertSame(dataSource, builder.apply(define).get());

    assertFalse(builder.apply(new DataStationDefine()).isPresent());

    var provider = new SpringDataStationDefineProvider();
    var bridge = new SpringDataStationDefineProvider.Bridge();
    assertTrue(bridge.getDataStationDefineMap().isEmpty());
    assertFalse(bridge.getDataStationDefine("default").isPresent());
    assertTrue(provider.getDataStationDefineMap().isEmpty());
  }

  @Test
  void simpleConfigurationClassesCanBeInvoked() throws Exception {
    var dataSourceConfiguration = new DataSourceConfiguration();
    assertTrue(dataSourceConfiguration.getDataSourceProperties().isEmpty());

    var autoConfiguration = new SpringDataStationAutoConfiguration();
    var init = SpringDataStationAutoConfiguration.class.getDeclaredMethod("init");
    init.setAccessible(true);
    init.invoke(autoConfiguration);
  }

  @Test
  void runtimeAutoConfigurationIsTheSingleSpringBootEntry() {
    var springFactories = readResource("META-INF/spring.factories");
    var autoConfigurationImports = readResource("META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports");

    assertTrue(springFactories.contains(
        "org.springframework.boot.autoconfigure.EnableAutoConfiguration=com.ouroboros.data.SpringDataStationAutoConfiguration"));
    assertTrue(autoConfigurationImports.contains("com.ouroboros.data.SpringDataStationAutoConfiguration"));
    assertFalse(springFactories.contains("CodingPKGeneratorAutoConfiguration"));
    assertFalse(autoConfigurationImports.contains("CodingPKGeneratorAutoConfiguration"));
  }

  @Test
  void runtimeAutoConfigurationUsesExplicitImportsInsteadOfRootComponentScan() {
    assertFalse(SpringDataStationAutoConfiguration.class.isAnnotationPresent(ComponentScan.class));

    var importAnnotation = SpringDataStationAutoConfiguration.class.getAnnotation(Import.class);
    assertNotNull(importAnnotation);

    assertEquals(Set.of(
        SpringDataStationDefineProvider.class,
        DataSourceConfiguration.class,
        DefaultSnowflakeNodeIdProviderConfig.class,
        FixLengthCodingGeneratorFactory.class
    ), Set.of(importAnnotation.value()));
  }

  private static DataSource createH2DataSource(String name) {
    var dataSource = new DriverManagerDataSource();
    dataSource.setDriverClassName("org.h2.Driver");
    dataSource.setUrl("jdbc:h2:mem:" + name + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
    dataSource.setUsername("sa");
    dataSource.setPassword("");
    return dataSource;
  }

  private static void invokeInit(SpringDataStationDefineProvider provider) throws Exception {
    var init = SpringDataStationDefineProvider.class.getDeclaredMethod("init");
    init.setAccessible(true);
    init.invoke(provider);
  }

  private static void setField(Object target, String fieldName, Object value) throws Exception {
    var field = target.getClass().getDeclaredField(fieldName);
    field.setAccessible(true);
    field.set(target, value);
  }

  private static String readResource(String resourcePath) {
    ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
    assertNotNull(classLoader, "classLoader should exist");

    try (InputStream inputStream = classLoader.getResourceAsStream(resourcePath)) {
      assertNotNull(inputStream, "resource should exist: " + resourcePath);
      ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
      byte[] buffer = new byte[1024];
      int read;
      while ((read = inputStream.read(buffer)) != -1) {
        outputStream.write(buffer, 0, read);
      }
      return new String(outputStream.toByteArray(), StandardCharsets.UTF_8);
    } catch (Exception e) {
      throw new AssertionError("failed to read resource: " + resourcePath, e);
    }
  }
}
