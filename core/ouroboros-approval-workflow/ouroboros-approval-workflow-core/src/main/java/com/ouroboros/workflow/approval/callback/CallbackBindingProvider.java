package com.ouroboros.workflow.approval.callback;

import java.util.List;

/**
 * 回调绑定元数据提供者（设计 L684"随元数据刷新事件重建缓存，机制同现有 metadata provider"）。
 *
 * <p>业务模型投影侧实现（{@code BusinessModelExport}），catalog 在元数据刷新事件时聚合重建。
 */
public interface CallbackBindingProvider {

  /**
   * 当前全部回调绑定元数据（按 recordType 唯一）。
   */
  List<CallbackBinding> getCallbackBindings();
}
