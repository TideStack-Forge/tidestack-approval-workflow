package com.ouroboros.workflow.approval;

import io.vavr.control.Try;

/**
 * 引擎内部受信任任务动作端口。
 *
 * <p>该端口与面向用户的 {@link FlowTaskService} 分离。实现必须在自己的事务和 listener
 * 语义内执行动作；上层不得通过 magic actor 调用普通用户入口获得同等权限。
 */
public interface TrustedFlowTaskActionService {

  Try<Void> execute(TrustedFlowTaskAction action);
}
