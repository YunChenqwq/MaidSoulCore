package com.maidsoulcore.decision;

/**
 * 事件在运行时中的几条核心路由。
 */
public enum DecisionRoute {
    /** 毫秒级本地规则处理，不等待远程模型。 */
    LOCAL_RULE,
    /** 规则兜底后交给 Planner 做结构化决策。 */
    HYBRID_PLAN,
    /** 只进入语言表达层，不触发高风险动作。 */
    REPLY_ONLY,
    /** 价值较低或时效已过，直接丢弃。 */
    DROP
}
