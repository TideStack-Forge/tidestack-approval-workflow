package com.ouroboros.data.runtime.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.ouroboros.data.exception.DataModelException;
import com.ouroboros.data.model.MigrationStrategy;
import com.ouroboros.data.station.Migratable;

import io.vavr.control.Either;

class DataModelLifecycleBridgeTest {
  @Test
  void refreshRunsStationThenModelThenMigration() {
    var calls = new ArrayList<String>();
    var bridge = new DefaultDataModelLifecycleBridge(
        () -> calls.add("station"),
        () -> calls.add("model"),
        () -> List.of(new RecordingMigratable("sql", MigrationStrategy.AUTO, calls)));

    bridge.refresh();

    assertEquals(List.of("station", "model", "migrate:sql"), calls);
  }

  @Test
  void refreshSkipsDisabledMigration() {
    var calls = new ArrayList<String>();
    var bridge = new DefaultDataModelLifecycleBridge(
        () -> calls.add("station"),
        () -> calls.add("model"),
        () -> List.of(new RecordingMigratable("sql", MigrationStrategy.DISABLED, calls)));

    bridge.refresh();

    assertEquals(List.of("station", "model"), calls);
  }

  private record RecordingMigratable(String name, MigrationStrategy strategy, List<String> calls)
      implements Migratable {
    @Override
    public String getName() {
      return name;
    }

    @Override
    public MigrationStrategy getMigrationStrategy() {
      return strategy;
    }

    @Override
    public Either<DataModelException, Boolean> migrate() {
      calls.add("migrate:" + name);
      return Either.right(true);
    }
  }
}
