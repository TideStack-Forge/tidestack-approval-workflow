package com.ouroboros.workflow.approval.flowable.transpiler;

import static com.ouroboros.workflow.approval.flowable.util.BpmnUtil.buildFlowableListener;
import static com.ouroboros.workflow.approval.flowable.util.BpmnUtil.buildSequenceFlow;
import static com.ouroboros.workflow.approval.flowable.util.BpmnUtil.saveFlowDefinitionRawJson;
import static com.ouroboros.workflow.approval.flowable.util.BpmnUtil.useDelegateBean;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.EndEvent;
import org.flowable.bpmn.model.ExclusiveGateway;
import org.flowable.bpmn.model.FlowableListener;
import org.flowable.bpmn.model.Gateway;
import org.flowable.bpmn.model.InclusiveGateway;
import org.flowable.bpmn.model.ParallelGateway;
import org.flowable.bpmn.model.Process;
import org.flowable.bpmn.model.ServiceTask;
import org.flowable.bpmn.model.TerminateEventDefinition;
import org.flowable.engine.delegate.ExecutionListener;

import com.ouroboros.util.Json;
import com.ouroboros.util.SPI;
import com.ouroboros.workflow.approval.definition.codec.CompositeFlowDefinitionCodec;
import com.ouroboros.workflow.approval.definition.FlowNode;
import com.ouroboros.workflow.approval.definition.nodes.EndNode;
import com.ouroboros.workflow.approval.definition.condition.Condition;
import com.ouroboros.workflow.approval.definition.graph.StructuredFlowDefinition;
import com.ouroboros.workflow.approval.definition.v2.FlowEdgeV2;
import com.ouroboros.workflow.approval.flowable.delegate.FailServiceTask;
import com.ouroboros.workflow.approval.flowable.listener.FlowEndExecutionListener;
import com.ouroboros.workflow.approval.semantics.SplitType;
import com.ouroboros.workflow.approval.semantics.StableSplitSemantics;
import com.ouroboros.workflow.approval.transpiler.AbstractApprovalWorkflowTranspiler;
import com.ouroboros.workflow.approval.transpiler.ApprovalWorkflowTranspilerBuilder;
import com.ouroboros.workflow.approval.transpiler.FlowNodeTranspileProcessor;
import com.ouroboros.workflow.approval.transpiler.TranspileContext;

public class FlowableWorkflowTranspiler extends AbstractApprovalWorkflowTranspiler<BpmnModel> {
  private static final CompositeFlowDefinitionCodec FLOW_DEFINITION_CODEC = new CompositeFlowDefinitionCodec();

  @Override
  protected List<FlowNodeTranspileProcessor<BpmnModel>> getTranspileProcessors() {
    BpmnModel bpmnModel = new BpmnModel();
    return (List) SPI.getServiceStream(FlowNodeTranspileProcessor.class)
        .filter(processor -> processor.supportModel(bpmnModel))
        .collect(Collectors.toList());
  }

  @Override
  protected TranspileContext<BpmnModel> initContext(StructuredFlowDefinition flowDefinition) {
    BpmnModel model = new BpmnModel();
    Process process = new Process();
    TranspileContext<BpmnModel> context = new TranspileContext<BpmnModel>(flowDefinition, model);

    ServiceTask failServiceTask = new ServiceTask();
    ArrayList<FlowableListener> failServiceTaskListeners = new ArrayList<FlowableListener>();
    EndEvent endEvent = buildEndEvent(Constants.SpecialNode.END_EVENT_ID, Constants.SpecialNode.END_EVENT_NAME, false);
    EndEvent failEndEvent = buildEndEvent(
        Constants.SpecialNode.FAIL_END_EVENT_ID,
        Constants.SpecialNode.FAIL_END_EVENT_NAME,
        true,
        false);

    model.setTargetNamespace(Constants.OUROBOROS_NAMESPACE + "/" + flowDefinition.getCategory());
    model.addProcess(process);

    process.setId(flowDefinition.getId());
    process.setName(flowDefinition.getName());
    process.setDocumentation(flowDefinition.getDescription());
    saveFlowDefinitionRawJson(process, toCanonicalFlowDefinitionJson(context.getStructuredFlowDefinition()));

    failServiceTask.setId(Constants.SpecialNode.FAIL_NODE_ID);
    failServiceTask.setName(Constants.SpecialNode.FAIL_NODE_NAME);
    useDelegateBean(failServiceTask, FailServiceTask.BEAN_NAME);
    failServiceTask.setExecutionListeners(failServiceTaskListeners);
    failServiceTaskListeners.add(buildFlowableListener(ExecutionListener.EVENTNAME_END, FlowEndExecutionListener.BEAN_NAME));

    process.addFlowElement(failServiceTask);
    process.addFlowElement(endEvent);
    process.addFlowElement(failEndEvent);
    process.addFlowElement(buildSequenceFlow(failServiceTask, failEndEvent));

    return context;
  }

  @Override
  protected void postTranspile(TranspileContext<BpmnModel> context) {
    BpmnModel model = context.getModel();
    Process process = model.getMainProcess();
    connectStructuredEdges(context, process);
    connectTerminalNodes(context, process);
  }

  private String toCanonicalFlowDefinitionJson(StructuredFlowDefinition flowDefinition) {
    return FLOW_DEFINITION_CODEC.encodeV2(flowDefinition)
        .flatMap(Json::tryToJsonString)
        .get();
  }

  private void connectStructuredEdges(TranspileContext<BpmnModel> context, Process process) {
    Map<String, List<FlowEdgeV2>> outgoing = new LinkedHashMap<String, List<FlowEdgeV2>>();
    for (FlowEdgeV2 edge : context.getStructuredFlowDefinition().getEdges()) {
      outgoing.computeIfAbsent(edge.getSourceId(), key -> new ArrayList<FlowEdgeV2>()).add(edge);
    }

    for (com.ouroboros.workflow.approval.definition.FlowNode node : context.getStructuredFlowDefinition().getNodes()) {
      String sourceId = node.getId();
      String sourceExit = context.getExitPoint(sourceId);
      if (sourceExit == null) {
        continue;
      }
      List<FlowEdgeV2> outgoingEdges = outgoing.getOrDefault(sourceId, java.util.Collections.<FlowEdgeV2>emptyList());
      if (outgoingEdges.isEmpty()) {
        continue;
      }

      if (outgoingEdges.size() == 1) {
        FlowEdgeV2 edge = outgoingEdges.get(0);
        String targetEntry = context.getEntryPoint(edge.getTargetId());
        if (targetEntry != null) {
          process.addFlowElement(buildFlow(sourceExit, targetEntry, edge));
        }
        continue;
      }

      Gateway gateway = inferGatewayType(context, outgoingEdges, sourceId, node.getName());
      process.addFlowElement(gateway);
      process.addFlowElement(buildSequenceFlow(sourceExit, gateway.getId()));

      for (FlowEdgeV2 edge : outgoingEdges) {
        String targetEntry = context.getEntryPoint(edge.getTargetId());
        if (targetEntry != null) {
          process.addFlowElement(buildFlow(gateway.getId(), targetEntry, edge));
        }
      }
    }
  }

  private Gateway inferGatewayType(TranspileContext<BpmnModel> context, List<FlowEdgeV2> outgoingEdges, String sourceId, String nodeName) {
    Gateway gateway;
    SplitType gatewayType = StableSplitSemantics.inferSplitType(context.getStructuredFlowDefinition(), outgoingEdges);

    if (gatewayType == SplitType.AND) {
      gateway = new ParallelGateway();
    } else if (gatewayType == SplitType.XOR) {
      gateway = new ExclusiveGateway();
    } else {
      gateway = new InclusiveGateway();
    }

    gateway.setId(sourceId + "__" + gatewayType.toString().toLowerCase() + "Split");
    gateway.setName(nodeName + " " + gatewayType + " Split");
    return gateway;
  }

  private org.flowable.bpmn.model.SequenceFlow buildFlow(String source, String target, FlowEdgeV2 edge) {
    Condition cond = edge.getCondition();
    String conditionExpr = cond == null ? null : cond.toExpression();
    if (conditionExpr == null || conditionExpr.trim().isEmpty()) {
      return buildSequenceFlow(source, target);
    }
    return buildSequenceFlow(source, target, conditionExpr);
  }

  private void connectTerminalNodes(TranspileContext<BpmnModel> context, Process process) {
    for (com.ouroboros.workflow.approval.definition.FlowNode node : context.getStructuredFlowDefinition().getNodes()) {
      if (node instanceof EndNode) {
        continue;
      }
      if (!context.getStructuredFlowDefinition().getOutgoingEdges(node.getId()).isEmpty()) {
        continue;
      }
      String exitPoint = context.getExitPoint(node.getId());
      if (exitPoint != null && !Constants.SpecialNode.END_EVENT_ID.equals(exitPoint)) {
        process.addFlowElement(buildSequenceFlow(exitPoint, Constants.SpecialNode.END_EVENT_ID));
      }
    }
  }

  private EndEvent buildEndEvent(String id, String name, boolean terminate) {
    return buildEndEvent(id, name, terminate, true);
  }

  private EndEvent buildEndEvent(String id, String name, boolean terminate, boolean withEndListener) {
    EndEvent endEvent = new EndEvent();

    endEvent.setId(id);
    endEvent.setName(name);
    if (withEndListener) {
      ArrayList<FlowableListener> endEventListeners = new ArrayList<FlowableListener>();
      endEvent.setExecutionListeners(endEventListeners);
      endEventListeners.add(buildFlowableListener(ExecutionListener.EVENTNAME_END, FlowEndExecutionListener.BEAN_NAME));
    }
    if (terminate) {
      endEvent.addEventDefinition(new TerminateEventDefinition());
    }
    return endEvent;
  }

  public static class Builder implements ApprovalWorkflowTranspilerBuilder<BpmnModel> {
    @Override
    public FlowableWorkflowTranspiler build() {
      return new FlowableWorkflowTranspiler();
    }
  }
}
