package com.ouroboros.workflow.approval.flowable.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.DeploymentBuilder;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.repository.ProcessDefinitionQuery;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Answers;
import org.slf4j.LoggerFactory;

import io.vavr.control.Try;

import com.ouroboros.exception.InternalException;
import com.ouroboros.util.Json;
import com.ouroboros.workflow.approval.definition.codec.CompositeFlowDefinitionCodec;
import com.ouroboros.workflow.approval.definition.codec.FlowDefinitionV2Normalizer;
import com.ouroboros.workflow.approval.flowable.util.BpmnUtil;

public class FlowableFlowManageServiceTest {
  private final CompositeFlowDefinitionCodec flowDefinitionCodec = new CompositeFlowDefinitionCodec();
  private final FlowDefinitionV2Normalizer flowDefinitionV2Normalizer = new FlowDefinitionV2Normalizer();
  private final Logger logger = (Logger) LoggerFactory.getLogger(FlowableFlowManageService.class);
  private ListAppender<ILoggingEvent> logAppender;

  @AfterEach
  void tearDownAppender() {
    if (logAppender != null) {
      logger.detachAppender(logAppender);
      logAppender.stop();
      logAppender = null;
    }
  }

  @Test
  void deployBuildsNewFlowableDeploymentWhenDefinitionChanged() {
    logAppender = attachLogAppender();
    FlowableFlowManageService service = new FlowableFlowManageService();
    RepositoryService repositoryService = mock(RepositoryService.class);
    ProcessDefinitionQuery latestDefinitionQuery = mock(ProcessDefinitionQuery.class);
    ProcessDefinitionQuery deployedDefinitionQuery = mock(ProcessDefinitionQuery.class);
    DeploymentBuilder deploymentBuilder = mock(DeploymentBuilder.class, Answers.RETURNS_SELF);
    Deployment deployment = mock(Deployment.class);
    ProcessDefinition deployedDefinition = mock(ProcessDefinition.class);

    service.repositoryService = repositoryService;

    when(repositoryService.createProcessDefinitionQuery()).thenReturn(latestDefinitionQuery, deployedDefinitionQuery);
    when(latestDefinitionQuery.processDefinitionKey("flow-a")).thenReturn(latestDefinitionQuery);
    when(latestDefinitionQuery.orderByProcessDefinitionVersion()).thenReturn(latestDefinitionQuery);
    when(latestDefinitionQuery.desc()).thenReturn(latestDefinitionQuery);
    when(latestDefinitionQuery.listPage(0, 1)).thenReturn(Collections.emptyList());
    when(repositoryService.createDeployment()).thenReturn(deploymentBuilder);
    when(deploymentBuilder.deploy()).thenReturn(deployment);
    when(deployment.getId()).thenReturn("deployment-new");
    when(deployedDefinitionQuery.deploymentId("deployment-new")).thenReturn(deployedDefinitionQuery);
    when(deployedDefinitionQuery.singleResult()).thenReturn(deployedDefinition);
    when(deployedDefinition.getId()).thenReturn("process-definition-new");

    String processDefinitionId = service.deploy("flow-a", v2FlowJson()).get();

    assertEquals("process-definition-new", processDefinitionId);
    verify(deploymentBuilder).addString("flowDefinition", canonicalV2FlowJson(v2FlowJson()));

    ArgumentCaptor<BpmnModel> modelCaptor = ArgumentCaptor.forClass(BpmnModel.class);
    verify(deploymentBuilder).addBpmnModel(eq("flow-a.bpmn"), modelCaptor.capture());
    assertEquals(
        Json.toJsonString(flowDefinitionCodec.decode(v2FlowJson()).get()),
        Json.toJsonString(flowDefinitionCodec.decode(BpmnUtil.readFlowDefinitionRawJson(modelCaptor.getValue())).get()));
    assertTrue(missingResourceWarnings().isEmpty());
  }

  @Test
  void deployStoresEditorLayoutInVersionedDefinitionResource() {
    FlowableFlowManageService service = new FlowableFlowManageService();
    RepositoryService repositoryService = mock(RepositoryService.class);
    ProcessDefinitionQuery latestDefinitionQuery = mock(ProcessDefinitionQuery.class);
    ProcessDefinitionQuery deployedDefinitionQuery = mock(ProcessDefinitionQuery.class);
    DeploymentBuilder deploymentBuilder = mock(DeploymentBuilder.class, Answers.RETURNS_SELF);
    Deployment deployment = mock(Deployment.class);
    ProcessDefinition deployedDefinition = mock(ProcessDefinition.class);

    service.repositoryService = repositoryService;

    when(repositoryService.createProcessDefinitionQuery()).thenReturn(latestDefinitionQuery, deployedDefinitionQuery);
    when(latestDefinitionQuery.processDefinitionKey("flow-a")).thenReturn(latestDefinitionQuery);
    when(latestDefinitionQuery.orderByProcessDefinitionVersion()).thenReturn(latestDefinitionQuery);
    when(latestDefinitionQuery.desc()).thenReturn(latestDefinitionQuery);
    when(latestDefinitionQuery.listPage(0, 1)).thenReturn(Collections.emptyList());
    when(repositoryService.createDeployment()).thenReturn(deploymentBuilder);
    when(deploymentBuilder.deploy()).thenReturn(deployment);
    when(deployment.getId()).thenReturn("deployment-new");
    when(deployedDefinitionQuery.deploymentId("deployment-new")).thenReturn(deployedDefinitionQuery);
    when(deployedDefinitionQuery.singleResult()).thenReturn(deployedDefinition);
    when(deployedDefinition.getId()).thenReturn("process-definition-new");

    service.deploy("flow-a", v2FlowJsonWithEditorLayout()).get();

    ArgumentCaptor<String> definitionResourceCaptor = ArgumentCaptor.forClass(String.class);
    verify(deploymentBuilder).addString(eq("flowDefinition"), definitionResourceCaptor.capture());
    JsonNode storedDefinition = Json.toBean(JsonNode.class, definitionResourceCaptor.getValue());
    assertEquals(120, storedDefinition.path("editorLayout").path("nodePositions").path("start-a").path("x").asInt());
    assertEquals(420, storedDefinition.path("editorLayout").path("nodePositions").path("approve-a").path("x").asInt());

    ArgumentCaptor<BpmnModel> modelCaptor = ArgumentCaptor.forClass(BpmnModel.class);
    verify(deploymentBuilder).addBpmnModel(eq("flow-a.bpmn"), modelCaptor.capture());
    JsonNode bpmnRawDefinition = Json.toBean(JsonNode.class, BpmnUtil.readFlowDefinitionRawJson(modelCaptor.getValue()));
    assertEquals(storedDefinition.path("editorLayout"), bpmnRawDefinition.path("editorLayout"));
  }

  @Test
  void deployReusesLatestDefinitionWhenRawJsonIsUnchanged() {
    FlowableFlowManageService service = new FlowableFlowManageService();
    RepositoryService repositoryService = mock(RepositoryService.class);
    ProcessDefinitionQuery latestDefinitionQuery = mock(ProcessDefinitionQuery.class);
    ProcessDefinitionQuery deploymentDefinitionQuery = mock(ProcessDefinitionQuery.class);
    ProcessDefinition latestDefinition = mock(ProcessDefinition.class);
    ProcessDefinition deployedDefinition = mock(ProcessDefinition.class);

    service.repositoryService = repositoryService;

    when(repositoryService.createProcessDefinitionQuery()).thenReturn(latestDefinitionQuery, deploymentDefinitionQuery);
    when(latestDefinitionQuery.processDefinitionKey("flow-a")).thenReturn(latestDefinitionQuery);
    when(latestDefinitionQuery.orderByProcessDefinitionVersion()).thenReturn(latestDefinitionQuery);
    when(latestDefinitionQuery.desc()).thenReturn(latestDefinitionQuery);
    when(latestDefinitionQuery.listPage(0, 1)).thenReturn(Collections.singletonList(latestDefinition));
    when(latestDefinition.getDeploymentId()).thenReturn("deployment-existing");
    when(repositoryService.getResourceAsStream("deployment-existing", "flowDefinition"))
        .thenReturn(new ByteArrayInputStream(v2FlowJson().getBytes(StandardCharsets.UTF_8)));
    when(deploymentDefinitionQuery.deploymentId("deployment-existing")).thenReturn(deploymentDefinitionQuery);
    when(deploymentDefinitionQuery.singleResult()).thenReturn(deployedDefinition);
    when(deployedDefinition.getId()).thenReturn("process-definition-existing");

    String processDefinitionId = service.deploy("flow-a", v2FlowJson()).get();

    assertEquals("process-definition-existing", processDefinitionId);
    verify(repositoryService, never()).createDeployment();
  }

  @Test
  void deployNewVersionBuildsDeploymentWhenRawJsonIsUnchanged() {
    FlowableFlowManageService service = new FlowableFlowManageService();
    RepositoryService repositoryService = mock(RepositoryService.class);
    ProcessDefinitionQuery latestDefinitionQuery = mock(ProcessDefinitionQuery.class);
    ProcessDefinitionQuery deployedDefinitionQuery = mock(ProcessDefinitionQuery.class);
    DeploymentBuilder deploymentBuilder = mock(DeploymentBuilder.class, Answers.RETURNS_SELF);
    ProcessDefinition latestDefinition = mock(ProcessDefinition.class);
    Deployment deployment = mock(Deployment.class);
    ProcessDefinition deployedDefinition = mock(ProcessDefinition.class);

    service.repositoryService = repositoryService;

    when(repositoryService.createProcessDefinitionQuery()).thenReturn(latestDefinitionQuery, deployedDefinitionQuery);
    when(latestDefinitionQuery.processDefinitionKey("flow-a")).thenReturn(latestDefinitionQuery);
    when(latestDefinitionQuery.orderByProcessDefinitionVersion()).thenReturn(latestDefinitionQuery);
    when(latestDefinitionQuery.desc()).thenReturn(latestDefinitionQuery);
    when(latestDefinitionQuery.listPage(0, 1)).thenReturn(Collections.singletonList(latestDefinition));
    when(latestDefinition.getDeploymentId()).thenReturn("deployment-existing");
    when(repositoryService.createDeployment()).thenReturn(deploymentBuilder);
    when(deploymentBuilder.deploy()).thenReturn(deployment);
    when(deployment.getId()).thenReturn("deployment-new");
    when(deployedDefinitionQuery.deploymentId("deployment-new")).thenReturn(deployedDefinitionQuery);
    when(deployedDefinitionQuery.singleResult()).thenReturn(deployedDefinition);
    when(deployedDefinition.getId()).thenReturn("process-definition-new");

    String processDefinitionId = service.deployNewVersion("flow-a", v2FlowJson()).get();

    assertEquals("process-definition-new", processDefinitionId);
    verify(repositoryService, never()).getResourceAsStream("deployment-existing", "flowDefinition");
    verify(deploymentBuilder).addString("flowDefinition", canonicalV2FlowJson(v2FlowJson()));
  }

  @Test
  void offlineSuspendsProcessDefinitionWithoutSuspendingRunningInstances() {
    FlowableFlowManageService service = new FlowableFlowManageService();
    RepositoryService repositoryService = mock(RepositoryService.class);
    service.repositoryService = repositoryService;

    assertTrue(service.offline("process-definition-existing").isSuccess());

    verify(repositoryService).suspendProcessDefinitionById(eq("process-definition-existing"), eq(false), isNull());
  }

  @Test
  void deployReactivatesSuspendedDefinitionWhenReusingUnchangedLatestDefinition() {
    FlowableFlowManageService service = new FlowableFlowManageService();
    RepositoryService repositoryService = mock(RepositoryService.class);
    ProcessDefinitionQuery latestDefinitionQuery = mock(ProcessDefinitionQuery.class);
    ProcessDefinitionQuery deploymentDefinitionQuery = mock(ProcessDefinitionQuery.class);
    ProcessDefinition latestDefinition = mock(ProcessDefinition.class);
    ProcessDefinition deployedDefinition = mock(ProcessDefinition.class);

    service.repositoryService = repositoryService;

    when(repositoryService.createProcessDefinitionQuery()).thenReturn(latestDefinitionQuery, deploymentDefinitionQuery);
    when(latestDefinitionQuery.processDefinitionKey("flow-a")).thenReturn(latestDefinitionQuery);
    when(latestDefinitionQuery.orderByProcessDefinitionVersion()).thenReturn(latestDefinitionQuery);
    when(latestDefinitionQuery.desc()).thenReturn(latestDefinitionQuery);
    when(latestDefinitionQuery.listPage(0, 1)).thenReturn(Collections.singletonList(latestDefinition));
    when(latestDefinition.getDeploymentId()).thenReturn("deployment-existing");
    when(repositoryService.getResourceAsStream("deployment-existing", "flowDefinition"))
        .thenReturn(new ByteArrayInputStream(v2FlowJson().getBytes(StandardCharsets.UTF_8)));
    when(deploymentDefinitionQuery.deploymentId("deployment-existing")).thenReturn(deploymentDefinitionQuery);
    when(deploymentDefinitionQuery.singleResult()).thenReturn(deployedDefinition);
    when(deployedDefinition.getId()).thenReturn("process-definition-existing");
    when(deployedDefinition.isSuspended()).thenReturn(true);

    String processDefinitionId = service.deploy("flow-a", v2FlowJson()).get();

    assertEquals("process-definition-existing", processDefinitionId);
    verify(repositoryService).activateProcessDefinitionById(eq("process-definition-existing"), eq(false), isNull());
    verify(repositoryService, never()).createDeployment();
  }

  @Test
  void deployBuildsNewDeploymentAndWarnsWhenLatestDefinitionResourceIsMissing() {
    logAppender = attachLogAppender();
    FlowableFlowManageService service = new FlowableFlowManageService();
    RepositoryService repositoryService = mock(RepositoryService.class);
    ProcessDefinitionQuery latestDefinitionQuery = mock(ProcessDefinitionQuery.class);
    ProcessDefinitionQuery deployedDefinitionQuery = mock(ProcessDefinitionQuery.class);
    DeploymentBuilder deploymentBuilder = mock(DeploymentBuilder.class, Answers.RETURNS_SELF);
    ProcessDefinition latestDefinition = mock(ProcessDefinition.class);
    Deployment deployment = mock(Deployment.class);
    ProcessDefinition deployedDefinition = mock(ProcessDefinition.class);

    service.repositoryService = repositoryService;

    when(repositoryService.createProcessDefinitionQuery()).thenReturn(latestDefinitionQuery, deployedDefinitionQuery);
    when(latestDefinitionQuery.processDefinitionKey("flow-a")).thenReturn(latestDefinitionQuery);
    when(latestDefinitionQuery.orderByProcessDefinitionVersion()).thenReturn(latestDefinitionQuery);
    when(latestDefinitionQuery.desc()).thenReturn(latestDefinitionQuery);
    when(latestDefinitionQuery.listPage(0, 1)).thenReturn(Collections.singletonList(latestDefinition));
    when(latestDefinition.getDeploymentId()).thenReturn("deployment-existing");
    when(repositoryService.getResourceAsStream("deployment-existing", "flowDefinition")).thenReturn(null);
    when(repositoryService.createDeployment()).thenReturn(deploymentBuilder);
    when(deploymentBuilder.deploy()).thenReturn(deployment);
    when(deployment.getId()).thenReturn("deployment-new");
    when(deployedDefinitionQuery.deploymentId("deployment-new")).thenReturn(deployedDefinitionQuery);
    when(deployedDefinitionQuery.singleResult()).thenReturn(deployedDefinition);
    when(deployedDefinition.getId()).thenReturn("process-definition-new");

    String processDefinitionId = service.deploy("flow-a", v2FlowJson()).get();

    assertEquals("process-definition-new", processDefinitionId);
    List<ILoggingEvent> warnings = missingResourceWarnings();
    assertEquals(1, warnings.size());
    assertTrue(warnings.get(0).getFormattedMessage().contains("flow-a"));
    assertTrue(warnings.get(0).getFormattedMessage().contains("deployment-existing"));
    assertTrue(warnings.get(0).getFormattedMessage().contains("flowDefinition"));
  }

  @Test
  void deployBuildsNewDeploymentWhenReadableLatestDefinitionIsDifferent() {
    FlowableFlowManageService service = new FlowableFlowManageService();
    RepositoryService repositoryService = mock(RepositoryService.class);
    ProcessDefinitionQuery latestDefinitionQuery = mock(ProcessDefinitionQuery.class);
    ProcessDefinitionQuery deployedDefinitionQuery = mock(ProcessDefinitionQuery.class);
    DeploymentBuilder deploymentBuilder = mock(DeploymentBuilder.class, Answers.RETURNS_SELF);
    ProcessDefinition latestDefinition = mock(ProcessDefinition.class);
    Deployment deployment = mock(Deployment.class);
    ProcessDefinition deployedDefinition = mock(ProcessDefinition.class);

    service.repositoryService = repositoryService;

    when(repositoryService.createProcessDefinitionQuery()).thenReturn(latestDefinitionQuery, deployedDefinitionQuery);
    when(latestDefinitionQuery.processDefinitionKey("flow-a")).thenReturn(latestDefinitionQuery);
    when(latestDefinitionQuery.orderByProcessDefinitionVersion()).thenReturn(latestDefinitionQuery);
    when(latestDefinitionQuery.desc()).thenReturn(latestDefinitionQuery);
    when(latestDefinitionQuery.listPage(0, 1)).thenReturn(Collections.singletonList(latestDefinition));
    when(latestDefinition.getDeploymentId()).thenReturn("deployment-existing");
    when(repositoryService.getResourceAsStream("deployment-existing", "flowDefinition"))
        .thenReturn(new ByteArrayInputStream(v2FlowJsonWithDifferentDefinition().getBytes(StandardCharsets.UTF_8)));
    when(repositoryService.createDeployment()).thenReturn(deploymentBuilder);
    when(deploymentBuilder.deploy()).thenReturn(deployment);
    when(deployment.getId()).thenReturn("deployment-new");
    when(deployedDefinitionQuery.deploymentId("deployment-new")).thenReturn(deployedDefinitionQuery);
    when(deployedDefinitionQuery.singleResult()).thenReturn(deployedDefinition);
    when(deployedDefinition.getId()).thenReturn("process-definition-new");

    String processDefinitionId = service.deploy("flow-a", v2FlowJson()).get();

    assertEquals("process-definition-new", processDefinitionId);
    verify(deploymentBuilder).addString("flowDefinition", canonicalV2FlowJson(v2FlowJson()));
  }

  @Test
  void deployFailsWithoutCreatingDeploymentWhenLatestResourceLookupFails() {
    FlowableFlowManageService service = new FlowableFlowManageService();
    RepositoryService repositoryService = mock(RepositoryService.class);
    ProcessDefinitionQuery latestDefinitionQuery = mock(ProcessDefinitionQuery.class);
    ProcessDefinition latestDefinition = mock(ProcessDefinition.class);

    service.repositoryService = repositoryService;

    when(repositoryService.createProcessDefinitionQuery()).thenReturn(latestDefinitionQuery);
    when(latestDefinitionQuery.processDefinitionKey("flow-a")).thenReturn(latestDefinitionQuery);
    when(latestDefinitionQuery.orderByProcessDefinitionVersion()).thenReturn(latestDefinitionQuery);
    when(latestDefinitionQuery.desc()).thenReturn(latestDefinitionQuery);
    when(latestDefinitionQuery.listPage(0, 1)).thenReturn(Collections.singletonList(latestDefinition));
    when(latestDefinition.getDeploymentId()).thenReturn("deployment-existing");
    when(repositoryService.getResourceAsStream("deployment-existing", "flowDefinition"))
        .thenThrow(new IllegalStateException("repository unavailable"));

    Try<String> result = service.deploy("flow-a", v2FlowJson());

    assertDeployReadFailure(result);
    verify(repositoryService, never()).createDeployment();
  }

  @Test
  void deployFailsWithoutCreatingDeploymentWhenLatestResourceStreamReadFails() {
    FlowableFlowManageService service = new FlowableFlowManageService();
    RepositoryService repositoryService = mock(RepositoryService.class);
    ProcessDefinitionQuery latestDefinitionQuery = mock(ProcessDefinitionQuery.class);
    ProcessDefinition latestDefinition = mock(ProcessDefinition.class);

    service.repositoryService = repositoryService;

    when(repositoryService.createProcessDefinitionQuery()).thenReturn(latestDefinitionQuery);
    when(latestDefinitionQuery.processDefinitionKey("flow-a")).thenReturn(latestDefinitionQuery);
    when(latestDefinitionQuery.orderByProcessDefinitionVersion()).thenReturn(latestDefinitionQuery);
    when(latestDefinitionQuery.desc()).thenReturn(latestDefinitionQuery);
    when(latestDefinitionQuery.listPage(0, 1)).thenReturn(Collections.singletonList(latestDefinition));
    when(latestDefinition.getDeploymentId()).thenReturn("deployment-existing");
    when(repositoryService.getResourceAsStream("deployment-existing", "flowDefinition"))
        .thenReturn(new BrokenInputStream());

    Try<String> result = service.deploy("flow-a", v2FlowJson());

    assertDeployReadFailure(result);
    verify(repositoryService, never()).createDeployment();
  }

  @Test
  void deployFailsWithoutCreatingDeploymentWhenLatestResourceCannotBeNormalized() {
    FlowableFlowManageService service = new FlowableFlowManageService();
    RepositoryService repositoryService = mock(RepositoryService.class);
    ProcessDefinitionQuery latestDefinitionQuery = mock(ProcessDefinitionQuery.class);
    ProcessDefinition latestDefinition = mock(ProcessDefinition.class);

    service.repositoryService = repositoryService;

    when(repositoryService.createProcessDefinitionQuery()).thenReturn(latestDefinitionQuery);
    when(latestDefinitionQuery.processDefinitionKey("flow-a")).thenReturn(latestDefinitionQuery);
    when(latestDefinitionQuery.orderByProcessDefinitionVersion()).thenReturn(latestDefinitionQuery);
    when(latestDefinitionQuery.desc()).thenReturn(latestDefinitionQuery);
    when(latestDefinitionQuery.listPage(0, 1)).thenReturn(Collections.singletonList(latestDefinition));
    when(latestDefinition.getDeploymentId()).thenReturn("deployment-existing");
    when(repositoryService.getResourceAsStream("deployment-existing", "flowDefinition"))
        .thenReturn(new ByteArrayInputStream("{".getBytes(StandardCharsets.UTF_8)));

    Try<String> result = service.deploy("flow-a", v2FlowJson());

    assertDeployReadFailure(result);
    verify(repositoryService, never()).createDeployment();
  }

  @Test
  void deployReusesLatestDefinitionWhenSemanticV2JsonOnlyDiffersByFieldOrder() {
    FlowableFlowManageService service = new FlowableFlowManageService();
    RepositoryService repositoryService = mock(RepositoryService.class);
    ProcessDefinitionQuery latestDefinitionQuery = mock(ProcessDefinitionQuery.class);
    ProcessDefinitionQuery deploymentDefinitionQuery = mock(ProcessDefinitionQuery.class);
    ProcessDefinition latestDefinition = mock(ProcessDefinition.class);
    ProcessDefinition deployedDefinition = mock(ProcessDefinition.class);

    service.repositoryService = repositoryService;

    when(repositoryService.createProcessDefinitionQuery()).thenReturn(latestDefinitionQuery, deploymentDefinitionQuery);
    when(latestDefinitionQuery.processDefinitionKey("flow-a")).thenReturn(latestDefinitionQuery);
    when(latestDefinitionQuery.orderByProcessDefinitionVersion()).thenReturn(latestDefinitionQuery);
    when(latestDefinitionQuery.desc()).thenReturn(latestDefinitionQuery);
    when(latestDefinitionQuery.listPage(0, 1)).thenReturn(Collections.singletonList(latestDefinition));
    when(latestDefinition.getDeploymentId()).thenReturn("deployment-existing");
    when(repositoryService.getResourceAsStream("deployment-existing", "flowDefinition"))
        .thenReturn(new ByteArrayInputStream(v2FlowJsonWithDifferentFieldOrder().getBytes(StandardCharsets.UTF_8)));
    when(deploymentDefinitionQuery.deploymentId("deployment-existing")).thenReturn(deploymentDefinitionQuery);
    when(deploymentDefinitionQuery.singleResult()).thenReturn(deployedDefinition);
    when(deployedDefinition.getId()).thenReturn("process-definition-existing");

    String processDefinitionId = service.deploy("flow-a", v2FlowJson()).get();

    assertEquals("process-definition-existing", processDefinitionId);
    verify(repositoryService, never()).createDeployment();
  }

  private String v2FlowJson() {
    return """
        {
          "formatVersion": 2,
          "id": "flow-a",
          "name": "采购审批",
          "category": "",
          "icon": "shenpi",
          "description": "",
          "formType": "default",
          "formKey": "purchase-form",
          "nodes": [
            { "type": "发起节点", "name": "发起节点", "id": "start-a", "permission": [] },
            { "type": "审批节点", "name": "经理审批", "id": "approve-a", "subType": "单签", "candidates": [], "permission": [], "actions": ["reject"] }
          ],
          "edges": [
            { "id": "e1", "type": "SEQUENCE", "sourceId": "start-a", "targetId": "approve-a" }
          ]
        }
        """;
  }

  private String v2FlowJsonWithEditorLayout() {
    return """
        {
          "formatVersion": 2,
          "id": "flow-a",
          "name": "采购审批",
          "category": "",
          "icon": "shenpi",
          "description": "",
          "formType": "default",
          "formKey": "purchase-form",
          "editorLayout": {
            "nodePositions": {
              "start-a": { "x": 120, "y": 80 },
              "approve-a": { "x": 420, "y": 80 }
            }
          },
          "nodes": [
            { "type": "发起节点", "name": "发起节点", "id": "start-a", "permission": [] },
            { "type": "审批节点", "name": "经理审批", "id": "approve-a", "subType": "单签", "candidates": [], "permission": [], "actions": ["reject"] }
          ],
          "edges": [
            { "id": "e1", "type": "SEQUENCE", "sourceId": "start-a", "targetId": "approve-a" }
          ]
        }
        """;
  }

  private String v2FlowJsonWithDifferentFieldOrder() {
    return """
        {
          "name": "采购审批",
          "formatVersion": 2,
          "id": "flow-a",
          "formKey": "purchase-form",
          "formType": "default",
          "description": "",
          "icon": "shenpi",
          "category": "",
          "nodes": [
            { "id": "start-a", "permission": [], "type": "发起节点", "name": "发起节点" },
            { "name": "经理审批", "actions": ["reject"], "subType": "单签", "permission": [], "candidates": [], "id": "approve-a", "type": "审批节点" }
          ],
          "edges": [
            { "targetId": "approve-a", "sourceId": "start-a", "type": "SEQUENCE", "id": "e1" }
          ]
        }
        """;
  }

  private String v2FlowJsonWithDifferentDefinition() {
    return """
        {
          "formatVersion": 2,
          "id": "flow-a",
          "name": "采购审批旧版",
          "category": "",
          "icon": "shenpi",
          "description": "",
          "formType": "default",
          "formKey": "purchase-form",
          "nodes": [
            { "type": "发起节点", "name": "发起节点", "id": "start-a", "permission": [] },
            { "type": "审批节点", "name": "经理审批", "id": "approve-a", "subType": "单签", "candidates": [], "permission": [], "actions": ["reject"] }
          ],
          "edges": [
            { "id": "e1", "type": "SEQUENCE", "sourceId": "start-a", "targetId": "approve-a" }
          ]
        }
        """;
  }

  private String canonicalV2FlowJson(String rawJson) {
    JsonNode normalized = flowDefinitionV2Normalizer.normalizeToJsonNode(rawJson).get();
    return Json.toJsonString(normalized);
  }

  private ListAppender<ILoggingEvent> attachLogAppender() {
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    return appender;
  }

  private List<ILoggingEvent> missingResourceWarnings() {
    return logAppender.list.stream()
        .filter(event -> event.getLevel().equals(Level.WARN))
        .filter(event -> event.getFormattedMessage().contains("Flowable deployment resource missing"))
        .toList();
  }

  private void assertDeployReadFailure(Try<String> result) {
    assertTrue(result.isFailure());
    assertTrue(result.getCause() instanceof InternalException);
    assertTrue(result.getCause().getMessage().contains("flow-a"));
    assertTrue(result.getCause().getMessage().contains("deployment-existing"));
    assertTrue(result.getCause().getMessage().contains("flowDefinition"));
  }

  private static class BrokenInputStream extends InputStream {

    @Override
    public int read() throws IOException {
      throw new IOException("stream broken");
    }
  }
}
