package com.ouroboros.workflow.approval.testkit.contract;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;

import com.ouroboros.util.Json;
import com.ouroboros.workflow.approval.definition.FlowNode;
import com.ouroboros.workflow.approval.definition.codec.CompositeFlowDefinitionCodec;
import com.ouroboros.workflow.approval.definition.graph.StructuredFlowDefinition;
import com.ouroboros.workflow.approval.definition.nodes.ApprovalNode;
import com.ouroboros.workflow.approval.definition.nodes.GatewayNode;
import com.ouroboros.workflow.approval.definition.nodes.JoinNode;
import com.ouroboros.workflow.approval.definition.nodes.MultipleApprovalNode;
import com.ouroboros.workflow.approval.definition.nodes.SubflowNode;
import com.ouroboros.workflow.approval.definition.v2.FlowEdgeV2;
import com.ouroboros.workflow.approval.testkit.adapter.EngineCapability;
import com.ouroboros.workflow.approval.testkit.adapter.EngineTestAdapter;
import com.ouroboros.workflow.approval.testkit.fixture.SharedSemanticScenarioFixtures;
import com.ouroboros.workflow.approval.testkit.scenario.SharedSemanticScenario;
import com.ouroboros.workflow.approval.testkit.snapshot.RuntimeInstance;
import com.ouroboros.workflow.approval.testkit.snapshot.StartRequest;
import com.ouroboros.workflow.approval.testkit.snapshot.TaskView;
import com.ouroboros.workflow.approval.testkit.snapshot.TimelineView;
import com.ouroboros.workflow.approval.testkit.snapshot.TranspiledModelSnapshot;

public abstract class AbstractEngineContractTest {
  private final SharedSemanticScenarioFixtures fixtures = new SharedSemanticScenarioFixtures();
  private final CompositeFlowDefinitionCodec codec = new CompositeFlowDefinitionCodec();

  protected abstract EngineTestAdapter adapter();

  protected abstract EngineCapability capabilities();

  protected SharedSemanticScenarioFixtures fixtures() {
    return fixtures;
  }

  protected String json(SharedSemanticScenario scenario) {
    return fixtures().json(scenario);
  }

  protected StartRequest startRequest(SharedSemanticScenario scenario) {
    return fixtures().startRequest(scenario);
  }

  protected TranspiledModelSnapshot expectedSnapshot(SharedSemanticScenario scenario) {
    return toSnapshot(structuredDefinition(scenario));
  }

  protected StructuredFlowDefinition structuredDefinition(SharedSemanticScenario scenario) {
    return codec.decode(json(scenario)).get();
  }

  protected StructuredFlowDefinition structuredDefinition(String json) {
    return codec.decode(json).get();
  }

  protected Map<String, Object> normalizedJsonObject(String json) {
    return Json.toMap(json);
  }

  protected Map<String, Object> normalizedDefinitionObject(SharedSemanticScenario scenario) {
    return Json.toMap(Json.toJsonString(codec.encodeV2(structuredDefinition(scenario)).get()));
  }

  protected List<String> flowNodeIds(List<FlowNode> nodes) {
    ArrayList<String> result = new ArrayList<String>();
    for (FlowNode node : nodes) {
      result.add(node.getId());
    }
    return result;
  }

  protected List<String> approvalNodeIds(List<ApprovalNode> nodes) {
    ArrayList<String> result = new ArrayList<String>();
    for (ApprovalNode node : nodes) {
      result.add(node.getId());
    }
    return result;
  }

  protected List<String> taskAssignees(List<TaskView> tasks) {
    ArrayList<String> assignees = new ArrayList<String>();
    for (TaskView task : tasks) {
      if (task.getAssignee() != null) {
        assignees.add(task.getAssignee());
      }
    }
    assignees.sort(String::compareTo);
    return assignees;
  }

  protected RuntimeInstance deployAndStart(SharedSemanticScenario scenario) {
    return deployAndStart(json(scenario), startRequest(scenario));
  }

  protected RuntimeInstance deployAndStart(SharedSemanticScenario scenario, StartRequest request) {
    return deployAndStart(json(scenario), request);
  }

  protected RuntimeInstance deployAndStart(String json, StartRequest request) {
    String flowKey = structuredDefinition(json).getId();
    adapter().deploy(flowKey, json);
    RuntimeInstance instance = adapter().start(flowKey, request);
    adapter().completeStartTask(instance.getInstanceId());
    return instance;
  }

  protected TaskView onlyTask(String instanceId, String nodeId) {
    List<TaskView> tasks = adapter().activeTasks(instanceId, nodeId);
    if (tasks.size() != 1) {
      throw new AssertionError("Expected exactly one active task for node " + nodeId + " but found " + tasks.size());
    }
    return tasks.get(0);
  }

  protected TimelineView.NodeTimelineView timelineNode(TimelineView timeline, String nodeId) {
    for (TimelineView.NodeTimelineView node : timeline.getNodes()) {
      if (nodeId.equals(node.getNodeId())) {
        return node;
      }
    }
    throw new AssertionError("No timeline node found for " + nodeId);
  }

  protected TranspiledModelSnapshot toSnapshot(StructuredFlowDefinition definition) {
    ArrayList<TranspiledModelSnapshot.NodeSnapshot> nodes = new ArrayList<TranspiledModelSnapshot.NodeSnapshot>();
    for (FlowNode node : definition.getNodes()) {
      nodes.add(new TranspiledModelSnapshot.NodeSnapshot(
          node.getId(),
          node.getType(),
          node.getName(),
          node instanceof ApprovalNode ? ((ApprovalNode) node).getSubType() : null,
          nodeProperties(node)
      ));
    }

    ArrayList<TranspiledModelSnapshot.EdgeSnapshot> edges = new ArrayList<TranspiledModelSnapshot.EdgeSnapshot>();
    for (FlowEdgeV2 edge : definition.getEdges()) {
      edges.add(new TranspiledModelSnapshot.EdgeSnapshot(
          edge.getId(),
          edge.getType() == null ? null : edge.getType().name(),
          edge.getSourceId(),
          edge.getTargetId(),
          edge.getName(),
          edge.getCondition() == null ? null : edge.getCondition().toExpression()
      ));
    }

    return new TranspiledModelSnapshot(definition.getId(), definition.getName(), nodes, edges);
  }

  @AfterEach
  void resetAdapter() {
    adapter().reset();
  }

  private Map<String, Object> nodeProperties(FlowNode node) {
    LinkedHashMap<String, Object> properties = new LinkedHashMap<String, Object>();

    if (node instanceof ApprovalNode) {
      ApprovalNode approvalNode = (ApprovalNode) node;
      if (approvalNode.getActions() != null) {
        properties.put("actions", approvalNode.getActions());
      }
      if (approvalNode.getNoCandidateMode() != null) {
        properties.put("noCandidateMode", approvalNode.getNoCandidateMode().toString());
      }
      if (approvalNode.getReAssignUsers() != null) {
        properties.put("reAssignUsers", approvalNode.getReAssignUsers());
      }
    }
    if (node instanceof MultipleApprovalNode) {
      MultipleApprovalNode multipleApprovalNode = (MultipleApprovalNode) node;
      properties.put("multipleMode", multipleApprovalNode.getMultipleMode());
      properties.put("percentage", multipleApprovalNode.getPercentage());
      properties.put("rejectAction", multipleApprovalNode.getRejectAction());
    }
    if (node instanceof GatewayNode) {
      properties.put("splitType", ((GatewayNode) node).getSplitType());
    }
    if (node instanceof JoinNode) {
      properties.put("joinType", ((JoinNode) node).getJoinType());
    }
    if (node instanceof SubflowNode) {
      SubflowNode subflowNode = (SubflowNode) node;
      properties.put("subFlowKey", subflowNode.getSubFlowKey());
      properties.put("rejectAction", subflowNode.getRejectAction());
      properties.put("inParameters", subflowNode.getInParameters().size());
      properties.put("outParameters", subflowNode.getOutParameters().size());
    }
    return properties;
  }
}
