package com.ouroboros.workflow.approval.flowable.listener;

import static com.ouroboros.workflow.approval.flowable.util.BpmnUtil.getCandidateUsers;
import static com.ouroboros.workflow.approval.flowable.util.BpmnUtil.readStructuredFlowDefinition;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.TaskService;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.UserTask;
import org.flowable.task.service.delegate.DelegateTask;
import org.flowable.task.service.delegate.TaskListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ouroboros.util.Dates;
import com.ouroboros.workflow.approval.definition.nodes.ApprovalNode;
import com.ouroboros.workflow.approval.event.TaskEventDispatcher;
import com.ouroboros.workflow.approval.flowable.transpiler.Constants;
import com.ouroboros.workflow.approval.flowable.util.BpmnUtil;
import com.ouroboros.workflow.approval.model.TaskState;
import com.ouroboros.workflow.approval.model.events.FlowTaskEvent;

@Component(CreateSingleApprovalTaskListener.BEAN_NAME)
public class CreateSingleApprovalTaskListener implements TaskListener {
  public static final String BEAN_NAME = "createSingleApprovalTaskListener";

  private static final Logger logger = LoggerFactory.getLogger(CreateSingleApprovalTaskListener.class);
  @Autowired
  private RepositoryService repositoryService;
  @Autowired
  private TaskService taskService;
  @Autowired
  private TaskEventDispatcher taskEventDispatcher;

  @Override
  public void notify(DelegateTask task) {
    String nodeId = task.getTaskDefinitionKey();
    markTaskKind(task, nodeId);
    String addSignAfterCurrentGateVar = Constants.Variable.addSignAfterCurrentGateActiveVariableName(nodeId);
    if (task.getVariable(addSignAfterCurrentGateVar) == null) {
      task.setVariable(addSignAfterCurrentGateVar, Boolean.FALSE);
    }

    Map<String, List<String>> assigneeMap = selectedAssigneesFromVariable(task.getVariable(Constants.Variable.USER_SELECTED_ASSIGNEES));
    if (!assigneeMap.isEmpty()) {
      if (assigneeMap.containsKey(nodeId)) {
        List<String> assignees = assigneeMap.get(nodeId);
        if (assignees != null && !assignees.isEmpty()) {
          if (assignees.size() == 1) {
            task.setAssignee(assignees.get(0));
          } else {
            task.addCandidateUsers(assignees);
          }
          dispatchEvent(task, assignees);
          return;
        }

        List<String> candidates = handleNoCandidateMode(task);
        if (!candidates.isEmpty()) {
          task.addCandidateUsers(candidates);
          dispatchEvent(task, candidates);
        }
        return;
      }
    }

    String starter = Optional.ofNullable(task.getVariable(Constants.Variable.PROCESS_STARTER))
        .map(Object::toString)
        .orElse("");

    List<String> candidates = getCandidateUsers(repositoryService, task).stream()
        .map(assignee -> assignee.replace("${starter}", starter))
        .collect(Collectors.toList());

    if (candidates.isEmpty()) {
      candidates = handleNoCandidateMode(task);
    }

    if (!candidates.isEmpty()) {
      task.addCandidateUsers(candidates);
      dispatchEvent(task, candidates);
    }
  }

  private void markTaskKind(DelegateTask task, String nodeId) {
    if (taskService == null || repositoryService == null || StringUtils.isAnyBlank(task.getId(), task.getProcessDefinitionId(), nodeId)) {
      return;
    }

    org.flowable.bpmn.model.BpmnModel bpmnModel = repositoryService.getBpmnModel(task.getProcessDefinitionId());
    if (bpmnModel == null) {
      return;
    }

    FlowElement flowElement = bpmnModel.getFlowElement(nodeId);
    if (!(flowElement instanceof UserTask)) {
      return;
    }

    String taskKind = BpmnUtil.readTaskKind((UserTask) flowElement);
    if (StringUtils.isNotBlank(taskKind)) {
      taskService.setVariableLocal(task.getId(), Constants.Variable.TASK_KIND, taskKind);
    }
  }

  private Map<String, List<String>> selectedAssigneesFromVariable(Object assigneeFromVar) {
    if (!(assigneeFromVar instanceof Map<?, ?>)) {
      return Collections.emptyMap();
    }

    Map<String, List<String>> result = new HashMap<>();
    Map<?, ?> assigneeMap = (Map<?, ?>) assigneeFromVar;
    for (Map.Entry<?, ?> entry : assigneeMap.entrySet()) {
      if (!(entry.getKey() instanceof String) || !(entry.getValue() instanceof List<?>)) {
        continue;
      }

      List<?> values = (List<?>) entry.getValue();
      if (values.stream().allMatch(String.class::isInstance)) {
        result.put((String) entry.getKey(), values.stream()
            .map(String.class::cast)
            .collect(Collectors.toList()));
      }
    }
    return result;
  }

  private List<String> handleNoCandidateMode(DelegateTask task) {
    ApprovalNode approvalNode = readApprovalNode(task);
    if (approvalNode == null) {
      autoComplete(task, Constants.Value.REJECTED, "审批人为空，未找到任务节点");
      return Collections.emptyList();
    }

    ApprovalNode.NoCandidateMode mode = approvalNode.getNoCandidateMode();
    if (mode == ApprovalNode.NoCandidateMode.approved) {
      autoComplete(task, Constants.Value.APPROVED, "审批人为空，执行通过逻辑");
      return Collections.emptyList();
    }

    if (mode == ApprovalNode.NoCandidateMode.reAssignUser) {
      List<String> reAssignUsers = Optional.ofNullable(approvalNode.getReAssignUsers())
          .orElse(Collections.emptyList());
      if (!reAssignUsers.isEmpty()) {
        return reAssignUsers;
      }
      logger.warn("No reAssignUsers configured for task {}, falling back to rejection", task.getTaskDefinitionKey());
      autoComplete(task, Constants.Value.REJECTED, "审批人为空，且未找到回退配置的候选人");
      return Collections.emptyList();
    }

    autoComplete(task, Constants.Value.REJECTED, "审批人为空，执行拒绝逻辑");
    return Collections.emptyList();
  }

  private void autoComplete(DelegateTask task, String action, String comment) {
    if (taskService == null) {
      logger.warn("No Flowable task service found while auto-completing task {}", task.getId());
      return;
    }

    String autoUserId = "$" + action;
    task.addCandidateUsers(Collections.singleton(autoUserId));
    taskService.setAssignee(task.getId(), autoUserId);
    taskService.addComment(task.getId(), task.getProcessInstanceId(), comment);
    taskService.setVariableLocal(task.getId(), Constants.Variable.LOCAL_APPROVAL_ACTION, action);
    taskService.complete(task.getId());
  }

  void dispatchEvent(DelegateTask task, List<String> candidates) {
    FlowTaskEvent event = new FlowTaskEvent();
    String nodeId = taskNodeId(task);

    event.setInstanceId(task.getProcessInstanceId());
    event.setTaskId(task.getId());
    event.setTaskName(task.getName());
    event.setCandidateUsers(candidates == null ? Collections.<String>emptyList() : candidates);
    event.setAssignee(task.getAssignee());
    event.setFlowKey(BpmnUtil.resolveFlowKey(repositoryService, task));
    event.setNodeId(nodeId);
    event.setState(TaskState.CREATED);
    event.setNotification(BpmnUtil.readNotification(repositoryService, task.getProcessDefinitionId()));
    if (task.getCreateTime() == null) {
      event.setEventTime(Dates.now());
    } else {
      event.setEventTime(task.getCreateTime());
    }
    event.setFlowNodeSupplier(BpmnUtil.getFlowNodeSupplier(repositoryService, task.getProcessDefinitionId(), nodeId));

    taskEventDispatcher.dispatchEvent(event);
  }

  private ApprovalNode readApprovalNode(DelegateTask task) {
    if (repositoryService == null || StringUtils.isBlank(task.getProcessDefinitionId())) {
      return null;
    }

    org.flowable.bpmn.model.BpmnModel bpmnModel = repositoryService.getBpmnModel(task.getProcessDefinitionId());
    if (bpmnModel == null) {
      return null;
    }

    com.ouroboros.workflow.approval.definition.graph.StructuredFlowDefinition definition = readStructuredFlowDefinition(bpmnModel);
    if (definition == null || definition.getNodes() == null) {
      return null;
    }

    return definition.getNodes().stream()
        .filter(ApprovalNode.class::isInstance)
        .map(ApprovalNode.class::cast)
        .filter(node -> StringUtils.equals(node.getId(), taskNodeId(task)))
        .findFirst()
        .orElse(null);
  }

  private String taskNodeId(DelegateTask task) {
    return StringUtils.defaultIfBlank(task.getTaskDefinitionKey(), task.getName());
  }
}
