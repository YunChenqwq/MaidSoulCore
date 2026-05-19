package com.maidsoul.brain.planner.hook;

/**
 * planner.before_request 扩展点。
 *
 * <p>用于在规划器请求前追加外部信息或额外工具。它不是插件系统本身，只是一个稳定插槽；
 * 以后世界状态、视觉摘要、外部检索结果都可以从这里进入 planner。</p>
 */
@FunctionalInterface
public interface PlannerBeforeRequestHook {
    PlannerBeforeRequestResult beforeRequest(PlannerRequestContext context);
}
