package com.ouroboros.workflow.approval.flowable.delegate;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;

import com.ouroboros.util.Json;
import org.flowable.bpmn.model.ServiceTask;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.identitylink.api.IdentityLink;
import org.junit.jupiter.api.Test;

import com.ouroboros.workflow.approval.definition.nodes.CcNode;
import com.ouroboros.workflow.approval.flowable.transpiler.Constants;
import com.ouroboros.workflow.approval.flowable.util.BpmnUtil;

class CcServiceTaskTest {
  @Test
  void executeRegistersParticipantIdentityLinksForCcUsers() {
    RuntimeService runtimeService = mock(RuntimeService.class);
    DelegateExecution execution = mock(DelegateExecution.class);
    ServiceTask serviceTask = ccServiceTask("cc-a", Arrays.asList("user-a", "user-b"));

    when(execution.getCurrentFlowElement()).thenReturn(serviceTask);
    when(execution.getProcessInstanceId()).thenReturn("instance-1");
    when(execution.getVariable(Constants.Variable.PROCESS_STARTER)).thenReturn("starter-1");
    when(runtimeService.getIdentityLinksForProcessInstance("instance-1")).thenReturn(Collections.<IdentityLink>emptyList());

    CcServiceTask delegate = newDelegate(runtimeService);
    delegate.execute(execution);

    verify(runtimeService).addUserIdentityLink("instance-1", "user-a", "participant");
    verify(runtimeService).addUserIdentityLink("instance-1", "user-b", "participant");
  }

  @Test
  void executeSkipsAlreadyRegisteredUsers() {
    RuntimeService runtimeService = mock(RuntimeService.class);
    DelegateExecution execution = mock(DelegateExecution.class);
    ServiceTask serviceTask = ccServiceTask("cc-a", Arrays.asList("user-a", "user-b"));
    IdentityLink existing = mock(IdentityLink.class);

    when(existing.getUserId()).thenReturn("user-a");
    when(execution.getCurrentFlowElement()).thenReturn(serviceTask);
    when(execution.getProcessInstanceId()).thenReturn("instance-1");
    when(execution.getVariable(Constants.Variable.PROCESS_STARTER)).thenReturn("starter-1");
    when(runtimeService.getIdentityLinksForProcessInstance("instance-1")).thenReturn(Collections.singletonList(existing));

    CcServiceTask delegate = newDelegate(runtimeService);
    delegate.execute(execution);

    verify(runtimeService).addUserIdentityLink("instance-1", "user-b", "participant");
  }

  private CcServiceTask newDelegate(RuntimeService runtimeService) {
    CcServiceTask delegate = new CcServiceTask();
    setField(delegate, "runtimeService", runtimeService);
    return delegate;
  }

  private ServiceTask ccServiceTask(String id, java.util.List<String> users) {
    ServiceTask serviceTask = new ServiceTask();
    serviceTask.setId(id);
    CcNode ccNode = new CcNode();
    ccNode.setId(id);
    ccNode.setName("抄送节点");
    ccNode.setCandidates(Collections.singletonList(userCandidate(users)));
    serviceTask.addExtensionElement(BpmnUtil.buildLargeTextExtensionElement("cc", Json.toJsonString(ccNode)));
    return serviceTask;
  }

  private com.ouroboros.workflow.approval.definition.candidates.UserCandidate userCandidate(java.util.List<String> users) {
    com.ouroboros.workflow.approval.definition.candidates.UserCandidate candidate =
        new com.ouroboros.workflow.approval.definition.candidates.UserCandidate();
    candidate.setUsers(users);
    return candidate;
  }

  private void setField(Object target, String fieldName, Object value) {
    try {
      Field field = target.getClass().getDeclaredField(fieldName);
      field.setAccessible(true);
      field.set(target, value);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("Failed to set field " + fieldName, e);
    }
  }
}
