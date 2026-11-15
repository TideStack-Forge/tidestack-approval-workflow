package com.ouroboros.workflow.approval.flowable.delegate;

import static com.ouroboros.workflow.approval.flowable.util.BpmnUtil.getCandidateUsers;
import static com.ouroboros.workflow.approval.flowable.util.BpmnUtil.readCandidates;
import static com.ouroboros.workflow.approval.flowable.util.BpmnUtil.readStructuredFlowDefinition;
import static com.ouroboros.workflow.approval.flowable.util.BpmnUtil.readRejectAction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.flowable.bpmn.model.CustomProperty;
import org.flowable.bpmn.model.FieldExtension;
import org.flowable.bpmn.model.ServiceTask;
import org.flowable.common.engine.impl.el.FixedValue;
import org.flowable.bpmn.model.Task;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ouroboros.workflow.approval.flowable.transpiler.Constants;
import com.ouroboros.workflow.approval.definition.nodes.ApprovalNode;

@Component(InitializeMultipleApprovalServiceTask.BEAN_NAME)
public class InitializeMultipleApprovalServiceTask implements JavaDelegate {
  public static final String BEAN_NAME = "initializeMultipleApprovalServiceTask";

  @Autowired
  private RepositoryService repositoryService;
  FixedValue percentage;
  FixedValue isSequential;
  FixedValue multipleApprovalTaskId;
  FixedValue rejectAction;

  @Override
  public void execute(DelegateExecution execution) {
    if (!(execution.getCurrentFlowElement() instanceof ServiceTask)) {
      return;
    }
    ServiceTask serviceTask = (ServiceTask) execution.getCurrentFlowElement();

    String approvalTaskId = readServiceTaskValue(serviceTask, Constants.Property.MULTIPLE_APPROVAL_TASK_ID, multipleApprovalTaskId);
    if (StringUtils.isBlank(approvalTaskId)) {
      return;
    }

    int percentage = parseInteger(readServiceTaskValue(serviceTask, Constants.Property.PERCENTAGE, this.percentage), 0);
    boolean isSequential = Boolean.parseBoolean(readServiceTaskValue(serviceTask, Constants.Property.IS_SEQUENTIAL, this.isSequential));
    String rejectAction = readRejectActionValue(serviceTask);

    List<String> assigneeList = getTaskAssignees(execution, approvalTaskId, serviceTask);
    if (assigneeList.isEmpty()) {
      List<String> assigneesWhenNoCandidate = resolveNoCandidateAssignees(execution, approvalTaskId, rejectAction);
      if (assigneesWhenNoCandidate == null) {
        return;
      }
      assigneeList = assigneesWhenNoCandidate;
    }

    setMultipleSession(execution, approvalTaskId, assigneeList, false, false);
    setControlVariables(execution, approvalTaskId, percentage, isSequential, rejectAction, false, false);
  }

  void approvalMultiTasks(DelegateExecution execution, String approvalTaskId, String rejectAction) {
    setMultipleSession(execution, approvalTaskId, Collections.<String>emptyList(), true, true);
    setControlVariables(execution, approvalTaskId, 0, false, rejectAction, true, true);
  }

  void rejectMultiTasks(DelegateExecution execution, String approvalTaskId, String rejectAction) {
    setMultipleSession(execution, approvalTaskId, Collections.<String>emptyList(), false, true);
    setControlVariables(execution, approvalTaskId, 0, false, rejectAction, false, true);
  }

  private List<String> getTaskAssignees(DelegateExecution execution, String approvalTaskId, ServiceTask task) {
    Map assigneeFromVar = execution.getVariable(Constants.Variable.USER_SELECTED_ASSIGNEES, Map.class);
    if (assigneeFromVar != null) {
      Map<String, List<String>> assigneeMap = (Map<String, List<String>>) assigneeFromVar;
      if (assigneeMap.containsKey(approvalTaskId)) {
        List<String> assignees = assigneeMap.get(approvalTaskId);
        return assignees == null ? Collections.<String>emptyList() : new ArrayList<String>(assignees);
      }
    }

    String starter = Optional.ofNullable(execution.getVariable(Constants.Variable.PROCESS_STARTER))
        .map(Object::toString)
        .orElse("");

    return getCandidateUsers(readConfiguredCandidates(execution, approvalTaskId), execution.getProcessInstanceId(), null, approvalTaskId)
        .stream()
        .map(assignee -> assignee.replace("${starter}", starter))
        .collect(Collectors.toList());
  }

  private void setMultipleSession(
      DelegateExecution execution,
      String approvalTaskId,
      List<String> assigneeList,
      boolean approved,
      boolean completed
  ) {
    var sessionId = UUID.randomUUID().toString();
    execution.setVariable(Constants.Variable.getMultipleApprovalSessionVariableName(approvalTaskId), sessionId);
    execution.setVariable(Constants.Variable.getMultipleApprovalAssigneeListVariableName(approvalTaskId), assigneeList);
    execution.setVariable(Constants.Variable.MULTIPLE_APPROVAL_SESSION + sessionId + Constants.Variable.APPROVED_SUFFIX, approved);
    execution.setVariable(
        Constants.Variable.MULTIPLE_APPROVAL_SESSION + sessionId + Constants.Variable.MULTIPLE_APPROVAL_COMPLETED_SUFFIX,
        completed
    );
    execution.setVariable(
        Constants.Variable.MULTIPLE_APPROVAL_SESSION + sessionId + Constants.Variable.MULTIPLE_APPROVAL_ASSIGNEE_LIST,
        assigneeList
    );
  }

  private void setControlVariables(
      DelegateExecution execution,
      String approvalTaskId,
      int percentage,
      boolean isSequential,
      String rejectAction,
      boolean approved,
      boolean completed
  ) {
    execution.setVariable(approvalTaskId + Constants.Variable.APPROVAL_ACTION_SUFFIX, rejectAction);
    execution.setVariable(approvalTaskId + Constants.Variable.MULTIPLE_APPROVED_PERCENTAGE_SUFFIX, percentage);
    execution.setVariable(approvalTaskId + Constants.Variable.MULTIPLE_APPROVAL_COMPLETED_SUFFIX, completed);
    execution.setVariable(approvalTaskId + Constants.Variable.MULTIPLE_APPROVAL_IS_SEQUENTIAL_SUFFIX, isSequential);
    execution.setVariable(approvalTaskId + Constants.Variable.APPROVED_SUFFIX, approved);
    execution.setVariable(approvalTaskId + Constants.Variable.NR_OF_APPROVED_SUFFIX, 0);
    execution.setVariable(approvalTaskId + Constants.Variable.NR_OF_REJECTED_SUFFIX, 0);
  }

  private String readCustomProperty(ServiceTask serviceTask, String name) {
    if (serviceTask.getCustomProperties() == null) {
      return null;
    }

    for (CustomProperty property : serviceTask.getCustomProperties()) {
      if (property != null && StringUtils.equals(name, property.getName())) {
        return property.getSimpleValue();
      }
    }
    return null;
  }

  private String readFieldExtension(ServiceTask serviceTask, String name) {
    if (serviceTask.getFieldExtensions() == null) {
      return null;
    }

    for (FieldExtension fieldExtension : serviceTask.getFieldExtensions()) {
      if (fieldExtension != null && StringUtils.equals(name, fieldExtension.getFieldName())) {
        return fieldExtension.getStringValue();
      }
    }
    return null;
  }

  private String readServiceTaskValue(ServiceTask serviceTask, String name, FixedValue injectedValue) {
    return Optional.ofNullable(readCustomProperty(serviceTask, name))
        .filter(StringUtils::isNotBlank)
        .orElseGet(() -> Optional.ofNullable(readFieldExtension(serviceTask, name))
            .filter(StringUtils::isNotBlank)
            .orElseGet(() -> readInjectedValue(injectedValue)));
  }

  private String readRejectActionValue(ServiceTask serviceTask) {
    return Optional.ofNullable(readServiceTaskValue(serviceTask, Constants.Property.REJECT_ACTION, rejectAction))
        .filter(StringUtils::isNotBlank)
        .orElseGet(() -> readRejectAction(serviceTask));
  }

  private String readInjectedValue(FixedValue fixedValue) {
    return fixedValue == null ? null : fixedValue.getExpressionText();
  }

  private List<com.ouroboros.workflow.approval.definition.Candidate> readConfiguredCandidates(
      DelegateExecution execution,
      String approvalTaskId
  ) {
    if (repositoryService == null || StringUtils.isBlank(execution.getProcessDefinitionId())) {
      return Collections.emptyList();
    }

    var bpmnModel = repositoryService.getBpmnModel(execution.getProcessDefinitionId());
    if (bpmnModel == null) {
      return Collections.emptyList();
    }

    var flowElement = bpmnModel.getFlowElement(approvalTaskId);
    if (!(flowElement instanceof Task)) {
      return Collections.emptyList();
    }

    return readCandidates((Task) flowElement);
  }

  private int parseInteger(String value, int defaultValue) {
    if (StringUtils.isBlank(value)) {
      return defaultValue;
    }

    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException ex) {
      return defaultValue;
    }
  }

  private List<String> resolveNoCandidateAssignees(
      DelegateExecution execution,
      String approvalTaskId,
      String rejectAction
  ) {
    var approvalNode = readApprovalNode(execution, approvalTaskId);
    if (approvalNode == null) {
      rejectMultiTasks(execution, approvalTaskId, rejectAction);
      return null;
    }

    var mode = approvalNode.getNoCandidateMode();
    if (mode == ApprovalNode.NoCandidateMode.approved) {
      approvalMultiTasks(execution, approvalTaskId, rejectAction);
      return null;
    }

    if (mode == ApprovalNode.NoCandidateMode.reAssignUser) {
      var reAssignUsers = Optional.ofNullable(approvalNode.getReAssignUsers())
          .orElse(Collections.emptyList());
      if (!reAssignUsers.isEmpty()) {
        return new ArrayList<String>(reAssignUsers);
      }
    }

    rejectMultiTasks(execution, approvalTaskId, rejectAction);
    return null;
  }

  private ApprovalNode readApprovalNode(DelegateExecution execution, String approvalTaskId) {
    if (repositoryService == null || StringUtils.isBlank(execution.getProcessDefinitionId())) {
      return null;
    }

    var bpmnModel = repositoryService.getBpmnModel(execution.getProcessDefinitionId());
    if (bpmnModel == null) {
      return null;
    }

    var definition = readStructuredFlowDefinition(bpmnModel);
    if (definition == null || definition.getNodes() == null) {
      return null;
    }

    return definition.getNodes().stream()
        .filter(node -> node != null && StringUtils.equals(node.getId(), approvalTaskId))
        .filter(ApprovalNode.class::isInstance)
        .map(ApprovalNode.class::cast)
        .findFirst()
        .orElse(null);
  }
}
