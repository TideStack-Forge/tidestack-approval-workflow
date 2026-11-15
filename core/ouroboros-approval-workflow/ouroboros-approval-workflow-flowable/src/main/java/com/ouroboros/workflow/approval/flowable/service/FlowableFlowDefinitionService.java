package com.ouroboros.workflow.approval.flowable.service;

import static com.ouroboros.workflow.approval.flowable.util.BpmnUtil.readFlowDefinition;
import static com.ouroboros.workflow.approval.flowable.util.BpmnUtil.readStructuredFlowDefinition;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.flowable.common.engine.impl.variable.MapDelegateVariableContainer;
import org.flowable.engine.RepositoryService;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import io.vavr.control.Try;

import com.ouroboros.exception.PlatformException;
import com.ouroboros.util.Converter;
import com.ouroboros.workflow.approval.BuiltInFlowDefinitionService;
import com.ouroboros.workflow.approval.definition.FlowDefinition;
import com.ouroboros.workflow.approval.definition.FlowNode;
import com.ouroboros.workflow.approval.definition.condition.Condition;
import com.ouroboros.workflow.approval.definition.graph.StructuredFlowDefinition;
import com.ouroboros.workflow.approval.definition.nodes.ApprovalNode;
import com.ouroboros.workflow.approval.definition.v2.FlowEdgeV2;
import com.ouroboros.workflow.approval.definition.codec.FlowDefinitionV2Normalizer;
import com.ouroboros.workflow.approval.model.FlowDefinitionVersion;
import com.ouroboros.workflow.approval.util.StructuredFlowRuntimeNavigator;
import com.ouroboros.workflow.approval.flowable.util.BpmnUtil;

@Service
public class FlowableFlowDefinitionService implements BuiltInFlowDefinitionService {
  final static Logger logger = LoggerFactory.getLogger(FlowableFlowDefinitionService.class);
  final StructuredFlowRuntimeNavigator runtimeNavigator = new StructuredFlowRuntimeNavigator();
  final FlowDefinitionV2Normalizer flowDefinitionV2Normalizer = new FlowDefinitionV2Normalizer();

  @Autowired
  RepositoryService repositoryService;
  @Autowired
  SpringProcessEngineConfiguration processEngineConfiguration;

  @Override
  public Try<FlowDefinition> getFlowDefinition(String flowKey) {
    var definitionTry = Try.of(() -> repositoryService.createProcessDefinitionQuery()
        .processDefinitionKey(flowKey)
        .latestVersion()
        .singleResult());
    return definitionTry.mapTry(definition -> {
      if (definition == null) {
        throw new PlatformException("流程定义不存在");
      }
      var model = repositoryService.getBpmnModel(definition.getId());
      return readFlowDefinition(model);
    });
  }

  @Override
  public List<FlowNode> getFlowNodes(String flowKey) {
    var definitionTry = getStructuredFlowDefinition(flowKey);
    if (definitionTry.isFailure()) {
      logger.error("获取流程定义失败", definitionTry.getCause());
      return Collections.emptyList();
    }
    return runtimeNavigator.getFlowNodes(definitionTry.get());
  }

  @Override
  public Try<JsonNode> getFlowDefinitionV2(String flowKey, Integer version) {
    return Try.of(() -> repositoryService.createProcessDefinitionQuery()
            .processDefinitionKey(flowKey)
            .processDefinitionVersion(version)
            .singleResult())
        .mapTry(definition -> {
          if (definition == null) {
            throw new PlatformException("流程定义不存在");
          }
          return repositoryService.getBpmnModel(definition.getId());
        })
        .map(BpmnUtil::readFlowDefinitionRawJson)
        .flatMap(flowDefinitionV2Normalizer::normalizeToJsonNode)
        .mapTry(definition -> {
          if (definition == null) {
            throw new PlatformException("流程定义不存在");
          }
          return definition;
        });
  }

  @Override
  public List<ApprovalNode> getUserSelectCandidateApprovalNodes(String flowKey) {
    var definitionTry = getStructuredFlowDefinition(flowKey);
    if (definitionTry.isFailure()) {
      logger.error("获取流程定义(flowKey:{})失败", flowKey, definitionTry.getCause());
      return Collections.emptyList();
    }
    return runtimeNavigator.getUserSelectCandidateApprovalNodes(definitionTry.get());
  }

  @Override
  public List<ApprovalNode> getUserSelectCandidateApprovalNodes(String flowKey, Map<String, Object> conditionContext) {
    var definitionTry = getStructuredFlowDefinition(flowKey);
    if (definitionTry.isFailure()) {
      logger.error("获取流程定义(flowKey:{})失败", flowKey, definitionTry.getCause());
      return Collections.emptyList();
    }
    return runtimeNavigator.getUserSelectCandidateApprovalNodes(definitionTry.get(), getBranchEdgeFilter(conditionContext));
  }

  @Override
  public Try<List<FlowDefinitionVersion>> listFlowDefinitionVersions(String flowKey) {
    return Try.of(() -> repositoryService.createProcessDefinitionQuery()
            .processDefinitionKey(flowKey)
            .orderByProcessDefinitionVersion()
            .desc()
            .list())
        .map(definitions -> definitions.stream()
            .map(definition -> {
              var deployment = repositoryService.createDeploymentQuery()
                  .deploymentId(definition.getDeploymentId())
                  .singleResult();
              FlowDefinitionVersion version = new FlowDefinitionVersion();
              version.setVersion(definition.getVersion());
              version.setProcessDefinitionId(definition.getId());
              version.setDeploymentId(definition.getDeploymentId());
              version.setDeploymentTime(toLocalDateTime(Optional.ofNullable(deployment)
                  .map(org.flowable.engine.repository.Deployment::getDeploymentTime)
                  .orElse(null)));
              version.setDeployedBy(Optional.ofNullable(deployment)
                  .map(org.flowable.engine.repository.Deployment::getTenantId)
                  .orElse(""));
              return version;
            })
            .collect(Collectors.toList()));
  }

  @Override
  public Try<FlowDefinitionVersion> getFlowDefinitionVersion(String flowKey, Integer version) {
    return Try.of(() -> repositoryService.createProcessDefinitionQuery()
            .processDefinitionKey(flowKey)
            .processDefinitionVersion(version)
            .singleResult())
        .mapTry(definition -> {
          if (definition == null) {
            throw new PlatformException("流程定义版本不存在");
          }
          var deployment = repositoryService.createDeploymentQuery()
              .deploymentId(definition.getDeploymentId())
              .singleResult();
          FlowDefinitionVersion result = new FlowDefinitionVersion();
          result.setVersion(definition.getVersion());
          result.setProcessDefinitionId(definition.getId());
          result.setDeploymentId(definition.getDeploymentId());
          result.setDeploymentTime(toLocalDateTime(Optional.ofNullable(deployment)
              .map(org.flowable.engine.repository.Deployment::getDeploymentTime)
              .orElse(null)));
          result.setDeployedBy(Optional.ofNullable(deployment)
              .map(org.flowable.engine.repository.Deployment::getTenantId)
              .orElse(""));
          result.setDefinition(BpmnUtil.readFlowDefinitionRawJson(repositoryService.getBpmnModel(definition.getId())));
          return result;
        });
  }

  private Predicate<FlowEdgeV2> getBranchEdgeFilter(Map<String, Object> conditionContext) {
    var variableContainer = new MapDelegateVariableContainer();
    Optional.ofNullable(conditionContext).orElse(Collections.emptyMap())
        .forEach(variableContainer::setVariable);

    return edge -> Try.of(() -> createExpression(BpmnUtil.conditionExpression(
            getConditionExpression(edge.getCondition()))).getValue(variableContainer))
        .map(Converter::toBoolean)
        .onFailure(e -> logger.error("表达式解析失败", e))
        .getOrElse(false);
  }

  private String getConditionExpression(Condition condition) {
    return Optional.ofNullable(condition)
        .map(Condition::toExpression)
        .orElse("true");
  }

  @Override
  public Try<StructuredFlowDefinition> getStructuredFlowDefinition(String flowKey) {
    var definitionTry = Try.of(() -> repositoryService.createProcessDefinitionQuery()
        .processDefinitionKey(flowKey)
        .latestVersion()
        .singleResult());
    return definitionTry
        .mapTry(definition -> {
          if (definition == null) {
            throw new PlatformException("流程定义不存在");
          }
          var model = repositoryService.getBpmnModel(definition.getId());
          return readStructuredFlowDefinition(model);
        })
        .mapTry(definition -> {
          if (definition == null) {
            throw new PlatformException("流程定义不存在");
          }
          return definition;
        });
  }

  private org.flowable.common.engine.api.delegate.Expression createExpression(String expr) {
    return processEngineConfiguration.getExpressionManager().createExpression(expr);
  }

  private LocalDateTime toLocalDateTime(java.util.Date value) {
    if (value == null) {
      return null;
    }
    return LocalDateTime.ofInstant(value.toInstant(), ZoneId.systemDefault());
  }
}
