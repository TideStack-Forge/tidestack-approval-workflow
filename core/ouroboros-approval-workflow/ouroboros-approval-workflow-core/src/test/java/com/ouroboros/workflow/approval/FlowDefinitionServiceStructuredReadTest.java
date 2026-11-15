package com.ouroboros.workflow.approval;

import java.util.List;

import io.vavr.control.Try;
import org.junit.jupiter.api.Test;

import com.ouroboros.workflow.approval.definition.FlowDefinition;
import com.ouroboros.workflow.approval.definition.nodes.HandleNode;
import com.ouroboros.workflow.approval.definition.nodes.StartNode;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FlowDefinitionServiceStructuredReadTest {
  @Test
  void legacyImplementationsExposeTheCanonicalGraphAndNotificationWithoutLosingNodeSwitches() {
    var service = mock(FlowDefinitionService.class, CALLS_REAL_METHODS);
    var definition = new FlowDefinition(); definition.setId("approval"); definition.setName("Approval");
    var start = new StartNode(); start.setId("start"); start.setNext("review");
    var review = new HandleNode(); review.setId("review"); review.setNotify(false);
    review.setCandidates(List.of(new com.ouroboros.workflow.approval.definition.candidates.StarterCandidate()));
    var end = new com.ouroboros.workflow.approval.definition.nodes.EndNode(); end.setId("end"); review.setNext("end");
    definition.setNodes(List.of(start, review, end));
    var notification = new FlowDefinition.Notification(); notification.setTaskCreatedTemplate("task-template"); definition.setNotification(notification);
    when(service.getFlowDefinition("approval")).thenReturn(Try.success(definition));
    var graph = service.getStructuredFlowDefinition("approval").get();
    assertNotNull(graph.getNode("review")); assertFalse(graph.getNode("review").isNotifyEnabled());
    assertEquals("task-template", graph.getNotification().getTaskCreatedTemplate());
    assertEquals("review", graph.getOutgoingEdges("start").getFirst().getTargetId());
  }

  @Test
  void unavailableLegacyDefinitionsRemainFailures() {
    var service = mock(FlowDefinitionService.class, CALLS_REAL_METHODS);
    var failure = new com.ouroboros.exception.BusinessException("definition unavailable");
    when(service.getFlowDefinition("approval")).thenReturn(Try.failure(failure));
    assertSame(failure, service.getStructuredFlowDefinition("approval").getCause());
  }
}
