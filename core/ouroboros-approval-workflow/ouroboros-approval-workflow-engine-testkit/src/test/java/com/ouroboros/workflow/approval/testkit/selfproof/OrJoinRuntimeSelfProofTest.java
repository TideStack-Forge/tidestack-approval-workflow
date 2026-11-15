package com.ouroboros.workflow.approval.testkit.selfproof;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ouroboros.workflow.approval.testkit.adapter.EngineCapability;
import com.ouroboros.workflow.approval.testkit.adapter.EngineTestAdapter;
import com.ouroboros.workflow.approval.testkit.contract.AbstractEngineContractTest;
import com.ouroboros.workflow.approval.testkit.snapshot.RuntimeInstance;
import com.ouroboros.workflow.approval.testkit.snapshot.StartRequest;
import com.ouroboros.workflow.approval.testkit.snapshot.TaskView;
import com.ouroboros.workflow.approval.testkit.snapshot.TimelineView;

public class OrJoinRuntimeSelfProofTest extends AbstractEngineContractTest {
  private static final String FLOW_KEY = "or-join-test-flow";

  private final SelfProofEngineTestAdapter selfProofAdapter = new SelfProofEngineTestAdapter();

  @Override
  protected EngineTestAdapter adapter() {
    return selfProofAdapter;
  }

  @Override
  protected EngineCapability capabilities() {
    return EngineCapability.allEnabled();
  }

  @Test
  public void orJoinCompletesAfterActivatedBranchesFinish() {
    deployOrJoinFlow();

    RuntimeInstance instance = startOrJoinInstanceWithAmount(2000);

    TimelineView timeline = adapter().timeline(instance.getInstanceId());
    assertEquals(2, countApprovalNodes(timeline), "amount=2000 时只应激活命中的条件分支和默认分支");

    completeActiveTask(instance.getInstanceId(), "approve-b");
    completeActiveTask(instance.getInstanceId(), "approve-c");

    assertTrue(adapter().isFinished(instance.getInstanceId()));
    assertTrue(adapter().activeTasks(instance.getInstanceId()).isEmpty());
    assertEquals("APPROVED", lastInstanceState(instance.getInstanceId()));
  }

  private void deployOrJoinFlow() {
    adapter().deploy(FLOW_KEY, orJoinFlow());
  }

  private RuntimeInstance startOrJoinInstanceWithAmount(int amount) {
    RuntimeInstance instance = adapter().start(FLOW_KEY, startRequestWithAmount(amount));
    adapter().completeStartTask(instance.getInstanceId());
    return instance;
  }

  private StartRequest startRequestWithAmount(int amount) {
    Map<String, List<String>> selectedAssignees = new LinkedHashMap<String, List<String>>();
    selectedAssignees.put("approve-a", Collections.singletonList("user1"));
    selectedAssignees.put("approve-b", Collections.singletonList("user2"));
    selectedAssignees.put("approve-c", Collections.singletonList("user3"));

    Map<String, Object> variables = new LinkedHashMap<String, Object>();
    variables.put("amount", amount);

    return new StartRequest("test-user",
        selectedAssignees,
        variables,
        Collections.<String, Object>emptyMap());
  }

  private long countApprovalNodes(TimelineView timeline) {
    return timeline.getNodes().stream()
        .filter(node -> node.getNodeId().startsWith("approve-"))
        .count();
  }

  private void completeActiveTask(String instanceId, String nodeId) {
    TaskView task = onlyTask(instanceId, nodeId);
    adapter().doAction(task.getTaskId(), "approve", task.getAssignee(), null,
        Collections.<String, Object>emptyMap(), null);
  }

  private String lastInstanceState(String instanceId) {
    return adapter().instanceEvents(instanceId)
        .get(adapter().instanceEvents(instanceId).size() - 1)
        .getState();
  }

  private String orJoinFlow() {
    return String.join("\n",
        "{",
        "  \"formatVersion\": 2,",
        "  \"id\": \"flow-or-join\",",
        "  \"name\": \"OR合流测试\",",
        "  \"nodes\": [",
        "    { \"type\": \"发起节点\", \"id\": \"start-a\" },",
        "    { \"type\": \"审批节点\", \"id\": \"approve-a\", \"subType\": \"单签\", \"candidates\": [] },",
        "    { \"type\": \"审批节点\", \"id\": \"approve-b\", \"subType\": \"单签\", \"candidates\": [] },",
        "    { \"type\": \"审批节点\", \"id\": \"approve-c\", \"subType\": \"单签\", \"candidates\": [] },",
        "    { \"type\": \"JoinNode\", \"id\": \"join-a\", \"joinType\": \"OR\" },",
        "    { \"type\": \"结束节点\", \"id\": \"end-a\" }",
        "  ],",
        "  \"edges\": [",
        "    { \"type\": \"SEQUENCE\", \"sourceId\": \"start-a\", \"targetId\": \"approve-a\", \"condition\": { \"left\": { \"type\": \"field\", \"field\": \"amount\" }, \"op\": \"greater\", \"right\": 5000 } },",
        "    { \"type\": \"SEQUENCE\", \"sourceId\": \"start-a\", \"targetId\": \"approve-b\", \"condition\": { \"left\": { \"type\": \"field\", \"field\": \"amount\" }, \"op\": \"greater\", \"right\": 1000 } },",
        "    { \"type\": \"SEQUENCE\", \"sourceId\": \"start-a\", \"targetId\": \"approve-c\" },",
        "    { \"type\": \"SEQUENCE\", \"sourceId\": \"approve-a\", \"targetId\": \"join-a\" },",
        "    { \"type\": \"SEQUENCE\", \"sourceId\": \"approve-b\", \"targetId\": \"join-a\" },",
        "    { \"type\": \"SEQUENCE\", \"sourceId\": \"approve-c\", \"targetId\": \"join-a\" },",
        "    { \"type\": \"SEQUENCE\", \"sourceId\": \"join-a\", \"targetId\": \"end-a\" }",
        "  ]",
        "}");
  }
}
