package com.maidsoulcore.planner;

import java.util.Map;

/**
 * Planner 输出的单个动作节点。
 */
public record PlannedAction(
        String actionType,
        Map<String, Object> parameters
) {
}
