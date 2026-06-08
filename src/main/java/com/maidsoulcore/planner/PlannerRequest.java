package com.maidsoulcore.planner;

import com.maidsoulcore.blackboard.BlackboardView;
import com.maidsoulcore.event.MaidEvent;

/**
 * 发给 Planner 的输入。
 * <p>
 * 当前最关键的两部分是：
 * 1. 刚刚发生的触发事件
 * 2. 当前黑板快照
 */
public record PlannerRequest(
        MaidEvent event,
        BlackboardView blackboard
) {
}
