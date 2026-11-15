package com.ouroboros.data.delete;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import io.vavr.control.Try;

import com.ouroboros.data.model.DataModel;

public class SpringSqlDeleteTransactionCoordinatorTest {

  @Test
  public void testShouldRollbackSameSourceWorkWhenLaterDeleteFails() {
    DriverManagerDataSource dataSource = new DriverManagerDataSource();
    dataSource.setDriverClassName("org.h2.Driver");
    dataSource.setUrl("jdbc:h2:mem:delete_tx_same;MODE=MySQL;DB_CLOSE_DELAY=-1");
    dataSource.setUsername("sa");
    dataSource.setPassword("");

    JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
    jdbcTemplate.execute("drop table if exists source_records");
    jdbcTemplate.execute("drop table if exists recycle_records");
    jdbcTemplate.execute("create table source_records (id varchar(64) primary key, name varchar(64))");
    jdbcTemplate.execute("create table recycle_records (id varchar(64), source_model varchar(128))");
    jdbcTemplate.update("insert into source_records (id, name) values (?, ?)", "A1", "Alice");

    DataSourceTransactionManager transactionManager = new DataSourceTransactionManager(dataSource);
    transactionManager.setNestedTransactionAllowed(true);
    SpringSqlDeleteTransactionCoordinator coordinator =
        new SpringSqlDeleteTransactionCoordinator(transactionManager, transactionManager, true);

    Try<Long> result = coordinator.execute(mock(DataModel.class), mock(DataModel.class), () -> {
      jdbcTemplate.update("insert into recycle_records (id, source_model) values (?, ?)", "A1", "demo.User");
      jdbcTemplate.update("delete from source_records where id = ?", "A1");
      return Try.failure(new IllegalStateException("boom"));
    });

    assertTrue(result.isFailure());
    assertEquals(1, jdbcTemplate.queryForObject("select count(*) from source_records where id = 'A1'", Integer.class).intValue());
    assertEquals(0, jdbcTemplate.queryForObject("select count(*) from recycle_records", Integer.class).intValue());
  }

  @Test
  public void testShouldOpenRollbackInReverseOrderForCrossSourceFailure() {
    List<String> events = new ArrayList<String>();
    PlatformTransactionManager recycleManager = new RecordingTransactionManager("recycle", events);
    PlatformTransactionManager sourceManager = new RecordingTransactionManager("source", events);
    SpringSqlDeleteTransactionCoordinator coordinator =
        new SpringSqlDeleteTransactionCoordinator(sourceManager, recycleManager, false);

    Try<Long> result = coordinator.execute(mock(DataModel.class), mock(DataModel.class), () -> {
      events.add("work:recycle");
      events.add("work:source");
      return Try.failure(new IllegalStateException("boom"));
    });

    assertTrue(result.isFailure());
    assertEquals("begin:recycle", events.get(0));
    assertEquals("begin:source", events.get(1));
    assertEquals("work:recycle", events.get(2));
    assertEquals("work:source", events.get(3));
    assertEquals("rollback:source", events.get(4));
    assertEquals("rollback:recycle", events.get(5));
  }

  private static class RecordingTransactionManager implements PlatformTransactionManager {
    private final String name;
    private final List<String> events;

    private RecordingTransactionManager(String name, List<String> events) {
      this.name = name;
      this.events = events;
    }

    @Override
    public TransactionStatus getTransaction(TransactionDefinition definition) {
      events.add("begin:" + name);
      return new SimpleTransactionStatus();
    }

    @Override
    public void commit(TransactionStatus status) {
      events.add("commit:" + name);
    }

    @Override
    public void rollback(TransactionStatus status) {
      events.add("rollback:" + name);
    }
  }
}
