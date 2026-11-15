package com.ouroboros.workflow.approval.flowable.service;

import org.flowable.bpmn.model.BpmnModel;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.repository.ProcessDefinitionQuery;
import org.junit.jupiter.api.Test;

import com.ouroboros.workflow.approval.FlowDefinitionService;
import com.ouroboros.workflow.approval.flowable.util.BpmnUtil;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FlowableFlowDefinitionStructuredReadTest {
  @Test
  void publicDefinitionPortReadsTheStoredV2GraphWithNotificationAndNodeSwitchIntact() {
    var process = new org.flowable.bpmn.model.Process(); process.setId("approval");
    BpmnUtil.saveFlowDefinitionRawJson(process, """
        {"formatVersion":2,"id":"approval","name":"Approval",
         "notification":{"taskCreatedTemplate":"notice"},
         "nodes":[{"type":"发起节点","id":"start","name":"Start"},
                  {"type":"审批节点","id":"review","name":"Review","subType":"单签","candidates":[],"notify":false},
                  {"type":"结束节点","id":"end","name":"End"}],
         "edges":[{"id":"to-review","type":"SEQUENCE","sourceId":"start","targetId":"review"},
                  {"id":"to-end","type":"SEQUENCE","sourceId":"review","targetId":"end"}]}
        """);
    var bpmn = new BpmnModel(); bpmn.addProcess(process);
    var service = service();
    var deployed = mock(ProcessDefinition.class); when(deployed.getId()).thenReturn("deployment");
    when(service.repositoryService.createProcessDefinitionQuery().singleResult()).thenReturn(deployed);
    when(service.repositoryService.getBpmnModel("deployment")).thenReturn(bpmn);
    FlowDefinitionService port = service;
    var graph = port.getStructuredFlowDefinition("approval").get();
    assertEquals(2, graph.getFormatVersion()); assertFalse(graph.getNode("review").isNotifyEnabled());
    assertEquals("notice", graph.getNotification().getTaskCreatedTemplate());
    assertEquals("review", graph.getOutgoingEdges("start").getFirst().getTargetId());
  }

  @Test
  void unavailableDeploymentRemainsAnExplicitFailure() {
    assertTrue(service().getStructuredFlowDefinition("missing").isFailure());
  }

  private FlowableFlowDefinitionService service() {
    var service = new FlowableFlowDefinitionService(); service.repositoryService = mock(RepositoryService.class);
    when(service.repositoryService.createProcessDefinitionQuery()).thenReturn(mock(ProcessDefinitionQuery.class, RETURNS_SELF));
    return service;
  }
}
