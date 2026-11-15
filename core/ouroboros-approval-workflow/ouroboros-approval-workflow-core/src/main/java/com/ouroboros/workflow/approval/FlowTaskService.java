package com.ouroboros.workflow.approval;

import java.util.List;
import java.util.Map;

import io.vavr.control.Try;

import com.ouroboros.uischema.schema.SchemaNode;
import com.ouroboros.workflow.approval.model.FlowTaskDetail;

public interface FlowTaskService {
  /**
   * 审批通过
   *
   * @param taskId
   * @param userId
   */
  default Try<Void> approve(String taskId, String userId) {
    return approve(taskId, userId, null, null, null);
  }

  /**
   * 审批通过
   *
   * @param taskId
   * @param userId
   * @param payload
   * @param <T>
   */
  default <T> Try<Void> approve(String taskId, String userId, T payload) {
    return approve(taskId, userId, null, null, payload);
  }

  /**
   * 审批通过
   *
   * @param taskId
   * @param userId
   * @param comment
   * @param payload
   * @param <T>
   */
  default <T> Try<Void> approve(String taskId, String userId, String comment, T payload) {
    return approve(taskId, userId, comment, null, payload);
  }

  default <T> Try<Void> approve(String taskId, String userId, String comment, Map<String, Object> formData, T payload) {
    return doAction(taskId, "approve", userId, comment, formData, payload);
  }

  /**
   * 办理完成
   *
   * @param taskId
   * @param userId
   */
  default Try<Void> complete(String taskId, String userId) {
    return complete(taskId, userId, null, null);
  }

  /**
   * 办理完成
   *
   * @param taskId
   * @param userId
   * @param comment
   * @param formData
   */
  default Try<Void> complete(String taskId, String userId, String comment, Map<String, Object> formData) {
    return doAction(taskId, "complete", userId, comment, formData, null);
  }

  /**
   * 审批拒绝
   *
   * @param taskId
   * @param userId
   */
  default Try<Void> reject(String taskId, String userId) {
    return reject(taskId, userId, null);
  }

  /**
   * 审批拒绝
   *
   * @param taskId
   * @param userId
   * @param comment
   */
  default Try<Void> reject(String taskId, String userId, String comment) {
    return doAction(taskId, "reject", userId, comment, null, null);
  }

  /**
   * 审批回退
   *
   * @param taskId
   * @param userId
   */
  default Try<Void> rollback(String taskId, String userId) {
    return rollback(taskId, userId, null);
  }

  /**
   * 审批回退
   *
   * @param taskId
   * @param userId
   * @param comment
   */
  default Try<Void> rollback(String taskId, String userId, String comment) {
    return doAction(taskId, "rollback", userId, comment, null, null);
  }

  /**
   * 自定义动作
   *
   * @param taskId
   * @param actionType
   * @param userId
   * @param comment
   * @param formData
   * @param payload
   */
  <T> Try<Void> doAction(
      String taskId,
      String actionType,
      String userId,
      String comment,
      Map<String, Object> formData,
      T payload);

  /**
   * 催办当前处理人（可选动作；未实现的引擎返回能力不支持失败）。
   *
   * @param taskId 任务ID
   * @param userId 发起人或当前处理人
   * @return 无
   */
  default Try<Void> remind(String taskId, String userId) {
    return Try.failure(new com.ouroboros.exception.PlatformException(
        "[" + ApprovalWorkflowErrorCodes.WORKFLOW_REMIND_UNSUPPORTED + "] 当前流程引擎不支持催办动作"));
  }

  /**
   * 获取任务的payload
   *
   * @param taskId
   * @param clazz
   * @param <T>
   * @return
   */
  <T> T getPayload(String taskId, Class<T> clazz);

  /**
   * 获取任务的表单数据
   *
   * @param taskId
   * @return
   */
  Map<String, Object> getTaskFormData(String taskId);

  /**
   * 获取任务的意见
   *
   * @param taskId
   * @return
   */
  String getComment(String taskId);

  /**
   * 设置任务处理人
   *
   * @param taskId
   * @param userId
   */
  void setTaskAssignee(String taskId, String userId);

  Try<SchemaNode> getTaskForm(String taskId);

  List<FlowTaskDetail> getTaskList(String userId);

  List<FlowTaskDetail> getTaskList(String userId, Integer offset, Integer limit);

  List<FlowTaskDetail> getTaskList(String userId, String flowKey);

  List<FlowTaskDetail> getTaskList(String userId, String flowKey, Integer offset, Integer limit);

  Long getTaskCount(String userId);

  Long getTaskCount(String userId, String flowKey);

  List<FlowTaskDetail> getHistoryTaskList(String userId, Integer offset, Integer limit);

  List<FlowTaskDetail> getHistoryTaskList(String userId, String flowKey, Integer offset, Integer limit);

  Long getHistoryTaskCount(String userId);

  Long getHistoryTaskCount(String userId, String flowKey);

  List<String> getCandidateUsers(String taskId);

  void setCandidateUsers(String taskId, List<String> userIds);

  String getTaskAssignee(String taskId);

  List<FlowTaskDetail> getAllActiveTaskList();

  FlowTaskDetail getTaskDetail(String taskId);
}
