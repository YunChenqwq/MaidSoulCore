package com.maidsoulcore.planner;

import java.util.List;

/**
 * Planner 的完整输出。
 *
 * @param planId   计划唯一标识
 * @param summary  计划摘要，便于日志与调试展示
 * @param actions  需要依次执行的动作列表
 */
public record ActionPlan(
        String planId,
        String summary,
        List<PlannedAction> actions
) {
}
