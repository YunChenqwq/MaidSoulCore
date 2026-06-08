package com.maidsoulcore.sim;

import com.maidsoulcore.blackboard.BlackboardView;
import com.maidsoulcore.decision.DecisionResult;
import com.maidsoulcore.event.MaidEvent;
import com.maidsoulcore.planner.ActionPlan;

import java.util.List;

/**
 * 单轮模拟的完整输出。
 * <p>
 * 控制台模式下，这个对象就是“调试面板数据源”的简化版。
 */
public record SimulationTurnResult(
        MaidEvent event,
        BlackboardView blackboard,
        DecisionResult decision,
        ActionPlan plan,
        List<String> executionLogs,
        String reply
) {
}
