package com.ouroboros.workflow.approval;

import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;

import com.fasterxml.jackson.databind.JsonNode;
import io.vavr.control.Try;

import com.ouroboros.exception.PlatformException;
import com.ouroboros.uischema.schema.SchemaNode;
import com.ouroboros.workflow.approval.definition.FlowDefinition;
import com.ouroboros.workflow.approval.model.FlowDurationStatistic;
import com.ouroboros.workflow.approval.model.FlowInstanceDetail;
import com.ouroboros.workflow.approval.model.FlowInstanceStatistic;
import com.ouroboros.workflow.approval.model.FlowProcessInfo;
import com.ouroboros.workflow.approval.model.FlowTimeline;

public interface FlowInstanceService {
  /**
   * 统一发起入口。调用方不选择引擎或接入模式；实际路由由流程映射决定。
   * 返回平台执行引用与真实启动状态，任务处理能力独立表达。
   */
  default Try<FlowStartResult> start(FlowStartCommand command) {
    Try<String> started = command.cc() == null
        ? start(command.flowKey(), command.userId(), command.taskAssignee(), command.instanceVariables(), command.formData())
        : start(command.flowKey(), command.userId(), command.taskAssignee(), command.instanceVariables(), command.formData(), command.cc());
    return started.map(FlowStartResult::started);
  }

  /**
   * 开始一个流程
   *
   * @param flowKey           流程定义Key（ID）
   * @param userId            启动流程的用户ID
   * @param taskAssignee      用户自选的任务候选人
   * @param instanceVariables 自定义流程变量
   * @return 平台执行引用；外部异步启动时仅代表已受理，不代表存在本地任务
   */
  default Try<String> start(
      String flowKey,
      String userId,
      Map<String, List<String>> taskAssignee,
      Map<String, Object> instanceVariables) {
    return start(flowKey, userId, taskAssignee, instanceVariables, null);
  }

  /**
   * 开始一个流程
   *
   * @param flowKey      流程定义Key（ID）
   * @param userId       启动流程的用户ID
   * @param taskAssignee 用户自选的任务候选人
   * @return 平台执行引用；外部异步启动时仅代表已受理，不代表存在本地任务
   */
  default Try<String> start(String flowKey, String userId, Map<String, List<String>> taskAssignee) {
    return start(flowKey, userId, taskAssignee, null, null);
  }

  /**
   * 开始一个流程
   *
   * @param flowKey           流程定义Key（ID）
   * @param userId            启动流程的用户ID
   * @param taskAssignee      用户自选的任务候选人
   * @param instanceVariables 自定义流程变量
   * @param formData          初始表单数据
   * @return 平台执行引用；外部异步启动时仅代表已受理，不代表存在本地任务
   */
  default Try<String> start(
      String flowKey,
      String userId,
      Map<String, List<String>> taskAssignee,
      Map<String, Object> instanceVariables,
      Map<String, Object> formData) {
    return start(
        flowKey,
        userId,
        taskAssignee,
        instanceVariables,
        formData,
        CompletableFuture.runAsync(() -> Try.run(() -> Thread.sleep(5000))));
  }

  /**
   * 开始一个流程
   *
   * @param flowKey           流程定义Key（ID）
   * @param userId            启动流程的用户ID
   * @param taskAssignee      用户自选的任务候选人
   * @param instanceVariables 自定义流程变量
   * @param formData          初始表单数据
   * @param initTaskDeferred  开启流程后初始任务提交延迟
   * @return 平台执行引用；外部异步启动时仅代表已受理，不代表存在本地任务
   */
  Try<String> start(
      String flowKey,
      String userId,
      Map<String, List<String>> taskAssignee,
      Map<String, Object> instanceVariables,
      Map<String, Object> formData,
      CompletableFuture<Void> initTaskDeferred);

  /**
   * 开始一个流程（带发起时抄送）。
   *
   * @param flowKey           流程定义Key（ID）
   * @param userId            启动流程的用户ID
   * @param taskAssignee      用户自选的任务候选人
   * @param instanceVariables 自定义流程变量
   * @param formData          初始表单数据
   * @param cc                发起时抄送配置 {@code {"userIds": List<String>, "position": "START"|"FINISH"|"START_FINISH"}}
   * @return 平台执行引用；外部异步启动时仅代表已受理，不代表存在本地任务
   */
  default Try<String> start(
      String flowKey,
      String userId,
      Map<String, List<String>> taskAssignee,
      Map<String, Object> instanceVariables,
      Map<String, Object> formData,
      Map<String, Object> cc) {
    return start(flowKey, userId, taskAssignee, instanceVariables, formData);
  }

  /**
   * 发起人撤回：仅发起人可撤回审批中的实例。
   *
   * @param instanceId 流程实例ID
   * @param userId     操作人（必须为发起人）
   * @param comment    撤回原因
   * @return 无
   */
  default Try<Void> withdraw(String instanceId, String userId, String comment) {
    return cancel(instanceId, userId, comment);
  }

  /**
   * 暂停一个流程
   *
   * @param instanceId 流程实例ID
   * @param userId     操作人
   * @return 无
   */
  default Try<Void> pause(String instanceId, String userId) {
    return pause(instanceId, userId, null);
  }

  /**
   * 暂停一个流程
   *
   * @param instanceId 流程实例ID
   * @param userId     操作人
   * @param comment    暂停原因
   * @return 无
   */
  Try<Void> pause(String instanceId, String userId, String comment);

  /**
   * 恢复一个暂停的流程
   *
   * @param instanceId 流程实例ID
   * @param userId     操作人
   * @return 无
   */
  Try<Void> resume(String instanceId, String userId);

  /**
   * 取消一个未完成的流程
   *
   * @param instanceId 流程实例ID
   * @param userId     操作人
   * @return 无
   */
  default Try<Void> cancel(String instanceId, String userId) {
    return cancel(instanceId, userId, null);
  }

  /**
   * 取消一个未完成的流程
   *
   * @param instanceId 流程实例ID
   * @param userId     操作人
   * @param comment    取消原因
   * @return 无
   */
  Try<Void> cancel(String instanceId, String userId, String comment);

  /**
   * 完成一个未完成的流程
   *
   * @param instanceId 流程实例ID
   * @param userId     操作人
   * @param approved   审批结果是否为通过
   * @return 无
   */
  default Try<Void> complete(String instanceId, String userId, Boolean approved) {
    return complete(instanceId, userId, approved, null);
  }

  /**
   * 完成一个未完成的流程
   *
   * @param instanceId 流程实例ID
   * @param userId     操作人
   * @param approved   审批结果是否为通过
   * @param comment    原因
   * @return 无
   */
  Try<Void> complete(String instanceId, String userId, Boolean approved, String comment);

  /**
   * 获取流程时间线
   *
   * @param instanceId 流程实例ID
   * @return 时间线
   */
  List<FlowTimeline> getTimeLine(String instanceId);

  /**
   * 获取流程初始表单Schema
   *
   * @param flowKey 流程定义Key（ID）
   * @return 表单Schema
   */
  Try<SchemaNode> getInitialForm(String flowKey);

  /**
   * 分页获取流程实例统计
   *
   * @param keyword 关键词
   * @param status  状态（deployed、offline）
   * @param offset  跳过多少条
   * @param limit   获取多少条
   * @return 流程实例统计
   */
  List<FlowInstanceStatistic> getStatistic(String keyword, String status, Integer offset, Integer limit);

  /**
   * 获取流程实例数量
   *
   * @param keyword 关键词
   * @param status  状态（deployed、offline）
   * @return 数量
   */
  Long getStatisticCount(String keyword, String status);

  /**
   * 获取流程、节点、审批人的耗时统计。
   *
   * @param flowKey 流程定义Key（ID）
   * @param from    统计开始时间（含）
   * @param to      统计结束时间（含）
   * @return 耗时统计
   */
  FlowDurationStatistic getDurationStatistic(String flowKey, java.util.Date from, java.util.Date to);

  /**
   * 分页获取流程实例
   *
   * @param flowKey 流程定义Key（ID）
   * @param status  状态（running、suspended、finished）
   * @param offset  跳过多少条
   * @param limit   获取多少条
   * @return 流程实例统计
   */
  List<FlowInstanceDetail> getInstances(String flowKey, String status, Integer offset, Integer limit);

  /**
   * 获取流程实例数量
   *
   * @param flowKey 流程定义Key（ID）
   * @param status  状态（running、suspended、finished）
   * @return 数量
   */
  Long getInstancesCount(String flowKey, String status);

  /**
   * 按审批地址反查流程信息（外部集成目录能力，当前只有 Flowable 实现）。
   *
   * <p>返回 {@code Try.failure}，与同接口的 {@link #getInstanceFlowDefinitionV2} 保持同一失败协议；
   * 此前这里直接抛 {@code UnsupportedOperationException}，既脱离 Ouroboros 异常体系，
   * 也让调用方无法用同一套方式处理"引擎不支持"。
   */
  default Try<Map<String, FlowProcessInfo>> getProcessInfoByApprovalUrls(
      String flowKey, List<String> approvalUrls) {
    return Try.failure(new PlatformException("[" + ApprovalWorkflowErrorCodes.WORKFLOW_ENGINE_NOT_FOUND
        + "] 当前流程引擎不支持按审批地址查询流程信息"));
  }

  /** 读取执行状态及可用的页面能力；执行引用不代表存在本地任务。 */
  default Try<FlowProcessInfo> getProcessInfo(String instanceId) {
    return Try.failure(new PlatformException("[" + ApprovalWorkflowErrorCodes.WORKFLOW_ENGINE_NOT_FOUND
        + "] 当前流程引擎不支持读取流程信息"));
  }

  /** 带业务记录类型的列表查询；旧引擎继续沿审批地址查询。 */
  default Try<Map<String, FlowProcessInfo>> getProcessInfoByApprovalUrls(
      String flowKey, List<String> approvalUrls, String businessRecordType) {
    return getProcessInfoByApprovalUrls(flowKey, approvalUrls);
  }

  /**
   * 获取流程实例的的流程定义（流程发起时的版本）
   *
   * @param instanceId 流程实例ID
   * @return 流程定义
   */
  FlowDefinition getInstanceFlowDefinition(String instanceId);

  default Try<JsonNode> getInstanceFlowDefinitionV2(String instanceId) {
    return Try.failure(new PlatformException("[" + ApprovalWorkflowErrorCodes.WORKFLOW_ENGINE_NOT_FOUND
        + "] 当前流程引擎不支持读取流程定义 V2 文档"));
  }

  default List<String> getActiveActivityIds(String instanceId) {
    return Collections.emptyList();
  }

}
