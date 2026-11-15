package com.ouroboros.data.delete;

import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import io.vavr.control.Try;

import com.ouroboros.data.model.DataModel;
import com.ouroboros.data.model.deletepolicy.DeleteTransactionCoordinator;

public class SpringSqlDeleteTransactionCoordinator implements DeleteTransactionCoordinator {
  private static final Logger logger = LoggerFactory.getLogger(SpringSqlDeleteTransactionCoordinator.class);

  private final PlatformTransactionManager sourceTransactionManager;
  private final PlatformTransactionManager recycleTransactionManager;
  private final boolean sharedTransactionManager;

  public SpringSqlDeleteTransactionCoordinator(PlatformTransactionManager sourceTransactionManager,
                                               PlatformTransactionManager recycleTransactionManager,
                                               boolean sharedTransactionManager) {
    this.sourceTransactionManager = sourceTransactionManager;
    this.recycleTransactionManager = recycleTransactionManager;
    this.sharedTransactionManager = sharedTransactionManager;
  }

  @Override
  public <T> Try<T> execute(DataModel sourceModel, DataModel recycleModel, Supplier<Try<T>> work) {
    return sharedTransactionManager ? executeWithSharedManager(work) : executeWithSeparateManagers(work);
  }

  private <T> Try<T> executeWithSharedManager(Supplier<Try<T>> work) {
    TransactionStatus outerStatus = null;
    TransactionStatus nestedStatus = null;
    boolean outerCompleted = false;
    boolean nestedCompleted = false;

    try {
      outerStatus = sourceTransactionManager.getTransaction(requiredDefinition());
      nestedStatus = sourceTransactionManager.getTransaction(nestedDefinition());

      Try<T> workResult = safeRun(work);
      if (workResult.isFailure()) {
        rollbackQuietly(sourceTransactionManager, nestedStatus, nestedCompleted);
        rollbackQuietly(sourceTransactionManager, outerStatus, outerCompleted);
        return Try.failure(workResult.getCause());
      }

      sourceTransactionManager.commit(nestedStatus);
      nestedCompleted = true;
      sourceTransactionManager.commit(outerStatus);
      outerCompleted = true;
      return workResult;
    } catch (Throwable throwable) {
      rollbackQuietly(sourceTransactionManager, nestedStatus, nestedCompleted);
      rollbackQuietly(sourceTransactionManager, outerStatus, outerCompleted);
      return Try.failure(throwable);
    }
  }

  private <T> Try<T> executeWithSeparateManagers(Supplier<Try<T>> work) {
    TransactionStatus recycleStatus = null;
    TransactionStatus sourceStatus = null;
    boolean recycleCompleted = false;
    boolean sourceCompleted = false;

    try {
      recycleStatus = recycleTransactionManager.getTransaction(requiredDefinition());
      sourceStatus = sourceTransactionManager.getTransaction(requiredDefinition());

      Try<T> workResult = safeRun(work);
      if (workResult.isFailure()) {
        rollbackQuietly(sourceTransactionManager, sourceStatus, sourceCompleted);
        rollbackQuietly(recycleTransactionManager, recycleStatus, recycleCompleted);
        return Try.failure(workResult.getCause());
      }

      /*
       * Cross-data-source coordination here is best-effort rather than XA:
       * the recycle-bin transaction is committed before the source transaction.
       */
      recycleTransactionManager.commit(recycleStatus);
      recycleCompleted = true;
      sourceTransactionManager.commit(sourceStatus);
      sourceCompleted = true;
      return workResult;
    } catch (Throwable throwable) {
      rollbackQuietly(sourceTransactionManager, sourceStatus, sourceCompleted);
      rollbackQuietly(recycleTransactionManager, recycleStatus, recycleCompleted);
      return Try.failure(throwable);
    }
  }

  private DefaultTransactionDefinition requiredDefinition() {
    DefaultTransactionDefinition definition = new DefaultTransactionDefinition();
    definition.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
    return definition;
  }

  private DefaultTransactionDefinition nestedDefinition() {
    DefaultTransactionDefinition definition = new DefaultTransactionDefinition();
    definition.setPropagationBehavior(TransactionDefinition.PROPAGATION_NESTED);
    return definition;
  }

  private <T> Try<T> safeRun(Supplier<Try<T>> work) {
    try {
      return work.get();
    } catch (Throwable throwable) {
      return Try.failure(throwable);
    }
  }

  private void rollbackQuietly(PlatformTransactionManager transactionManager, TransactionStatus status, boolean completed) {
    if (transactionManager == null || status == null || completed) {
      return;
    }
    try {
      transactionManager.rollback(status);
    } catch (Throwable rollbackFailure) {
      logger.warn("回滚删除事务失败", rollbackFailure);
    }
  }
}
