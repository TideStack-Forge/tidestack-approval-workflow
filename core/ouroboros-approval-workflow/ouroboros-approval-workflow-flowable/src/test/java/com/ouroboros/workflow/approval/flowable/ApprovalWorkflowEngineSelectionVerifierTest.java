package com.ouroboros.workflow.approval.flowable;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.ouroboros.workflow.approval.ApprovalWorkflowErrorCodes;

/**
 * 引擎选择校验：{@code ouroboros.workflow.approval.engine} 取值非法时必须在启动期失败。
 *
 * <p>背景：{@code ApprovalWorkflowFlowableAutoConfiguration} 是唯一注册 FlowManageService /
 * FlowInstanceService / FlowTaskService 的地方，且受 {@code @ConditionalOnProperty(matchIfMissing = true)}
 * 约束。属性拼错（如 {@code activiti}、{@code camunda}）会让条件不成立、引擎 Bean 全部缺失，
 * 而启动过程本身不报错——故障被推迟到运行期以 null 依赖的形式暴露。
 */
class ApprovalWorkflowEngineSelectionVerifierTest {

  private static final String BOOT3_AUTO_CONFIGURATION_IMPORTS =
      "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports";

  private final ApplicationContextRunner runner = new ApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(ApprovalWorkflowEngineSelectionVerifier.class));

  @Test
  void unsupportedEngineValueFailsAtStartup() {
    runner.withPropertyValues("ouroboros.workflow.approval.engine=activiti")
        .run(context -> {
          assertThat(context.getStartupFailure()).isNotNull();
          assertThat(rootMessages(context.getStartupFailure()))
              .anyMatch(message -> message.contains(ApprovalWorkflowErrorCodes.WORKFLOW_ENGINE_NOT_FOUND));
        });
  }

  @Test
  void misspelledEngineValueFailsAtStartup() {
    runner.withPropertyValues("ouroboros.workflow.approval.engine=Flowable")
        .run(context -> assertThat(context.getStartupFailure()).isNotNull());
  }

  @Test
  void flowableEngineValueIsAccepted() {
    runner.withPropertyValues("ouroboros.workflow.approval.engine=flowable")
        .run(context -> assertThat(context.getStartupFailure()).isNull());
  }

  @Test
  void missingEnginePropertyFallsBackToFlowable() {
    runner.run(context -> assertThat(context.getStartupFailure()).isNull());
  }

  @Test
  void verifierParticipatesInAutoConfigurationRegardlessOfEngineProperty() throws IOException {
    List<String> importNames = loadBoot3AutoConfigurationImports();

    assertThat(importNames).contains(ApprovalWorkflowEngineSelectionVerifier.class.getName());
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
