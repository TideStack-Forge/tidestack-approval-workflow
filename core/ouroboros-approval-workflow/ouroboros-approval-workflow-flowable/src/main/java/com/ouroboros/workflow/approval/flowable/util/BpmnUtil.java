package com.ouroboros.workflow.approval.flowable.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.flowable.bpmn.model.BaseElement;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.CustomProperty;
import org.flowable.bpmn.model.ExtensionElement;
import org.flowable.bpmn.model.FieldExtension;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.FlowNode;
import org.flowable.bpmn.model.FlowableListener;
import org.flowable.bpmn.model.FormProperty;
import org.flowable.bpmn.model.ImplementationType;
import org.flowable.bpmn.model.IOParameter;
import org.flowable.bpmn.model.Process;
import org.flowable.bpmn.model.SequenceFlow;
import org.flowable.bpmn.model.ServiceTask;
import org.flowable.bpmn.model.Task;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.task.service.delegate.DelegateTask;

import com.ouroboros.exception.PlatformException;
import com.ouroboros.workflow.approval.ApprovalWorkflowErrorCodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vavr.control.Try;

import com.ouroboros.exception.InternalException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.ouroboros.util.Json;
import com.ouroboros.util.SPI;
import com.ouroboros.workflow.approval.CandidateUserResolver;
import com.ouroboros.workflow.approval.definition.Candidate;
import com.ouroboros.workflow.approval.definition.FlowDefinition;
import com.ouroboros.workflow.approval.definition.IOParameterSetting;
import com.ouroboros.workflow.approval.definition.NodeFormFieldPermission;
import com.ouroboros.workflow.approval.definition.codec.CompositeFlowDefinitionCodec;
import com.ouroboros.workflow.approval.definition.graph.StructuredFlowDefinition;
import com.ouroboros.workflow.approval.flowable.transpiler.Constants;

public class BpmnUtil {
  private static final Logger logger = LoggerFactory.getLogger(BpmnUtil.class);
  private static final List<CandidateUserResolver> CANDIDATE_USER_RESOLVERS = SPI
      .getCachedReversedServiceStream(CandidateUserResolver.class)
      .collect(Collectors.toList());
  private static final CompositeFlowDefinitionCodec FLOW_DEFINITION_CODEC = new CompositeFlowDefinitionCodec();

  public static SequenceFlow buildSequenceFlow(String source, String target) {
    if (StringUtils.isAnyBlank(source, target)) {
      throw new IllegalArgumentException("source, target can not be null");
    }

    SequenceFlow flow = new SequenceFlow();
    flow.setSourceRef(source);
    flow.setTargetRef(target);
    return flow;
  }

  public static SequenceFlow buildSequenceFlow(String source, String target, String condition) {
    if (StringUtils.isAnyBlank(source, target, condition)) {
      throw new IllegalArgumentException("source, target, condition can not be null");
    }

    SequenceFlow flow = new SequenceFlow();
    flow.setSourceRef(source);
    flow.setTargetRef(target);
    flow.setConditionExpression(conditionExpression(condition));
    return flow;
  }

  public static SequenceFlow buildSequenceFlow(FlowNode source, FlowNode target) {
    return buildSequenceFlow(source.getId(), target.getId());
  }

  public static SequenceFlow buildSequenceFlow(FlowNode source, FlowNode target, String condition) {
    return buildSequenceFlow(source.getId(), target.getId(), condition);
  }

  /**
   * 把条件渲染成引擎要求的承载形态 {@code ${expr}}。
   *
   * <p>裸表达式交给 {@code ExpressionManager.createExpression} 会被**原样当成字面量返回**，
   * 既不求值也不报错——所以引擎写入 BPMN 与运行时"按分支匹配节点"必须用同一形态，
   * 否则后者会静默地恒判为不匹配。
   */
  public static String conditionExpression(String condition) {
    return "${" + condition + "}";
  }

  public static String executionVariableExpression(String variableName) {
    if (StringUtils.isBlank(variableName)) {
      throw new IllegalArgumentException("variableName can not be null");
    }
    return "execution.getVariable('" + StringUtils.replace(variableName, "'", "\\'") + "')";
  }

  public static String variableEqualsExpression(String variableName, boolean expectedValue) {
    return executionVariableExpression(variableName) + "==" + expectedValue;
  }

  public static String variableEqualsExpression(String variableName, String expectedValue) {
    return executionVariableExpression(variableName)
        + "==\""
        + StringUtils.replace(StringUtils.defaultString(expectedValue), "\"", "\\\"")
        + "\"";
  }

  public static ExtensionElement buildLargeTextExtensionElement(String name, String value) {
    ExtensionElement extensionElement = new ExtensionElement();
    extensionElement.setName(name);
    extensionElement.setNamespace(Constants.OUROBOROS_NAMESPACE);
    extensionElement.setNamespacePrefix(Constants.OUROBOROS_NAMESPACE_PREFIX);
    extensionElement.setElementText(value);
    return extensionElement;
  }

  public static CustomProperty buildCustomProperty(String name, String value) {
    CustomProperty customProperty = new CustomProperty();
    customProperty.setName(name);
    customProperty.setSimpleValue(value);
    return customProperty;
  }

  public static FieldExtension buildFieldExtension(String name, String value) {
    FieldExtension fieldExtension = new FieldExtension();
    fieldExtension.setFieldName(name);
    fieldExtension.setStringValue(value);
    return fieldExtension;
  }

  public static void addExtensionElement(BaseElement element, String name, String value) {
    element.addExtensionElement(buildLargeTextExtensionElement(name, value));
  }

  public static List<String> getExtensionElementTexts(BaseElement element, String name) {
    List<ExtensionElement> extensionElements = element.getExtensionElements().get(name);
    if (extensionElements == null) {
      return Collections.emptyList();
    }
    return extensionElements.stream()
        .filter(extensionElement -> StringUtils.isNotEmpty(extensionElement.getNamespace())
            && extensionElement.getNamespace().startsWith(Constants.OUROBOROS_NAMESPACE))
        .map(ExtensionElement::getElementText)
        .collect(Collectors.toList());
  }

  public static FlowableListener buildFlowableListener(String event, String beanName) {
    FlowableListener listener = new FlowableListener();
    listener.setEvent(event);
    listener.setImplementationType(ImplementationType.IMPLEMENTATION_TYPE_DELEGATEEXPRESSION);
    listener.setImplementation("${" + beanName + "}");
    return listener;
  }

  public static void useDelegateBean(ServiceTask task, String beanName) {
    task.setImplementationType(ImplementationType.IMPLEMENTATION_TYPE_DELEGATEEXPRESSION);
    task.setImplementation("${" + beanName + "}");
  }

  public static List<FormProperty> mapFormProperties(List<NodeFormFieldPermission> permission) {
    if (permission == null) {
      return Collections.emptyList();
    }

    List<FormProperty> result = new ArrayList<FormProperty>();
    for (NodeFormFieldPermission p : permission) {
      FormProperty formProperty = new FormProperty();
      formProperty.setName(p.getLabel());
      formProperty.setId(p.getName());
      formProperty.setWriteable(p.isWritable());
      formProperty.setReadable(p.isVisible());
      formProperty.setRequired(p.isRequired());
      result.add(formProperty);
    }
    return result;
  }

  public static List<IOParameter> mapIOParameter(List<IOParameterSetting> ioParameterSettings) {
    if (ioParameterSettings == null) {
      return Collections.emptyList();
    }
    return ioParameterSettings.stream()
        .map(param -> {
          IOParameter ioParameter = new IOParameter();
          ioParameter.setSourceExpression(param.getExpression());
          ioParameter.setSource(param.getSource());
          ioParameter.setTarget(param.getTarget());
          return ioParameter;
        })
        .collect(Collectors.toList());
  }

  public static void saveCandidates(Task task, List<Candidate> candidates) {
    addExtensionElement(task, "candidates", candidates == null ? "[]" : Json.toJsonString(candidates));
  }

  public static List<Candidate> readCandidates(Task task) {
    String json = getExtensionElementTexts(task, "candidates").stream().findFirst().orElse(null);
    if (StringUtils.isBlank(json)) {
      return Collections.emptyList();
    }
    return Json.tryToList(new TypeReference<List<Candidate>>() {
        }, json)
        .toEither()
        .peekLeft(e -> logger.error("读取候选人信息失败", e))
        .getOrElseGet(e -> Collections.<Candidate>emptyList());
  }

  public static List<String> getCandidateUsers(RepositoryService repositoryService, DelegateTask task) {
    if (repositoryService == null || task == null || StringUtils.isAnyBlank(task.getProcessDefinitionId(), task.getTaskDefinitionKey())) {
      return Collections.emptyList();
    }

    BpmnModel bpmnModel = repositoryService.getBpmnModel(task.getProcessDefinitionId());
    if (bpmnModel == null) {
      return Collections.emptyList();
    }

    FlowElement flowElement = bpmnModel.getFlowElement(task.getTaskDefinitionKey());
    if (!(flowElement instanceof Task)) {
      return Collections.emptyList();
    }
    Task userTask = (Task) flowElement;

    return getCandidateUsers(readCandidates(userTask), task.getProcessInstanceId(), task.getId(), task.getName());
  }

  public static List<String> getCandidateUsers(
      List<Candidate> candidates,
      String processInstanceId,
      String taskInstanceId,
      String taskName
  ) {
    if (candidates == null) {
      return Collections.emptyList();
    }

    return candidates.stream()
        .flatMap(candidate -> CANDIDATE_USER_RESOLVERS.stream()
            .filter(resolver -> resolver.support(candidate))
            .findFirst()
            .map(resolver -> resolver.resolve(candidate, processInstanceId, taskInstanceId, taskName))
            .map(List::stream)
            .orElseGet(Stream::empty))
        .collect(Collectors.toList());
  }

  public static void saveActions(org.flowable.bpmn.model.Activity activity, List<String> actions) {
    addExtensionElement(activity, "actions", Json.toJsonString(actions));
  }

  public static void saveTaskKind(org.flowable.bpmn.model.Activity activity, String taskKind) {
    addExtensionElement(activity, "taskKind", taskKind);
  }

  public static String readTaskKind(org.flowable.bpmn.model.Activity activity) {
    return getExtensionElementTexts(activity, "taskKind").stream().findFirst().orElse(null);
  }

  public static void saveRejectAction(org.flowable.bpmn.model.Activity activity, String rejectAction) {
    addExtensionElement(activity, "rejectAction", rejectAction);
  }

  public static List<String> readActions(org.flowable.bpmn.model.Activity activity) {
    String actions = getExtensionElementTexts(activity, "actions").stream().findFirst().orElse(null);
    if (StringUtils.isBlank(actions)) {
      return Collections.emptyList();
    }
    return Json.toList(new TypeReference<List<String>>() {
    }, actions);
  }

  public static String readRejectAction(org.flowable.bpmn.model.Activity activity) {
    return getExtensionElementTexts(activity, "rejectAction").stream().findFirst().orElse(null);
  }

  public static void saveFlowDefinition(Process process, FlowDefinition flowDefinition) {
    addExtensionElement(process, "flowDefinition", Json.toJsonString(flowDefinition));
  }

  /**
   * Compatibility-only BPMN payload for V1 consumers. Default runtime/transpiler mainline should
   * persist and restore definitions from {@code flowDefinitionRawJson} instead.
   */
  @Deprecated
  public static void saveCompatibilityFlowDefinition(Process process, FlowDefinition flowDefinition) {
    saveFlowDefinition(process, flowDefinition);
  }

  public static void saveFlowDefinitionRawJson(Process process, String rawJson) {
    process.getExtensionElements().remove("flowDefinitionRawJson");
    addExtensionElement(process, "flowDefinitionRawJson", rawJson);
  }

  public static String readFlowDefinitionRawJson(Process process) {
    return Stream.of("flowDefinitionRawJson", "flowDefinition")
        .flatMap(name -> getExtensionElementTexts(process, name).stream())
        .filter(StringUtils::isNotBlank)
        .findFirst()
        .orElse(null);
  }

  public static String readFlowDefinitionRawJson(BpmnModel bpmnModel) {
    return readFlowDefinitionRawJson(bpmnModel.getProcesses().get(0));
  }

  public static StructuredFlowDefinition readStructuredFlowDefinition(Process process) {
    String rawJson = readFlowDefinitionRawJson(process);
    if (StringUtils.isBlank(rawJson)) {
      return null;
    }
    return FLOW_DEFINITION_CODEC.decode(rawJson)
        .onFailure(e -> logger.error("读取流程定义失败", e))
        .getOrElse((StructuredFlowDefinition) null);
  }

  public static StructuredFlowDefinition readStructuredFlowDefinition(BpmnModel bpmnModel) {
    return readStructuredFlowDefinition(bpmnModel.getProcesses().get(0));
  }

  public static FlowDefinition readFlowDefinition(Process process) {
    StructuredFlowDefinition structuredDefinition = readStructuredFlowDefinition(process);
    if (structuredDefinition == null) {
      return null;
    }
    return FLOW_DEFINITION_CODEC.encodeV1(structuredDefinition)
        .onFailure(e -> logger.error("转换V1流程定义失败", e))
        .getOrElse((FlowDefinition) null);
  }

  public static FlowDefinition readFlowDefinition(BpmnModel bpmnModel) {
    return readFlowDefinition(bpmnModel.getProcesses().get(0));
  }

  public static FlowDefinition.Notification readNotification(
      RepositoryService repositoryService,
      String processDefinitionId) {
    if (repositoryService == null || StringUtils.isBlank(processDefinitionId)) {
      return null;
    }

    return Optional.ofNullable(repositoryService.getBpmnModel(processDefinitionId))
        .map(BpmnUtil::readStructuredFlowDefinition)
        .map(StructuredFlowDefinition::getNotification)
        .orElse(null);
  }

  /**
   * Compatibility-only V1 projection facade. Default runtime mainline should prefer
   * {@link #readStructuredFlowDefinition(Process)} / {@link #readFlowDefinitionRawJson(Process)}.
   */
  @Deprecated
  public static FlowDefinition readCompatibilityFlowDefinition(Process process) {
    return readFlowDefinition(process);
  }

  /**
   * Compatibility-only V1 projection facade. Default runtime mainline should prefer
   * {@link #readStructuredFlowDefinition(BpmnModel)} / {@link #readFlowDefinitionRawJson(BpmnModel)}.
   */
  @Deprecated
  public static FlowDefinition readCompatibilityFlowDefinition(BpmnModel bpmnModel) {
    return readFlowDefinition(bpmnModel);
  }

  public static Supplier<com.ouroboros.workflow.approval.definition.FlowNode> getFlowNodeSupplier(
      RepositoryService repositoryService,
      String processDefinitionId,
      String flowNodeId
  ) {
    return () -> {
      if (repositoryService == null) {
        return null;
      }

      Try<Optional<com.ouroboros.workflow.approval.definition.FlowNode>> definitionTry = Try.of(() -> {
            BpmnModel model = repositoryService.getBpmnModel(processDefinitionId);
            if (model == null) {
              return null;
            }
            StructuredFlowDefinition flowDefinition = readStructuredFlowDefinition(model);
            if (flowDefinition == null) {
              throw new InternalException("反序列化流程定义失败");
            }
            return flowDefinition;
          })
          .map(definition -> definition.getNodes().stream()
              .filter(flowNode -> flowNodeId.equals(flowNode.getId()))
              .findFirst());
      return definitionTry.toEither()
          .peekLeft(e -> logger.error("获取任务节点定义失败", e))
          .getOrElse(Optional::empty)
          .orElse(null);
    };
  }

  public static String resolveFlowKey(RepositoryService repositoryService, DelegateExecution execution) {
    if (execution == null) {
      return null;
    }
    return resolveFlowKey(
        repositoryService,
        execution.getVariable(Constants.Variable.PROCESS_FLOW_KEY, String.class),
        execution.getProcessDefinitionId());
  }

  public static String resolveFlowKey(RepositoryService repositoryService, DelegateTask task) {
    if (task == null) {
      return null;
    }
    return resolveFlowKey(
        repositoryService,
        task.getVariable(Constants.Variable.PROCESS_FLOW_KEY, String.class),
        task.getProcessDefinitionId());
  }

  /**
   * 读取"字符串列表"型流程变量，元素类型不对时显式失败。
   *
   * <p>Flowable 的 {@code getVariable(name, List.class)} 只会校验到 List 这一层，
   * 拿到之后按 {@code List<String>} 使用就必须做未检查强转（违反"禁止类型擦除"红线）。
   * 这里把逐元素校验集中到一处，调用方不再需要 {@code @SuppressWarnings("unchecked")}。
   *
   * <p>变量缺失返回空列表；值不是 List 或存在非字符串元素时抛 {@code PlatformException}——
   * 静默跳过会让"候选人列表读错"表现为审批任务没人可办，比直接失败更难定位。
   */
  public static List<String> readStringListVariable(Object rawValue, String variableName) {
    if (rawValue == null) {
      return Collections.emptyList();
    }
    if (!(rawValue instanceof List<?> list)) {
      throw new PlatformException("[" + ApprovalWorkflowErrorCodes.WORKFLOW_ENGINE_NOT_FOUND
          + "] 流程变量不是列表: " + variableName + "，实际为 " + rawValue.getClass().getSimpleName());
    }
    List<String> values = new ArrayList<>(list.size());
    for (Object element : list) {
      if (element != null && !(element instanceof String)) {
        throw new PlatformException("[" + ApprovalWorkflowErrorCodes.WORKFLOW_ENGINE_NOT_FOUND
            + "] 流程变量包含非字符串元素: " + variableName + "，元素类型 "
            + element.getClass().getSimpleName());
      }
      // 显式转 String 不会产生未检查告警（已逐元素校验过）
      values.add((String) element);
    }
    return values;
  }

  private static String resolveFlowKey(RepositoryService repositoryService, String flowKey, String processDefinitionId) {
    if (StringUtils.isNotBlank(flowKey)) {
      return flowKey;
    }

    if (repositoryService == null || StringUtils.isBlank(processDefinitionId)) {
      return null;
    }

    ProcessDefinition processDefinition = repositoryService.getProcessDefinition(processDefinitionId);
    return processDefinition == null ? null : processDefinition.getKey();
  }
}
