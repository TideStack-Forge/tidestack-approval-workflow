package com.ouroboros.workflow.approval.flowable.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.flowable.task.service.impl.persistence.entity.TaskEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ouroboros.exception.PlatformException;
import com.ouroboros.workflow.approval.flowable.transpiler.Constants;

@Component
public class FlowableAddSignTaskManager {
  @Autowired
  TaskService taskService;
  @Autowired
  RuntimeService runtimeService;

  public String createBeforeCurrentGroup(Task sourceTask, List<String> signerUserIds) {
    var normalizedSigners = normalizeSigners(signerUserIds);
    if (normalizedSigners.isEmpty()) {
      throw new PlatformException("加签目标用户不能为空");
    }

    var sourceAssignee = sourceTask.getAssignee();
    if (StringUtils.isBlank(sourceAssignee)) {
      throw new PlatformException("任务未被认领，无法加签");
    }
    if (normalizedSigners.size() == 1 && StringUtils.equals(normalizedSigners.get(0), sourceAssignee)) {
      throw new PlatformException("加签目标用户不能仅包含当前审批人");
    }

    var groupId = initializeGroup(sourceTask, normalizedSigners, Constants.Value.ADD_SIGN_POSITION_BEFORE_CURRENT);
    taskService.setVariableLocal(sourceTask.getId(), Constants.Variable.ADD_SIGN_WAITING, Boolean.TRUE);
    taskService.setVariableLocal(sourceTask.getId(), Constants.Variable.ADD_SIGN_GROUP_ID, groupId);

    createAddSignTasks(
        sourceTask.getProcessInstanceId(),
        sourceTask.getProcessDefinitionId(),
        sourceTask.getName(),
        sourceTask.getId(),
        groupId,
        normalizedSigners);
    return groupId;
  }

  public String prepareAfterCurrentGroup(Task sourceTask, List<String> signerUserIds) {
    var normalizedSigners = normalizeSigners(signerUserIds);
    if (normalizedSigners.isEmpty()) {
      throw new PlatformException("加签目标用户不能为空");
    }

    var groupId = initializeGroup(sourceTask, normalizedSigners, Constants.Value.ADD_SIGN_POSITION_AFTER_CURRENT);
    var sourceNodeId = sourceTask.getTaskDefinitionKey();
    taskService.setVariable(
        sourceTask.getId(),
        Constants.Variable.addSignAfterCurrentGateActiveVariableName(sourceNodeId),
        Boolean.TRUE);
    taskService.setVariableLocal(sourceTask.getId(), Constants.Variable.ADD_SIGN_GROUP_ID, groupId);
    return groupId;
  }

  public void activateAfterCurrentGroup(String processInstanceId, String groupId) {
    var signers = readGroupSigners(processInstanceId, groupId);
    var processDefinitionId = runtimeService.getVariable(
        processInstanceId,
        groupVar(groupId, Constants.Variable.ADD_SIGN_GROUP_PROCESS_DEFINITION_ID_SUFFIX),
        String.class);
    var sourceTaskName = runtimeService.getVariable(
        processInstanceId,
        groupVar(groupId, Constants.Variable.ADD_SIGN_GROUP_SOURCE_TASK_NAME_SUFFIX),
        String.class);
    var sourceTaskId = runtimeService.getVariable(
        processInstanceId,
        groupVar(groupId, Constants.Variable.ADD_SIGN_GROUP_SOURCE_TASK_ID_SUFFIX),
        String.class);
    createAddSignTasks(processInstanceId, processDefinitionId, sourceTaskName, sourceTaskId, groupId, signers);
  }

  public void onAddSignTaskApproved(Task addSignTask) {
    var groupId = taskService.getVariableLocal(addSignTask.getId(), Constants.Variable.ADD_SIGN_GROUP_ID, String.class);
    if (StringUtils.isBlank(groupId)) {
      throw new PlatformException("加签任务缺少分组信息");
    }

    taskService.complete(addSignTask.getId());
    var processInstanceId = addSignTask.getProcessInstanceId();
    var completedName = groupVar(groupId, Constants.Variable.ADD_SIGN_GROUP_COMPLETED_SUFFIX);
    var completed = intVar(processInstanceId, completedName);
    runtimeService.setVariable(processInstanceId, completedName, completed + 1);

    if (isAddSignGroupComplete(processInstanceId, groupId)) {
      handleGroupCompletion(processInstanceId, groupId);
    }
  }

  public void onResumeTaskApproved(Task resumeTask) {
    var sourceTaskId = taskService.getVariableLocal(
        resumeTask.getId(),
        Constants.Variable.ADD_SIGN_RESUME_SOURCE_TASK_ID,
        String.class);
    if (StringUtils.isBlank(sourceTaskId)) {
      throw new PlatformException("恢复任务缺少来源任务信息");
    }

    var sourceTask = taskService.createTaskQuery().taskId(sourceTaskId).active().singleResult();
    if (sourceTask == null) {
      throw new PlatformException("来源任务不存在或已结束: " + sourceTaskId);
    }

    taskService.complete(resumeTask.getId());
    taskService.setVariableLocal(sourceTask.getId(), Constants.Variable.ADD_SIGN_WAITING, Boolean.FALSE);
    taskService.removeVariableLocal(sourceTask.getId(), Constants.Variable.ADD_SIGN_GROUP_ID);
    taskService.setVariableLocal(sourceTask.getId(), Constants.Variable.LOCAL_APPROVAL_ACTION, Constants.Value.APPROVED);
    taskService.complete(sourceTask.getId());
  }

  public boolean isAddSignGroupComplete(String processInstanceId, String groupId) {
    var total = intVar(processInstanceId, groupVar(groupId, Constants.Variable.ADD_SIGN_GROUP_TOTAL_SUFFIX));
    var completed = intVar(processInstanceId, groupVar(groupId, Constants.Variable.ADD_SIGN_GROUP_COMPLETED_SUFFIX));
    return total > 0 && completed >= total;
  }

  private String initializeGroup(Task sourceTask, List<String> signerUserIds, String position) {
    var groupId = UUID.randomUUID().toString();
    var processInstanceId = sourceTask.getProcessInstanceId();

    runtimeService.setVariable(processInstanceId, groupVar(groupId, Constants.Variable.ADD_SIGN_GROUP_TOTAL_SUFFIX), signerUserIds.size());
    runtimeService.setVariable(processInstanceId, groupVar(groupId, Constants.Variable.ADD_SIGN_GROUP_COMPLETED_SUFFIX), 0);
    runtimeService.setVariable(processInstanceId, groupVar(groupId, Constants.Variable.ADD_SIGN_GROUP_POSITION_SUFFIX), position);
    runtimeService.setVariable(
        processInstanceId,
        groupVar(groupId, Constants.Variable.ADD_SIGN_GROUP_PROCESS_INSTANCE_ID_SUFFIX),
        processInstanceId);
    runtimeService.setVariable(
        processInstanceId,
        groupVar(groupId, Constants.Variable.ADD_SIGN_GROUP_PROCESS_DEFINITION_ID_SUFFIX),
        sourceTask.getProcessDefinitionId());
    runtimeService.setVariable(
        processInstanceId,
        groupVar(groupId, Constants.Variable.ADD_SIGN_GROUP_SOURCE_TASK_ID_SUFFIX),
        sourceTask.getId());
    runtimeService.setVariable(
        processInstanceId,
        groupVar(groupId, Constants.Variable.ADD_SIGN_GROUP_SOURCE_TASK_NAME_SUFFIX),
        sourceTask.getName());
    runtimeService.setVariable(
        processInstanceId,
        groupVar(groupId, Constants.Variable.ADD_SIGN_GROUP_SOURCE_TASK_ASSIGNEE_SUFFIX),
        sourceTask.getAssignee());
    runtimeService.setVariable(
        processInstanceId,
        groupVar(groupId, Constants.Variable.ADD_SIGN_GROUP_SOURCE_NODE_SUFFIX),
        sourceTask.getTaskDefinitionKey());
    runtimeService.setVariable(
        processInstanceId,
        groupVar(groupId, Constants.Variable.ADD_SIGN_GROUP_SOURCE_TASK_FORM_KEY_SUFFIX),
        sourceTask.getFormKey());
    runtimeService.setVariable(
        processInstanceId,
        groupVar(groupId, Constants.Variable.ADD_SIGN_GROUP_SOURCE_TASK_FORM_DATA_SUFFIX),
        taskService.getVariableLocal(sourceTask.getId(), Constants.TASK_FORM_DATA_KEY));
    runtimeService.setVariable(
        processInstanceId,
        groupVar(groupId, Constants.Variable.ADD_SIGN_GROUP_SOURCE_TASK_PAYLOAD_SUFFIX),
        taskService.getVariableLocal(sourceTask.getId(), Constants.TASK_PAYLOAD_KEY));
    runtimeService.setVariable(
        processInstanceId,
        groupVar(groupId, Constants.Variable.ADD_SIGN_GROUP_SIGNERS_SUFFIX),
        new ArrayList<String>(signerUserIds));
    return groupId;
  }

  private void handleGroupCompletion(String processInstanceId, String groupId) {
    var position = runtimeService.getVariable(
        processInstanceId,
        groupVar(groupId, Constants.Variable.ADD_SIGN_GROUP_POSITION_SUFFIX),
        String.class);
    if (Constants.Value.ADD_SIGN_POSITION_BEFORE_CURRENT.equals(position)) {
      createResumeTask(processInstanceId, groupId);
      return;
    }
    if (Constants.Value.ADD_SIGN_POSITION_AFTER_CURRENT.equals(position)) {
      resumeAfterCurrentFlow(processInstanceId, groupId);
      return;
    }
    throw new PlatformException("不支持的加签位置: " + position);
  }

  private void createResumeTask(String processInstanceId, String groupId) {
    var alreadyCreated = runtimeService.getVariable(
        processInstanceId,
        groupVar(groupId, Constants.Variable.ADD_SIGN_GROUP_RESUME_TASK_CREATED_SUFFIX),
        Boolean.class);
    if (Boolean.TRUE.equals(alreadyCreated)) {
      return;
    }

    var sourceTaskName = runtimeService.getVariable(
        processInstanceId,
        groupVar(groupId, Constants.Variable.ADD_SIGN_GROUP_SOURCE_TASK_NAME_SUFFIX),
        String.class);
    var sourceAssignee = runtimeService.getVariable(
        processInstanceId,
        groupVar(groupId, Constants.Variable.ADD_SIGN_GROUP_SOURCE_TASK_ASSIGNEE_SUFFIX),
        String.class);
    var sourceTaskId = runtimeService.getVariable(
        processInstanceId,
        groupVar(groupId, Constants.Variable.ADD_SIGN_GROUP_SOURCE_TASK_ID_SUFFIX),
        String.class);
    var processDefinitionId = runtimeService.getVariable(
        processInstanceId,
        groupVar(groupId, Constants.Variable.ADD_SIGN_GROUP_PROCESS_DEFINITION_ID_SUFFIX),
        String.class);

    var resumeTask = (TaskEntity) taskService.newTask();
    resumeTask.setName("加签恢复 - " + StringUtils.defaultString(sourceTaskName, "原审批"));
    resumeTask.setAssignee(sourceAssignee);
    resumeTask.setProcessInstanceId(processInstanceId);
    resumeTask.setProcessDefinitionId(processDefinitionId);
    resumeTask.setScopeId(processInstanceId);
    resumeTask.setScopeType("bpmn");
    taskService.saveTask(resumeTask);

    taskService.setVariableLocal(resumeTask.getId(), Constants.Variable.ADD_SIGN_TASK_KIND, Constants.Value.ADD_SIGN_TASK_KIND_RESUME);
    taskService.setVariableLocal(resumeTask.getId(), Constants.Variable.ADD_SIGN_GROUP_ID, groupId);
    taskService.setVariableLocal(resumeTask.getId(), Constants.Variable.ADD_SIGN_RESUME_SOURCE_TASK_ID, sourceTaskId);
    taskService.setVariableLocal(resumeTask.getId(), Constants.TASK_FORM_TYPE_KEY, runtimeService.getVariable(processInstanceId, Constants.TASK_FORM_TYPE_KEY));
    taskService.setVariableLocal(
        resumeTask.getId(),
        Constants.TASK_FORM_ID_KEY,
        runtimeService.getVariable(processInstanceId, Constants.TASK_FORM_ID_KEY));
    taskService.setVariableLocal(
        resumeTask.getId(),
        Constants.TASK_FORM_DATA_KEY,
        runtimeService.getVariable(processInstanceId, groupVar(groupId, Constants.Variable.ADD_SIGN_GROUP_SOURCE_TASK_FORM_DATA_SUFFIX)));
    taskService.setVariableLocal(
        resumeTask.getId(),
        Constants.TASK_PAYLOAD_KEY,
        runtimeService.getVariable(processInstanceId, groupVar(groupId, Constants.Variable.ADD_SIGN_GROUP_SOURCE_TASK_PAYLOAD_SUFFIX)));

    runtimeService.setVariable(
        processInstanceId,
        groupVar(groupId, Constants.Variable.ADD_SIGN_GROUP_RESUME_TASK_CREATED_SUFFIX),
        Boolean.TRUE);
  }

  private void resumeAfterCurrentFlow(String processInstanceId, String groupId) {
    var sourceNodeId = runtimeService.getVariable(
        processInstanceId,
        groupVar(groupId, Constants.Variable.ADD_SIGN_GROUP_SOURCE_NODE_SUFFIX),
        String.class);
    if (StringUtils.isBlank(sourceNodeId)) {
      throw new PlatformException("后加签缺少来源节点信息");
    }

    var gateTask = taskService.createTaskQuery()
        .processInstanceId(processInstanceId)
        .taskDefinitionKey(Constants.Variable.addSignAfterCurrentGateTaskId(sourceNodeId))
        .active()
        .singleResult();
    if (gateTask == null) {
      throw new PlatformException("未找到后加签网关任务: " + sourceNodeId);
    }

    runtimeService.setVariable(processInstanceId, Constants.Variable.addSignAfterCurrentGateActiveVariableName(sourceNodeId), Boolean.FALSE);
    taskService.complete(gateTask.getId());
  }

  private void createAddSignTasks(
      String processInstanceId,
      String processDefinitionId,
      String sourceTaskName,
      String sourceTaskId,
      String groupId,
      List<String> signerUserIds) {
    for (String userId : signerUserIds) {
      var addSignTask = (TaskEntity) taskService.newTask();
      addSignTask.setName("加签 - " + StringUtils.defaultString(sourceTaskName, "审批任务"));
      addSignTask.setAssignee(userId);
      addSignTask.setProcessInstanceId(processInstanceId);
      addSignTask.setProcessDefinitionId(processDefinitionId);
      addSignTask.setScopeId(processInstanceId);
      addSignTask.setScopeType("bpmn");
      taskService.saveTask(addSignTask);

      taskService.setVariableLocal(addSignTask.getId(), Constants.Variable.ADD_SIGN_TASK_KIND, Constants.Value.ADD_SIGN_TASK_KIND_ADDED);
      taskService.setVariableLocal(addSignTask.getId(), Constants.Variable.ADD_SIGN_SOURCE_TASK_ID, sourceTaskId);
      taskService.setVariableLocal(addSignTask.getId(), Constants.Variable.ADD_SIGN_GROUP_ID, groupId);
      taskService.setVariableLocal(addSignTask.getId(), Constants.TASK_FORM_TYPE_KEY, runtimeService.getVariable(processInstanceId, Constants.TASK_FORM_TYPE_KEY));
      taskService.setVariableLocal(
          addSignTask.getId(),
          Constants.TASK_FORM_ID_KEY,
          runtimeService.getVariable(processInstanceId, Constants.TASK_FORM_ID_KEY));
      taskService.setVariableLocal(
          addSignTask.getId(),
          Constants.TASK_FORM_DATA_KEY,
          runtimeService.getVariable(processInstanceId, groupVar(groupId, Constants.Variable.ADD_SIGN_GROUP_SOURCE_TASK_FORM_DATA_SUFFIX)));
      taskService.setVariableLocal(
          addSignTask.getId(),
          Constants.TASK_PAYLOAD_KEY,
          runtimeService.getVariable(processInstanceId, groupVar(groupId, Constants.Variable.ADD_SIGN_GROUP_SOURCE_TASK_PAYLOAD_SUFFIX)));
    }
  }

  private List<String> readGroupSigners(String processInstanceId, String groupId) {
    Object signers = runtimeService.getVariable(
        processInstanceId,
        groupVar(groupId, Constants.Variable.ADD_SIGN_GROUP_SIGNERS_SUFFIX));
    if (!(signers instanceof List)) {
      return Collections.emptyList();
    }
    return ((List<?>) signers).stream()
        .filter(Objects::nonNull)
        .map(String::valueOf)
        .collect(Collectors.toList());
  }

  private int intVar(String processInstanceId, String variableName) {
    Object value = runtimeService.getVariable(processInstanceId, variableName);
    if (value instanceof Number) {
      return ((Number) value).intValue();
    }
    return 0;
  }

  private String groupVar(String groupId, String suffix) {
    return Constants.Variable.addSignGroupVariableName(groupId, suffix);
  }

  private List<String> normalizeSigners(List<String> signerUserIds) {
    if (signerUserIds == null) {
      return Collections.emptyList();
    }

    return signerUserIds.stream()
        .filter(Objects::nonNull)
        .map(String::valueOf)
        .map(String::trim)
        .filter(StringUtils::isNotBlank)
        .collect(Collectors.collectingAndThen(Collectors.toCollection(LinkedHashSet::new), ArrayList::new));
  }
}
