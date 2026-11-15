package com.ouroboros.workflow.approval;

/**
 * 内置引擎 FlowTaskService 实现标记。
 *
 * <p>供 ability 层按类型选取内置 Bean（如镜像快捷回调需排除路由门面取得内置引擎任务服务），
 * 避免反向依赖路由类。
 */
public interface BuiltInFlowTaskService extends FlowTaskService {
}
