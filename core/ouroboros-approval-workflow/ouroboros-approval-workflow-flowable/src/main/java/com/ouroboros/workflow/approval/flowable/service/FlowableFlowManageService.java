package com.ouroboros.workflow.approval.flowable.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.ProcessDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.vavr.control.Try;

import com.ouroboros.exception.InternalException;
import com.ouroboros.util.Json;
import com.ouroboros.workflow.approval.BuiltInFlowManageService;
import com.ouroboros.workflow.approval.definition.codec.CompositeFlowDefinitionCodec;
import com.ouroboros.workflow.approval.definition.codec.FlowDefinitionV2Normalizer;
import com.ouroboros.workflow.approval.flowable.util.BpmnUtil;
import com.ouroboros.workflow.approval.transpiler.ApprovalWorkflowTranspilerBuilder;

@Service
public class FlowableFlowManageService implements BuiltInFlowManageService {
  private static final Logger LOGGER = LoggerFactory.getLogger(FlowableFlowManageService.class);
  private static final String FLOW_DEFINITION_RESOURCE_NAME = "flowDefinition";

  final CompositeFlowDefinitionCodec flowDefinitionCodec = new CompositeFlowDefinitionCodec();
  final FlowDefinitionV2Normalizer flowDefinitionV2Normalizer = new FlowDefinitionV2Normalizer();

  @Autowired
  RepositoryService repositoryService;

  @Override
  @Transactional
  public Try<String> deploy(String flowKey, String flowDefinitionJson) {
    return deploy(flowKey, flowDefinitionJson, true);
  }

  @Override
  @Transactional
  public Try<String> deployNewVersion(String flowKey, String flowDefinitionJson) {
    return deploy(flowKey, flowDefinitionJson, false);
  }

  @Override
  @Transactional
  public Try<Void> offline(String processDefinitionId) {
    return Try.run(() -> repositoryService.suspendProcessDefinitionById(processDefinitionId, false, null));
  }

  private Try<String> deploy(String flowKey, String flowDefinitionJson, boolean reuseUnchangedLatestDefinition) {
    return flowDefinitionV2Normalizer.normalizeToJsonNode(flowDefinitionJson)
        .flatMap(canonicalFlowDefinition -> Try.of(() -> {
      String canonicalFlowDefinitionJson = Json.toJsonString(canonicalFlowDefinition);
      com.ouroboros.workflow.approval.definition.graph.StructuredFlowDefinition structuredDefinition = flowDefinitionCodec
          .decode(canonicalFlowDefinitionJson)
          .get();
      structuredDefinition.setId(flowKey);
      com.ouroboros.workflow.approval.transpiler.ApprovalWorkflowTranspiler<BpmnModel> transpiler = ApprovalWorkflowTranspilerBuilder
          .get(BpmnModel.class)
          .build();
      String oldDeploymentId = repositoryService.createProcessDefinitionQuery()
          .processDefinitionKey(flowKey)
          .orderByProcessDefinitionVersion()
          .desc()
          .listPage(0, 1)
          .stream()
          .map(ProcessDefinition::getDeploymentId)
          .findFirst()
          .orElse(null);
      if (reuseUnchangedLatestDefinition && oldDeploymentId != null) {
        Optional<String> deployedDefinitionJson = readDeployedDefinition(flowKey, oldDeploymentId).get();
        if (deployedDefinitionJson.isPresent()
            && Objects.equals(canonicalFlowDefinition,
                normalizeDeployedDefinition(flowKey, oldDeploymentId, deployedDefinitionJson.get()).get())) {
          ProcessDefinition processDefinition = getProcessDefinition(oldDeploymentId);
          if (processDefinition.isSuspended()) {
            repositoryService.activateProcessDefinitionById(processDefinition.getId(), false, null);
          }
          return processDefinition.getId();
        }
      }

      BpmnModel model = transpiler.transpile(structuredDefinition);
      BpmnUtil.saveFlowDefinitionRawJson(model.getProcesses().get(0), canonicalFlowDefinitionJson);

      Deployment deployment = repositoryService.createDeployment()
          .name(structuredDefinition.getName())
          .key(flowKey)
          .category(structuredDefinition.getCategory())
          .addString(FLOW_DEFINITION_RESOURCE_NAME, canonicalFlowDefinitionJson)
          .addBpmnModel(flowKey + ".bpmn", model)
          .deploy();

      return getProcessDefinitionId(deployment.getId());
    }));
  }

  private Try<Optional<String>> readDeployedDefinition(String flowKey, String deploymentId) {
    return Try.of(() -> {
      try (InputStream stream = repositoryService.getResourceAsStream(deploymentId, FLOW_DEFINITION_RESOURCE_NAME)) {
        if (stream == null) {
          LOGGER.warn(
              "Flowable deployment resource missing: flowKey={}, deploymentId={}, resourceName={}",
              flowKey,
              deploymentId,
              FLOW_DEFINITION_RESOURCE_NAME);
          return Optional.empty();
        }
        return Optional.of(readStream(stream));
      } catch (Exception e) {
        throw new InternalException(deployedDefinitionFailureMessage(flowKey, deploymentId), e);
      }
    });
  }

  private String readStream(InputStream stream) throws IOException {
    try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
      return bufferedReader.lines().collect(Collectors.joining("\n"));
    }
  }

  private Try<JsonNode> normalizeDeployedDefinition(String flowKey, String deploymentId, String flowDefinitionJson) {
    Try<JsonNode> normalized = flowDefinitionV2Normalizer.normalizeToJsonNode(flowDefinitionJson);
    if (normalized.isFailure()) {
      return Try.failure(
          new InternalException(deployedDefinitionFailureMessage(flowKey, deploymentId), normalized.getCause()));
    }
    return normalized;
  }

  private String deployedDefinitionFailureMessage(String flowKey, String deploymentId) {
    return "读取已部署流程定义资源失败: flowKey=" + flowKey
        + ", deploymentId=" + deploymentId
        + ", resourceName=" + FLOW_DEFINITION_RESOURCE_NAME;
  }

  private String getProcessDefinitionId(String deploymentId) {
    return getProcessDefinition(deploymentId).getId();
  }

  private ProcessDefinition getProcessDefinition(String deploymentId) {
    return repositoryService.createProcessDefinitionQuery()
        .deploymentId(deploymentId)
        .singleResult();
  }
}
