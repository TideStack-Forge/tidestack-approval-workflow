package com.ouroboros.workflow.approval;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import com.ouroboros.workflow.approval.event.InstanceEventDispatcher;
import com.ouroboros.workflow.approval.event.TaskEventDispatcher;

@Configuration
@Import({InstanceEventDispatcher.class, TaskEventDispatcher.class})
public class ApprovalWorkflowAutoConfiguration {
  private static final Logger LOGGER = LoggerFactory.getLogger(ApprovalWorkflowAutoConfiguration.class);

  @PostConstruct
  public void init() {
    LOGGER.info("Approval Workflow Auto Configuration loaded");
  }
}
