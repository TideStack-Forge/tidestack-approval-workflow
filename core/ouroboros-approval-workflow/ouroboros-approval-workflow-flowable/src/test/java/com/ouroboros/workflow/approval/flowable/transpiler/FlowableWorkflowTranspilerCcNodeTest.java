package com.ouroboros.workflow.approval.flowable.transpiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.ServiceTask;
import org.junit.jupiter.api.Test;

import com.ouroboros.workflow.approval.definition.codec.CompositeFlowDefinitionCodec;
import com.ouroboros.workflow.approval.flowable.transpiler.Constants;

class FlowableWorkflowTranspilerCcNodeTest {
  private final CompositeFlowDefinitionCodec codec = new CompositeFlowDefinitionCodec();

  @Test
  void transpileCcNodeCreatesServiceTaskWithCcDelegate() {
    BpmnModel model = new FlowableWorkflowTranspiler().transpile(codec.decode(ccFlowJson()).get());
    ServiceTask serviceTask = (ServiceTask) model.getMainProcess().getFlowElement("cc-a" + Constants.SpecialNode.APPROVED_ID_SUFFIX);

    assertNotNull(serviceTask);
    assertEquals("${ccServiceTask}", serviceTask.getImplementation());
  }

  private String ccFlowJson() {
    return """
        {
          "formatVersion": 2,
          "id": "flow-cc",
          "name": "抄送流程",
          "nodes": [
            { "type": "发起节点", "name": "发起节点", "id": "start-a", "permission": [] },
            { "type": "CcNode", "name": "抄送节点", "id": "cc-a", "candidates": [{ "type": "User", "users": ["u-cc-1"] }] },
            { "type": "EndNode", "name": "结束", "id": "end-a" }
          ],
          "edges": [
            { "id": "e1", "type": "SEQUENCE", "sourceId": "start-a", "targetId": "cc-a" },
            { "id": "e2", "type": "SEQUENCE", "sourceId": "cc-a", "targetId": "end-a" }
          ]
        }
        """;
  }
}
