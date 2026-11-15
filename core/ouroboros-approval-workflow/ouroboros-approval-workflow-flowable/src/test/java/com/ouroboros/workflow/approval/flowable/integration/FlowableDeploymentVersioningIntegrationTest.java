package com.ouroboros.workflow.approval.flowable.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.List;

import org.flowable.engine.ProcessEngine;
import org.flowable.engine.ProcessEngineConfiguration;
import org.flowable.engine.ProcessEngines;
import org.flowable.engine.repository.ProcessDefinition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.ouroboros.workflow.approval.flowable.service.FlowableFlowManageService;

class FlowableDeploymentVersioningIntegrationTest {
  private ProcessEngine processEngine;

  @AfterEach
  void tearDown() {
    if (processEngine != null) {
      ProcessEngines.unregister(processEngine);
      processEngine.close();
      processEngine = null;
    }
  }

  @Test
  void deployCreatesNewProcessDefinitionVersionWhenDefinitionChanges() {
    FlowableFlowManageService service = new FlowableFlowManageService();
    setField(service, "repositoryService", processEngine().getRepositoryService());

    String v1ProcessDefinitionId = service.deploy("flow-versioning-regression", versionedFlowJson("一级审批")).get();
    String v2ProcessDefinitionId = service.deploy("flow-versioning-regression", versionedFlowJson("一级审批-升级"))
        .get();

    List<ProcessDefinition> definitions = processEngine().getRepositoryService().createProcessDefinitionQuery()
        .processDefinitionKey("flow-versioning-regression")
        .orderByProcessDefinitionVersion()
        .asc()
        .list();

    assertEquals(2, definitions.size());
    assertEquals(1, definitions.get(0).getVersion());
    assertEquals(2, definitions.get(1).getVersion());
    assertEquals(v1ProcessDefinitionId, definitions.get(0).getId());
    assertEquals(v2ProcessDefinitionId, definitions.get(1).getId());
  }

  @Test
  void deployReusesExistingProcessDefinitionWhenDefinitionUnchanged() {
    FlowableFlowManageService service = new FlowableFlowManageService();
    setField(service, "repositoryService", processEngine().getRepositoryService());

    String firstDeployment = service.deploy("flow-versioning-regression-stable", versionedFlowJson("一级审批")).get();
    String secondDeployment = service.deploy("flow-versioning-regression-stable", versionedFlowJson("一级审批")).get();

    List<ProcessDefinition> definitions = processEngine().getRepositoryService().createProcessDefinitionQuery()
        .processDefinitionKey("flow-versioning-regression-stable")
        .list();

    assertEquals(1, definitions.size());
    assertEquals(firstDeployment, secondDeployment);
    assertTrue(firstDeployment.startsWith("flow-versioning-regression-stable"));
  }

  @Test
  void deployNewVersionCreatesProcessDefinitionVersionWhenDefinitionUnchanged() {
    FlowableFlowManageService service = new FlowableFlowManageService();
    setField(service, "repositoryService", processEngine().getRepositoryService());

    String firstDeployment = service.deploy("flow-versioning-regression-reset", versionedFlowJson("一级审批")).get();
    String secondDeployment = service.deployNewVersion("flow-versioning-regression-reset", versionedFlowJson("一级审批")).get();

    List<ProcessDefinition> definitions = processEngine().getRepositoryService().createProcessDefinitionQuery()
        .processDefinitionKey("flow-versioning-regression-reset")
        .orderByProcessDefinitionVersion()
        .asc()
        .list();

    assertEquals(2, definitions.size());
    assertEquals(1, definitions.get(0).getVersion());
    assertEquals(2, definitions.get(1).getVersion());
    assertEquals(firstDeployment, definitions.get(0).getId());
    assertEquals(secondDeployment, definitions.get(1).getId());
    assertNotEquals(firstDeployment, secondDeployment);
  }

  private ProcessEngine processEngine() {
    if (processEngine == null) {
      processEngine = ProcessEngineConfiguration.createStandaloneInMemProcessEngineConfiguration()
          .setEngineName(ProcessEngines.NAME_DEFAULT)
          .setDatabaseSchemaUpdate("true")
          .setAsyncExecutorActivate(false)
          .setAsyncHistoryExecutorActivate(false)
          .setHistory("full")
          .buildProcessEngine();
    }
    return processEngine;
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

  private String versionedFlowJson(String approvalNodeName) {
    return "{"
        + "\"formatVersion\":2,"
        + "\"id\":\"flow-versioning-regression\","
        + "\"name\":\"版本化回归流程\","
        + "\"category\":\"\","
        + "\"icon\":\"shenpi\","
        + "\"description\":\"\","
        + "\"formType\":\"default\","
        + "\"formKey\":\"runtime-form\","
        + "\"nodes\":["
        + "{\"type\":\"发起节点\",\"name\":\"发起节点\",\"id\":\"startnode\",\"permission\":[]},"
        + "{\"type\":\"审批节点\",\"name\":\"" + approvalNodeName + "\",\"id\":\"approve-a\",\"subType\":\"单签\",\"candidates\":[],\"permission\":[],\"actions\":[\"reject\"]},"
        + "{\"type\":\"结束节点\",\"name\":\"结束\",\"id\":\"end-a\"}"
        + "],"
        + "\"edges\":["
        + "{\"id\":\"e1\",\"type\":\"SEQUENCE\",\"sourceId\":\"startnode\",\"targetId\":\"approve-a\"},"
        + "{\"id\":\"e2\",\"type\":\"SEQUENCE\",\"sourceId\":\"approve-a\",\"targetId\":\"end-a\"}"
        + "]"
        + "}";
  }
}
