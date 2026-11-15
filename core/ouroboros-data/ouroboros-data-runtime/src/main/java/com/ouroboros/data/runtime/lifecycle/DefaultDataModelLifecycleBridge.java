package com.ouroboros.data.runtime.lifecycle;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ouroboros.data.exception.DatabaseMigrationException;
import com.ouroboros.data.model.DataModelCenter;
import com.ouroboros.data.model.MigrationStrategy;
import com.ouroboros.data.station.DataStationCenter;
import com.ouroboros.data.station.Migratable;

public class DefaultDataModelLifecycleBridge implements DataModelLifecycleBridge {
  private static final Logger logger = LoggerFactory.getLogger(DefaultDataModelLifecycleBridge.class);

  private final Runnable dataStationRefresh;
  private final Runnable dataModelRefresh;
  private final Supplier<List<Migratable>> migratableSupplier;

  public DefaultDataModelLifecycleBridge() {
    this(DataStationCenter::refresh, DataModelCenter::refresh, DefaultDataModelLifecycleBridge::getMigratableDataStations);
  }

  DefaultDataModelLifecycleBridge(Runnable dataStationRefresh, Runnable dataModelRefresh,
                                  Supplier<List<Migratable>> migratableSupplier) {
    this.dataStationRefresh = Objects.requireNonNull(dataStationRefresh);
    this.dataModelRefresh = Objects.requireNonNull(dataModelRefresh);
    this.migratableSupplier = Objects.requireNonNull(migratableSupplier);
  }

  @Override
  public void refresh() {
    logger.info("正在刷新数据站");
    dataStationRefresh.run();
    dataModelRefresh.run();

    var migratableList = migratableSupplier.get().stream()
        .filter(ds -> ds.getMigrationStrategy() != MigrationStrategy.DISABLED)
        .collect(Collectors.toList());

    if (migratableList.isEmpty()) {
      logger.info("没有需要迁移的数据站");
      return;
    }

    logger.info("共有 {} 个数据站需要迁移（{}）", migratableList.size(),
        migratableList.stream().map(Migratable::getName).collect(Collectors.joining(",")));
    for (var dataStation : migratableList) {
      migrate(dataStation);
    }
  }

  private static List<Migratable> getMigratableDataStations() {
    return DataStationCenter.getDataStationMap().values().stream()
        .filter(Migratable.class::isInstance)
        .map(Migratable.class::cast)
        .collect(Collectors.toList());
  }

  private static void migrate(Migratable dataStation) {
    var dataStationName = dataStation.getName();

    if (dataStation.getMigrationStrategy() != MigrationStrategy.DISABLED) {
      logger.info("正在迁移数据站 {}", dataStationName);
    } else {
      return;
    }

    var migrate = dataStation.migrate();
    if (migrate.isLeft()) {
      throw new DatabaseMigrationException(String.format("数据站 %s 迁移失败", dataStationName),
          dataStationName, migrate.getLeft());
    }

    if (migrate.get()) {
      logger.info("数据站 {} 迁移成功", dataStationName);
    }
  }
}
