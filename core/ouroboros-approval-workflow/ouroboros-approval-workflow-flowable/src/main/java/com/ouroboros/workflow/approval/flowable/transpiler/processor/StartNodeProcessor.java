package com.ouroboros.workflow.approval.flowable.transpiler.processor;

import static com.ouroboros.workflow.approval.flowable.util.BpmnUtil.buildFlowableListener;
import static com.ouroboros.workflow.approval.flowable.util.BpmnUtil.buildSequenceFlow;
import static com.ouroboros.workflow.approval.flowable.util.BpmnUtil.mapFormProperties;
import static com.ouroboros.workflow.approval.flowable.util.BpmnUtil.useDelegateBean;

import java.util.ArrayList;

import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.ExclusiveGateway;
import org.flowable.bpmn.model.FlowableListener;
import org.flowable.bpmn.model.InclusiveGateway;
import org.flowable.bpmn.model.Process;
import org.flowable.bpmn.model.ServiceTask;
import org.flowable.bpmn.model.StartEvent;
import org.flowable.bpmn.model.UserTask;
import org.flowable.engine.delegate.ExecutionListener;

import com.ouroboros.workflow.approval.definition.FlowNode;
import com.ouroboros.workflow.approval.definition.nodes.StartNode;
import com.ouroboros.workflow.approval.flowable.delegate.InitializeFlowServiceTask;
import com.ouroboros.workflow.approval.flowable.listener.FlowStartExecutionListener;
import com.ouroboros.workflow.approval.flowable.transpiler.Constants;
import com.ouroboros.workflow.approval.transpiler.TranspileContext;
import com.ouroboros.workflow.approval.util.TreeGraphNode;

public class StartNodeProcessor extends FlowableAbstractProcessor {
  @Override
  Runnable doProcess(TranspileContext<BpmnModel> context, Process process, TreeGraphNode node) {
    var flowNode = node.getFlowNode();
    var formKey = context.getStructuredFlowDefinition().getFormKey();

    var startEvent = new StartEvent();
    var initializeFlowServiceTask = new ServiceTask();
    var initialForkGateway = new ExclusiveGateway();
    var initialJoinGateway = new InclusiveGateway();
    var startUserTask = new UserTask();
    var executionListeners = new ArrayList<FlowableListener>();

    startEvent.setId(Constants.SpecialNode.START_EVENT_ID);
    startEvent.setName(Constants.SpecialNode.START_EVENT_NAME);
    startEvent.setExecutionListeners(executionListeners);
    executionListeners.add(buildFlowableListener(ExecutionListener.EVENTNAME_START, FlowStartExecutionListener.BEAN_NAME));

    initializeFlowServiceTask.setId(Constants.SpecialNode.INITIALIZE_FLOW_ID);
    initializeFlowServiceTask.setName(Constants.SpecialNode.INITIALIZE_FLOW_NAME);
    useDelegateBean(initializeFlowServiceTask, InitializeFlowServiceTask.BEAN_NAME);

    initialForkGateway.setId(Constants.SpecialNode.INITIAL_FORK_GATEWAY_ID);
    initialForkGateway.setName(Constants.SpecialNode.INITIAL_FORK_GATEWAY_NAME);

    startUserTask.setId(flowNode.getId());
    startUserTask.setName(flowNode.getName());
    startUserTask.setFormKey(formKey);
    startUserTask.setFormProperties(mapFormProperties(flowNode.getPermission()));

    initialJoinGateway.setId(Constants.SpecialNode.INITIAL_JOIN_GATEWAY_ID);
    initialJoinGateway.setName(Constants.SpecialNode.INITIAL_JOIN_GATEWAY_NAME);

    process.addFlowElement(startEvent);
    process.addFlowElement(initializeFlowServiceTask);
    process.addFlowElement(initialForkGateway);
    process.addFlowElement(startUserTask);
    process.addFlowElement(initialJoinGateway);

    process.addFlowElement(buildSequenceFlow(startEvent, initializeFlowServiceTask));
    process.addFlowElement(buildSequenceFlow(initializeFlowServiceTask, initialForkGateway));
    process.addFlowElement(buildSequenceFlow(initialForkGateway, startUserTask, Constants.Variable.HAS_PARENT_FLOW + "==false"));
    process.addFlowElement(buildSequenceFlow(startUserTask, initialJoinGateway));
    process.addFlowElement(buildSequenceFlow(initialForkGateway, initialJoinGateway, Constants.Variable.HAS_PARENT_FLOW + "==true"));

    context.setEntryPoint(flowNode.getId(), startUserTask.getId());
    context.setExitPoint(flowNode.getId(), initialJoinGateway.getId());
    return null;
  }

  @Override
  public boolean supportNode(FlowNode flowNode) {
    return flowNode instanceof StartNode;
  }
}
