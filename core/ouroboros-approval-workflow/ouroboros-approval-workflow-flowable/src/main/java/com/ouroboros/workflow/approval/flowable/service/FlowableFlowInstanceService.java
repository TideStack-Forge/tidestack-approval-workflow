package com.ouroboros.workflow.approval.flowable.service;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.concurrent.CompletableFuture;

import org.apache.commons.lang3.StringUtils;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.identitylink.api.IdentityLinkType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import io.vavr.control.Try;

import com.ouroboros.uischema.schema.SchemaNode;
import com.ouroboros.workflow.approval.FlowDefinitionService;
import com.ouroboros.workflow.approval.FlowFormService;
import com.ouroboros.workflow.approval.BuiltInFlowInstanceService;
import com.ouroboros.workflow.approval.definition.FlowDefinition;
import com.ouroboros.workflow.approval.event.InstanceEventDispatcher;
import com.ouroboros.workflow.approval.flowable.transpiler.Constants;
import com.ouroboros.workflow.approval.model.FlowDurationStatistic;
import com.ouroboros.workflow.approval.model.FlowInstanceDetail;
import com.ouroboros.workflow.approval.model.FlowInstanceStatistic;
import com.ouroboros.workflow.approval.model.FlowProcessInfo;
import com.ouroboros.workflow.approval.model.FlowTimeline;

@Service
public class FlowableFlowInstanceService implements BuiltInFlowInstanceService {
  final FlowInstanceCommandService commandService;
  final FlowInstanceQueryService queryService;

  @Autowired
  RuntimeService runtimeService;

  @Autowired
  com.ouroboros.workflow.approval.lifecycle.ApprovalCaseService approvalCaseService;

  @Autowired
  com.ouroboros.workflow.approval.flowable.event.FlowableSourceTransaction sourceTransaction;

  @Autowired
  public FlowableFlowInstanceService(FlowInstanceCommandService commandService, FlowInstanceQueryService queryService) {
    this.commandService = commandService;
    this.queryService = queryService;
  }

  public FlowableFlowInstanceService(TaskService taskService, RuntimeService runtimeService, List<FlowFormService> flowFormServices) {
    this(taskService, runtimeService, null, null, null, flowFormServices, null);
  }

  public FlowableFlowInstanceService(
      TaskService taskService,
      RuntimeService runtimeService,
      RepositoryService repositoryService,
      HistoryService historyService,
      FlowDefinitionService flowDefinitionService,
      List<FlowFormService> flowFormServices,
      InstanceEventDispatcher instanceEventDispatcher) {
    this.commandService = new FlowInstanceCommandService(
        taskService,
        runtimeService,
        repositoryService,
        historyService,
        flowDefinitionService,
        flowFormServices,
        instanceEventDispatcher);
    this.queryService = new FlowInstanceQueryService(taskService, runtimeService, repositoryService, historyService, flowFormServices);
  }

  @Override
  @Transactional(propagation = Propagation.REQUIRED)
  public Try<String> start(String flowKey, String userId, Map<String, List<String>> taskAssignee, Map<String, Object> instanceVariables,
                           Map<String, Object> formData, CompletableFuture<Void> initTaskDeferred) {
    if (sourceTransaction != null) { sourceTransaction.requireParticipation(); }
    var variables = new LinkedHashMap<String, Object>(instanceVariables == null ? Map.of() : instanceVariables);
    var submission = reserveSubmission(flowKey, variables);
    Try<String> started = commandService.start(flowKey, userId, taskAssignee, variables, formData, initTaskDeferred);
    if (submission != null) {
      if (started.isSuccess()) {
        approvalCaseService.bindExecution(submission.approvalCaseId(), submission.submissionRound(), started.get());
      } else {
        approvalCaseService.markStartFailed(submission.approvalCaseId(), submission.submissionRound(), started.getCause().getMessage());
      }
    }
    return started;
  }

  @Override
  @Transactional(propagation = Propagation.REQUIRED)
  public Try<String> start(String flowKey, String userId, Map<String, List<String>> taskAssignee, Map<String, Object> instanceVariables,
                           Map<String, Object> formData, Map<String, Object> cc) {
    return start(flowKey, userId, taskAssignee, instanceVariables, formData)
        .map(instanceId -> applyStartCc(instanceId, cc));
  }

  private com.ouroboros.workflow.approval.lifecycle.ApprovalCaseService.Submission reserveSubmission(
      String flowKey, Map<String, Object> variables) {
    Object recordType = variables.get(com.ouroboros.workflow.approval.FlowInstanceVariables.BUSINESS_RECORD_TYPE);
    Object recordId = variables.get(com.ouroboros.workflow.approval.FlowInstanceVariables.BUSINESS_RECORD_ID);
    if (recordType == null && recordId == null) { return null; }
    if (recordType == null || recordId == null || approvalCaseService == null) {
      throw new com.ouroboros.exception.BusinessException("本地发起缺少完整业务记录上下文或正式案件服务");
    }
    if (sourceTransaction != null) { sourceTransaction.requireCaseParticipation(); }
    var submission = approvalCaseService.reserveSubmission(String.valueOf(recordType), String.valueOf(recordId), flowKey);
    variables.put(com.ouroboros.workflow.approval.FlowInstanceVariables.BUSINESS_RECORD_TYPE, String.valueOf(recordType));
    variables.put(com.ouroboros.workflow.approval.FlowInstanceVariables.BUSINESS_RECORD_ID, String.valueOf(recordId));
    variables.put(com.ouroboros.workflow.approval.FlowInstanceVariables.APPROVAL_CASE_ID, submission.approvalCaseId());
    variables.put(com.ouroboros.workflow.approval.FlowInstanceVariables.SUBMISSION_ROUND, submission.submissionRound());
    return submission;
  }

  /**
   * 发起时抄送：START/START_FINISH 在发起成功后立即加入参与者；FINISH 记录到实例变量由终态监听器处理。
   */
  private String applyStartCc(String instanceId, Map<String, Object> cc) {
    if (cc == null || cc.isEmpty() || instanceId == null) {
      return instanceId;
    }
    Object rawUserIds = cc.get("userIds");
    if (!(rawUserIds instanceof List<?> userIds)) {
      return instanceId;
    }
    List<String> users = userIds.stream()
        .map(Object::toString)
        .filter(StringUtils::isNotBlank)
        .distinct()
        .toList();
    if (users.isEmpty()) {
      return instanceId;
    }
    String position = cc.get("position") == null ? "START" : String.valueOf(cc.get("position"));
    if ("START".equals(position) || "START_FINISH".equals(position)) {
      users.forEach(userId -> runtimeService.addUserIdentityLink(instanceId, userId, IdentityLinkType.PARTICIPANT));
    }
    if ("FINISH".equals(position) || "START_FINISH".equals(position)) {
      runtimeService.setVariable(instanceId, Constants.Variable.START_CC_FINISH_USERS, users);
    }
    return instanceId;
  }

  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Try<Void> withdraw(String instanceId, String userId, String comment) {
    if (sourceTransaction != null) { sourceTransaction.requireParticipation(); }
    return commandService.withdraw(instanceId, userId, comment);
  }

  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Try<Void> pause(String instanceId, String userId, String comment) {
    if (sourceTransaction != null) { sourceTransaction.requireParticipation(); }
    return commandService.pause(instanceId, userId, comment);
  }

  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Try<Void> resume(String instanceId, String userId) {
    if (sourceTransaction != null) { sourceTransaction.requireParticipation(); }
    return commandService.resume(instanceId, userId);
  }

  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Try<Void> cancel(String instanceId, String userId, String comment) {
    if (sourceTransaction != null) { sourceTransaction.requireParticipation(); }
    return commandService.cancel(instanceId, userId, comment);
  }

  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Try<Void> complete(String instanceId, String userId, Boolean approved, String comment) {
    if (sourceTransaction != null) { sourceTransaction.requireParticipation(); }
    return commandService.complete(instanceId, userId, approved, comment);
  }

  @Override
  public List<FlowTimeline> getTimeLine(String instanceId) {
    return queryService.getTimeLine(instanceId);
  }

  @Override
  public Try<SchemaNode> getInitialForm(String flowKey) {
    return queryService.getInitialForm(flowKey);
  }

  @Override
  public List<FlowInstanceStatistic> getStatistic(String keyword, String status, Integer offset, Integer limit) {
    return queryService.getStatistic(keyword, status, offset, limit);
  }

  @Override
  public Long getStatisticCount(String keyword, String status) {
    return queryService.getStatisticCount(keyword, status);
  }

  @Override
  public FlowDurationStatistic getDurationStatistic(String flowKey, java.util.Date from, java.util.Date to) {
    return queryService.getDurationStatistic(flowKey, from, to);
  }

  @Override
  public List<FlowInstanceDetail> getInstances(String flowKey, String status, Integer offset, Integer limit) {
    return queryService.getInstances(flowKey, status, offset, limit);
  }

  @Override
  public Long getInstancesCount(String flowKey, String status) {
    return queryService.getInstancesCount(flowKey, status);
  }

  @Override
  public Try<Map<String, FlowProcessInfo>> getProcessInfoByApprovalUrls(
      String flowKey, List<String> approvalUrls) {
    return Try.success(queryService.getProcessInfoByApprovalUrls(flowKey, approvalUrls));
  }

  @Override
  public Try<FlowProcessInfo> getProcessInfo(String instanceId) {
    return queryService.getProcessInfo(instanceId);
  }

  @Override
  public FlowDefinition getInstanceFlowDefinition(String instanceId) {
    return queryService.getInstanceFlowDefinition(instanceId);
  }

  @Override
  public Try<JsonNode> getInstanceFlowDefinitionV2(String instanceId) {
    return queryService.getInstanceFlowDefinitionV2(instanceId);
  }

  @Override
  public List<String> getActiveActivityIds(String instanceId) {
    return queryService.getActiveActivityIds(instanceId);
  }

}
