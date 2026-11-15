package com.ouroboros.workflow.approval.flowable.delegate;

import static com.ouroboros.workflow.approval.flowable.util.BpmnUtil.getCandidateUsers;
import static com.ouroboros.workflow.approval.flowable.util.BpmnUtil.getExtensionElementTexts;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.ServiceTask;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.flowable.identitylink.api.IdentityLink;
import org.flowable.identitylink.api.IdentityLinkType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ouroboros.exception.InternalException;
import com.ouroboros.util.Json;
import com.ouroboros.workflow.approval.definition.nodes.CcNode;
import com.ouroboros.workflow.approval.flowable.transpiler.Constants;

@Component(CcServiceTask.BEAN_NAME)
public class CcServiceTask implements JavaDelegate {
  public static final String BEAN_NAME = "ccServiceTask";
  private static final String CC_EXTENSION_NAME = "cc";

  @Autowired
  private RuntimeService runtimeService;

  @Override
  public void execute(DelegateExecution execution) {
    FlowElement currentFlowElement = execution.getCurrentFlowElement();
    if (!(currentFlowElement instanceof ServiceTask)) {
      return;
    }

    String processInstanceId = execution.getProcessInstanceId();
    if (StringUtils.isBlank(processInstanceId)) {
      throw new InternalException("抄送节点执行时缺少流程实例ID");
    }

    CcNode ccNode = readCcNode((ServiceTask) currentFlowElement);
    if (ccNode == null) {
      throw new InternalException("抄送节点配置缺失: " + currentFlowElement.getId());
    }

    String starter = Optional.ofNullable(execution.getVariable(Constants.Variable.PROCESS_STARTER))
        .map(Object::toString)
        .orElse("");
    List<String> candidateUsers = getCandidateUsers(
        ccNode.getCandidates(),
        processInstanceId,
        null,
        ccNode.getName())
        .stream()
        .filter(StringUtils::isNotBlank)
        .map(userId -> userId.replace("${starter}", starter))
        .filter(StringUtils::isNotBlank)
        .distinct()
        .collect(Collectors.toList());
    if (candidateUsers.isEmpty()) {
      return;
    }

    Set<String> existingUsers = Optional.ofNullable(runtimeService.getIdentityLinksForProcessInstance(processInstanceId))
        .orElse(Collections.<IdentityLink>emptyList())
        .stream()
        .filter(Objects::nonNull)
        .map(IdentityLink::getUserId)
        .filter(StringUtils::isNotBlank)
        .collect(Collectors.toSet());

    candidateUsers.stream()
        .filter(userId -> !existingUsers.contains(userId))
        .forEach(userId -> runtimeService.addUserIdentityLink(processInstanceId, userId, IdentityLinkType.PARTICIPANT));
  }

  private CcNode readCcNode(ServiceTask serviceTask) {
    String json = getExtensionElementTexts(serviceTask, CC_EXTENSION_NAME).stream()
        .findFirst()
        .orElse(null);
    if (StringUtils.isBlank(json)) {
      return null;
    }
    return Json.tryToBean(CcNode.class, json)
        .getOrElseThrow(e -> new InternalException("抄送节点配置反序列化失败: " + e.getMessage(), e));
  }
}
