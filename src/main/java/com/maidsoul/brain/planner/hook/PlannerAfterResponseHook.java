package com.maidsoul.brain.planner.hook;

/**
 * planner.after_response 扩展点。
 *
 * <p>对齐 上游参考系统 的 planner.after_response：允许运行时信息层在模型返回后调整文本或工具调用结果。
 * 原型机当前只暴露 PlanDecision 级别的调整，不引入完整插件序列化。</p>
 */
@FunctionalInterface
public interface PlannerAfterResponseHook {
    PlannerAfterResponseResult afterResponse(PlannerResponseContext context);
}
