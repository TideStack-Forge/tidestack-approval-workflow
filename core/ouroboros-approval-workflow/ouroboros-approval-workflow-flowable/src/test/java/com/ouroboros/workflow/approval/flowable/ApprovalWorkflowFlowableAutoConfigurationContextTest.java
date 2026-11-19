package com.ouroboros.workflow.approval.flowable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.spring.boot.ProcessEngineAutoConfiguration;
import org.flowable.spring.boot.ProcessEngineServicesAutoConfiguration;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.support.SpringFactoriesLoader;

import com.ouroboros.workflow.approval.FlowDefinitionService;
import com.ouroboros.workflow.approval.FlowInstanceService;
import com.ouroboros.workflow.approval.FlowManageService;
import com.ouroboros.workflow.approval.FlowTaskService;
import com.ouroboros.workflow.approval.TrustedFlowTaskActionService;
import com.ouroboros.workflow.approval.event.InstanceEventDispatcher;
import com.ouroboros.workflow.approval.event.TaskEventDispatcher;
import com.ouroboros.workflow.approval.flowable.action.FlowableApproveAction;
import com.ouroboros.workflow.approval.flowable.action.FlowableRejectAction;
import com.ouroboros.workflow.approval.flowable.action.FlowableRollbackAction;
import com.ouroboros.workflow.approval.flowable.listener.AddSignTaskCompletionListener;
import com.ouroboros.workflow.approval.flowable.service.FlowableFlowDefinitionService;
import com.ouroboros.workflow.approval.flowable.service.FlowableFlowInstanceService;
import com.ouroboros.workflow.approval.flowable.service.FlowableFlowManageService;
import com.ouroboros.workflow.approval.flowable.service.FlowableFlowTaskService;
import com.ouroboros.workflow.approval.lifecycle.ApprovalCaseService;

class ApprovalWorkflowFlowableAutoConfigurationContextTest {
  private static final String BOOT3_AUTO_CONFIGURATION_IMPORTS =
      "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports";

  @Test
  void shouldDiscoverAutoConfigurationThroughSpringFactories() {
    List<String> factoryNames = SpringFactoriesLoader.loadFactoryNames(
        EnableAutoConfiguration.class,
        Thread.currentThread().getContextClassLoader());

    assertTrue(factoryNames.contains(ApprovalWorkflowFlowableAutoConfiguration.class.getName()));
  }

  @Test
  void shouldDiscoverAutoConfigurationThroughBoot3Imports() throws IOException {
    List<String> importNames = loadBoot3AutoConfigurationImports();

    assertTrue(importNames.contains(ApprovalWorkflowFlowableAutoConfiguration.class.getName()));
  }

  @Test
  void shouldBridgeFlowableProcessAutoConfigurationsThroughBoot3Imports() throws IOException {
    List<String> importNames = loadBoot3AutoConfigurationImports();

    assertTrue(importNames.contains(ProcessEngineAutoConfiguration.class.getName()));
    assertTrue(importNames.contains(ProcessEngineServicesAutoConfiguration.class.getName()));
  }

  @Test
  void shouldRegisterFlowableServicesAndActionsWhenEngineIsFlowable() {
    contextRunner()
        .withPropertyValues("ouroboros.workflow.approval.engine=flowable")
        .withConfiguration(AutoConfigurations.of(ApprovalWorkflowFlowableAutoConfiguration.class))
        .run(context -> {
          assertNull(context.getStartupFailure());
          assertEquals(1, context.getBeansOfType(FlowManageService.class).size());
          assertEquals(1, context.getBeansOfType(FlowDefinitionService.class).size());
          assertEquals(1, context.getBeansOfType(FlowInstanceService.class).size());
          assertEquals(1, context.getBeansOfType(FlowTaskService.class).size());
          assertEquals(1, context.getBeansOfType(TrustedFlowTaskActionService.class).size());
          assertEquals(1, context.getBeansOfType(FlowableFlowManageService.class).size());
          assertEquals(1, context.getBeansOfType(FlowableFlowDefinitionService.class).size());
          assertEquals(1, context.getBeansOfType(FlowableFlowInstanceService.class).size());
          assertEquals(1, context.getBeansOfType(FlowableFlowTaskService.class).size());
          assertEquals(1, context.getBeansOfType(FlowableApproveAction.class).size());
          assertEquals(1, context.getBeansOfType(FlowableRejectAction.class).size());
          assertEquals(1, context.getBeansOfType(FlowableRollbackAction.class).size());
          assertEquals(1, context.getBeansOfType(AddSignTaskCompletionListener.class).size());
          assertEquals(1, context.getBeansOfType(InstanceEventDispatcher.class).size());
          assertEquals(1, context.getBeansOfType(TaskEventDispatcher.class).size());
        });
  }

  /**
   * activiti6 已移出主线（legacy-compat/），引擎属性只接受 flowable：
   * 取任何其他值时必须在启动期失败，而不是"条件不成立 + 引擎 Bean 全部缺失"。
   */
  @Test
  void shouldFailFastWhenEngineIsNotFlowable() {
    contextRunner()
        .withPropertyValues("ouroboros.workflow.approval.engine=activiti6")
        .withConfiguration(AutoConfigurations.of(
            ApprovalWorkflowEngineSelectionVerifier.class,
            ApprovalWorkflowFlowableAutoConfiguration.class))
        .run(context -> {
          // 启动失败即整体不可用：不再断言 Bean 数量（未启动的上下文没有可断言的 Bean 图）
          assertNotNull(context.getStartupFailure());
          assertTrue(rootMessages(context.getStartupFailure()).stream()
              .anyMatch(message -> message.contains("WORKFLOW_ENGINE_NOT_FOUND")));
        });
  }

  private static List<String> rootMessages(Throwable failure) {
    List<String> messages = new ArrayList<>();
    for (Throwable current = failure; current != null; current = current.getCause()) {
      if (current.getMessage() != null) {
        messages.add(current.getMessage());
      }
    }
    return messages;
  }

  @Test
  void shouldRegisterFlowableServicesWhenEnginePropertyIsMissing() {
    contextRunner()
        .withConfiguration(AutoConfigurations.of(ApprovalWorkflowFlowableAutoConfiguration.class))
        .run(context -> {
          assertNull(context.getStartupFailure());
          assertEquals(1, context.getBeansOfType(FlowManageService.class).size());
          assertEquals(1, context.getBeansOfType(FlowDefinitionService.class).size());
          assertEquals(1, context.getBeansOfType(FlowInstanceService.class).size());
          assertEquals(1, context.getBeansOfType(FlowTaskService.class).size());
          assertEquals(1, context.getBeansOfType(TrustedFlowTaskActionService.class).size());
          assertEquals(1, context.getBeansOfType(FlowableFlowManageService.class).size());
          assertEquals(1, context.getBeansOfType(FlowableFlowDefinitionService.class).size());
          assertEquals(1, context.getBeansOfType(FlowableFlowInstanceService.class).size());
          assertEquals(1, context.getBeansOfType(FlowableFlowTaskService.class).size());
          assertEquals(1, context.getBeansOfType(AddSignTaskCompletionListener.class).size());
          assertEquals(1, context.getBeansOfType(InstanceEventDispatcher.class).size());
          assertEquals(1, context.getBeansOfType(TaskEventDispatcher.class).size());
        });
  }

  private ApplicationContextRunner contextRunner() {
    return new ApplicationContextRunner()
        .withBean(ApprovalCaseService.class, () -> mock(ApprovalCaseService.class))
        .withBean(TaskService.class, () -> mock(TaskService.class))
        .withBean(RuntimeService.class, () -> mock(RuntimeService.class))
        .withBean(RepositoryService.class, () -> mock(RepositoryService.class))
        .withBean(HistoryService.class, () -> mock(HistoryService.class))
        .withBean(SpringProcessEngineConfiguration.class, () -> mock(SpringProcessEngineConfiguration.class));
  }

  private List<String> loadBoot3AutoConfigurationImports() throws IOException {
    List<String> importNames = new ArrayList<>();
    ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
    Enumeration<URL> resources = classLoader.getResources(BOOT3_AUTO_CONFIGURATION_IMPORTS);
    while (resources.hasMoreElements()) {
      URL resource = resources.nextElement();
      try (BufferedReader reader = new BufferedReader(
          new InputStreamReader(resource.openStream(), StandardCharsets.UTF_8))) {
        String line;
        while ((line = reader.readLine()) != null) {
          String trimmed = line.trim();
          if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
            importNames.add(trimmed);
          }
        }
      }
    }
    return importNames;
  }
}
