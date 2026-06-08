package com.maidsoulcore.bootstrap;

import com.maidsoulcore.blackboard.BlackboardStore;
import com.maidsoulcore.decision.DecisionGate;
import com.maidsoulcore.planner.ActionPlan;
import com.maidsoulcore.planner.PlannerClient;
import com.maidsoulcore.planner.PlannedAction;
import com.maidsoulcore.runtime.MaidSoulRuntime;
import com.maidsoulcore.runtime.RuntimeConfig;
import com.maidsoulcore.trace.CompositeTraceSink;
import com.maidsoulcore.trace.RingBufferTraceSink;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 纯 Java 运行时的快速启动器。
 * <p>
 * 这个类主要用于：
 * 1. 在没有 Forge/TLM 的情况下快速拼出一个默认运行时；
 * 2. 方便后续做单元测试、离线调试或原型验证。
 */
public final class MaidSoulBootstrap {
    private MaidSoulBootstrap() {
    }

    /**
     * 创建一套最小可运行的默认运行时。
     * <p>
     * 当前 Planner 是一个占位实现，便于框架先跑通。
     */
    public static MaidSoulRuntime createDefaultRuntime() {
        RingBufferTraceSink ringBuffer = new RingBufferTraceSink(RuntimeConfig.defaults().traceBufferSize());
        PlannerClient plannerClient = request -> new ActionPlan(
                UUID.randomUUID().toString(),
                "stub planner action",
                List.of(new PlannedAction("observe_owner", Map.of()))
        );
        return new MaidSoulRuntime(
                RuntimeConfig.defaults(),
                new BlackboardStore(),
                new DecisionGate(),
                plannerClient,
                new CompositeTraceSink(List.of(ringBuffer))
        );
    }
}
