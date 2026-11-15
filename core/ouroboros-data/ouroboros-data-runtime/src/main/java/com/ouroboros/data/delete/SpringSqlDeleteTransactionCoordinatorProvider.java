package com.ouroboros.data.delete;

import java.util.Optional;

import javax.sql.DataSource;

import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import com.ouroboros.data.model.DataModel;
import com.ouroboros.data.model.deletepolicy.DeleteTransactionCoordinator;
import com.ouroboros.data.model.deletepolicy.DeleteTransactionCoordinatorProvider;

public class SpringSqlDeleteTransactionCoordinatorProvider implements DeleteTransactionCoordinatorProvider {

  @Override
  public boolean support(DataModel sourceModel, DataModel recycleModel) {
    return extractDataSource(sourceModel).isPresent() && extractDataSource(recycleModel).isPresent();
  }

  @Override
  public Optional<DeleteTransactionCoordinator> build(DataModel sourceModel, DataModel recycleModel) {
    Optional<DataSource> sourceDataSource = extractDataSource(sourceModel);
    Optional<DataSource> recycleDataSource = extractDataSource(recycleModel);
    if (!sourceDataSource.isPresent() || !recycleDataSource.isPresent()) {
      return Optional.empty();
    }

    boolean sharedTransactionManager = sourceDataSource.get() == recycleDataSource.get();
    DataSourceTransactionManager sourceTransactionManager = newTransactionManager(sourceDataSource.get());
    DataSourceTransactionManager recycleTransactionManager = sharedTransactionManager
        ? sourceTransactionManager
        : newTransactionManager(recycleDataSource.get());

    return Optional.of(new SpringSqlDeleteTransactionCoordinator(
        sourceTransactionManager,
        recycleTransactionManager,
        sharedTransactionManager
    ));
  }

  private Optional<DataSource> extractDataSource(DataModel model) {
    return Optional.ofNullable(model)
        .map(DataModel::getDataStation)
        .map(dataStation -> dataStation.getDataSource())
        .filter(DataSource.class::isInstance)
        .map(DataSource.class::cast);
  }

  private DataSourceTransactionManager newTransactionManager(DataSource dataSource) {
    DataSourceTransactionManager transactionManager = new DataSourceTransactionManager(dataSource);
    transactionManager.setNestedTransactionAllowed(true);
    return transactionManager;
  }
}
