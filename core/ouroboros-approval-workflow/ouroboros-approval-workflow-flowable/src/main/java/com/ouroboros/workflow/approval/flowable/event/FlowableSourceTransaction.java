package com.ouroboros.workflow.approval.flowable.event;

import org.flowable.spring.SpringProcessEngineConfiguration;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.ouroboros.data.model.TypedDataModelCenter;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.model.annotation.AllowDirectJDBC;
import com.ouroboros.workflow.approval.event.FlowEventOutbox;

/** Proves that engine writes and source facts have the same physical commit owner before executing a command. */
@Component
public class FlowableSourceTransaction {
  private final SpringProcessEngineConfiguration configuration;

  public FlowableSourceTransaction(SpringProcessEngineConfiguration configuration) { this.configuration = configuration; }

  public void requireCaseParticipation() {
    Object outbox = transactionResource(FlowEventOutbox.class);
    if (outbox != transactionResource(com.ouroboros.workflow.approval.lifecycle.ApprovalCase.class)) {
      throw new BusinessException("审批案件与引擎源事实不属于同一提交资源");
    }
  }

  private Object transactionResource(Class<?> type) {
    return TypedDataModelCenter.getDataModel(type)
        .orElseThrow(() -> new BusinessException("审批源模型未注册: " + type.getSimpleName()))
        .getDataModel().getAdapter().requireTransactionResource()
        .getOrElseThrow(error -> new BusinessException("审批源模型未参与实际事务: " + type.getSimpleName(), error));
  }

  @AllowDirectJDBC("verify Flowable and source facts share the same Spring-bound JDBC transaction resource; no SQL execution")
  public void requireParticipation() {
    if (!TransactionSynchronizationManager.isActualTransactionActive() || configuration.getDataSource() == null) {
      throw new BusinessException("审批引擎命令必须参与实际源事务");
    }
    var source = TypedDataModelCenter.getDataModel(String.class, FlowEventOutbox.class)
        .orElseThrow(() -> new BusinessException("审批源事实模型未注册"));
    Object sourceResource = source.getDataModel().getAdapter().requireTransactionResource()
        .getOrElseThrow(error -> new BusinessException("审批源事实未参与实际事务", error));
    var dataSource = configuration.getDataSource() instanceof TransactionAwareDataSourceProxy proxy
        ? proxy.getTargetDataSource() : configuration.getDataSource();
    var connection = DataSourceUtils.getConnection(dataSource);
    try {
      if (!DataSourceUtils.isConnectionTransactional(connection, dataSource)
          || !(sourceResource instanceof java.sql.Connection sourceConnection)
          || DataSourceUtils.getTargetConnection(connection) != DataSourceUtils.getTargetConnection(sourceConnection)) {
        throw new BusinessException("审批引擎与源事实不属于同一提交资源");
      }
    } finally {
      DataSourceUtils.releaseConnection(connection, dataSource);
    }
  }
}
