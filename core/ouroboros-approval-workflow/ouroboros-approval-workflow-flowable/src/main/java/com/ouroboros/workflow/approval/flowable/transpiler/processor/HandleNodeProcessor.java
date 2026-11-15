package com.ouroboros.workflow.approval.flowable.transpiler.processor;

import static com.ouroboros.workflow.approval.flowable.util.BpmnUtil.buildFlowableListener;
import static com.ouroboros.workflow.approval.flowable.util.BpmnUtil.mapFormProperties;
import static com.ouroboros.workflow.approval.flowable.util.BpmnUtil.saveCandidates;
import static com.ouroboros.workflow.approval.flowable.util.BpmnUtil.saveTaskKind;

import java.util.ArrayList;

import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.FlowableListener;
import org.flowable.bpmn.model.Process;
import org.flowable.bpmn.model.UserTask;
import org.flowable.task.service.delegate.TaskListener;

import com.ouroboros.workflow.approval.definition.FlowNode;
import com.ouroboros.workflow.approval.definition.nodes.HandleNode;
import com.ouroboros.workflow.approval.flowable.listener.CreateSingleApprovalTaskListener;
import com.ouroboros.workflow.approval.flowable.transpiler.Constants;
import com.ouroboros.workflow.approval.transpiler.TranspileContext;
import com.ouroboros.workflow.approval.util.TreeGraphNode;

public class HandleNodeProcessor extends FlowableAbstractProcessor {
  @Override
  Runnable doProcess(TranspileContext<BpmnModel> context, Process process, TreeGraphNode node) {
    var flowNode = (HandleNode) node.getFlowNode();
    var formKey = context.getStructuredFlowDefinition().getFormKey();

    var handleTask = new UserTask();
    var taskListeners = new ArrayList<FlowableListener>();

    handleTask.setId(flowNode.getId());
    handleTask.setName(flowNode.getName());
    handleTask.setFormKey(formKey);
    handleTask.setTaskListeners(taskListeners);
    handleTask.setFormProperties(mapFormProperties(flowNode.getPermission()));
    saveCandidates(handleTask, flowNode.getCandidates());
    saveTaskKind(handleTask, Constants.Value.TASK_KIND_HANDLE);

    taskListeners.add(buildFlowableListener(TaskListener.EVENTNAME_CREATE, CreateSingleApprovalTaskListener.BEAN_NAME));

    process.addFlowElement(handleTask);

    context.setEntryPoint(flowNode.getId(), handleTask.getId());
    context.setExitPoint(flowNode.getId(), handleTask.getId());
    return null;
  }

  @Override
  public boolean supportNode(FlowNode flowNode) {
    return flowNode instanceof HandleNode;
  }
}
