package com.maidsoul.brain.action.ysm;

import com.maidsoul.brain.reasoning.PlanDecision;

import java.util.function.Consumer;

/**
 * 动作调度桥 — brain 引擎和 Forge 层之间的事件通道。
 *
 * <p>Forge 层在运行时创建时设置 {@link #handler}，brain 引擎（ReasoningEngine）
 * 遇到 play_pose/play_animation 决策时调用此 handler，由 Forge 层发网络包到客户端。
 */
public final class ActionDispatchHandler {
    public static Consumer<PlanDecision> handler;

    private ActionDispatchHandler() {}
}
