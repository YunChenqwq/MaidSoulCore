package com.maidsoulcore.planner;

/**
 * Planner 客户端抽象。
 * <p>
 * 后续这里可以对接：
 * 本地规则 Planner、OpenAI 兼容接口、MaiBot 风格多模型 Planner。
 */
public interface PlannerClient {
    /**
     * 根据请求生成一个结构化动作计划。
     */
    ActionPlan plan(PlannerRequest request);
}
