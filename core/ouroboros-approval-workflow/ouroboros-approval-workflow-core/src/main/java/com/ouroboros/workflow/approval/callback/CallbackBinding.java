package com.ouroboros.workflow.approval.callback;

import java.util.Map;

/**
 * 审批流程回调绑定元数据（设计文档 L684）。
 *
 * <p>由业务模型投影管线生成并注册进 {@code ApprovalProcessCallbackBindingCatalog}
 * （随元数据刷新事件重建缓存）；statusField/statusMap 供 processStatusRouter 参数化
 * （模板当前编译期固化，元数据为运行时参数化/审计的接入点）。
 *
 * @param flowKey     绑定流程定义 ID（workflow.bindings[0].processId）
 * @param callbackFlow 回调逻辑流名（{@code recordType + ".processCallback"}）
 * @param recordType  业务记录类型（业务模型 fullName）
 * @param statusField 业务记录状态字段名（可空）
 * @param statusMap   业务状态码映射（processing/approved/veto/canceled/terminated，可空）
 */
public record CallbackBinding(
    String flowKey,
    String callbackFlow,
    String recordType,
    String statusField,
    Map<String, Object> statusMap) {
}
