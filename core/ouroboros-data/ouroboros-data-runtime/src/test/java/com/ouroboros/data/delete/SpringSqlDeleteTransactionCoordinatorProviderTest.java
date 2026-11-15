package com.ouroboros.data.delete;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import io.vavr.control.Try;

import com.ouroboros.data.model.DataModel;
import com.ouroboros.data.model.deletepolicy.DeleteTransactionCoordinator;
import com.ouroboros.data.station.DataStation;

class SpringSqlDeleteTransactionCoordinatorProviderTest {

  @Test
  void supportAndBuildReturnEmptyWhenDataSourceMissing() {
    var provider = new SpringSqlDeleteTransactionCoordinatorProvider();
    var noStation = mock(DataModel.class);
    when(noStation.getDataStation()).thenReturn(null);

    assertFalse(provider.support(noStation, noStation));
    assertFalse(provider.build(noStation, noStation).isPresent());
    assertFalse(provider.build(null, noStation).isPresent());
  }

  @Test
  void buildUsesSharedTransactionManagerWhenDataSourceIsSame() {
    var provider = new SpringSqlDeleteTransactionCoordinatorProvider();
    var shared = createDataSource("shared_provider");
    var source = modelWithDataSource(shared);
    var recycle = modelWithDataSource(shared);

    assertTrue(provider.support(source, recycle));
    var coordinator = provider.build(source, recycle).get();
    var result = coordinator.execute(source, recycle, () -> Try.success(1L));
    assertTrue(result.isSuccess());
    assertSame(1L, result.get());
  }

  @Test
  void buildUsesSeparateManagersWhenDataSourceIsDifferent() {
    var provider = new SpringSqlDeleteTransactionCoordinatorProvider();
    var source = modelWithDataSource(createDataSource("source_provider"));
    var recycle = modelWithDataSource(createDataSource("recycle_provider"));

    assertTrue(provider.support(source, recycle));
    var coordinator = provider.build(source, recycle).get();
    var result = coordinator.execute(source, recycle, () -> Try.success(2L));
    assertNotNull(result);
  }

  @Test
  void supportReturnsFalseWhenStationDataSourceTypeIsNotJdbc() {
    var provider = new SpringSqlDeleteTransactionCoordinatorProvider();
    var source = mock(DataModel.class);
    var station = mock(DataStation.class);
    doReturn(station).when(source).getDataStation();
    when(station.getDataSource()).thenReturn("not-a-datasource");

    var recycle = modelWithDataSource(createDataSource("recycle_station"));
    assertFalse(provider.support(source, recycle));
    assertFalse(provider.build(source, recycle).isPresent());
  }

  private static DataModel modelWithDataSource(DataSource dataSource) {
    var model = mock(DataModel.class);
    var station = mock(DataStation.class);
    doReturn(station).when(model).getDataStation();
    when(station.getDataSource()).thenReturn(dataSource);
    return model;
  }

  private static DataSource createDataSource(String name) {
    var dataSource = new DriverManagerDataSource();
    dataSource.setDriverClassName("org.h2.Driver");
    dataSource.setUrl("jdbc:h2:mem:" + name + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
    dataSource.setUsername("sa");
    dataSource.setPassword("");
    return dataSource;
  }
}
