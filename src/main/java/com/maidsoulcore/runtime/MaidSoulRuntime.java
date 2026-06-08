package com.maidsoulcore.runtime;

import com.maidsoulcore.blackboard.BlackboardStore;
import com.maidsoulcore.blackboard.BlackboardView;
import com.maidsoulcore.decision.DecisionGate;
import com.maidsoulcore.decision.DecisionResult;
import com.maidsoulcore.decision.DecisionRoute;
import com.maidsoulcore.event.EventBus;
import com.maidsoulcore.event.EventStage;
import com.maidsoulcore.event.InMemoryEventBus;
import com.maidsoulcore.event.MaidEvent;
import com.maidsoulcore.planner.ActionPlan;
import com.maidsoulcore.planner.PlannerClient;
import com.maidsoulcore.planner.PlannerRequest;
import com.maidsoulcore.trace.TraceEvent;
import com.maidsoulcore.trace.TraceSink;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * MaidSoulCore 运行时总调度器。
 * <p>
 * 这是核心层里最接近“主循环”的类：
 * 负责接事件、查黑板、做路由、调 Planner、写 trace。
 */
public final class MaidSoulRuntime {
    private final RuntimeConfig config;
    private final EventBus eventBus;
    private final BlackboardStore blackboardStore;
    private final DecisionGate decisionGate;
    private final PlannerClient plannerClient;
    private final TraceSink traceSink;
    private final AtomicLong traceSequence = new AtomicLong();

    public MaidSoulRuntime(
            RuntimeConfig config,
            BlackboardStore blackboardStore,
            DecisionGate decisionGate,
            PlannerClient plannerClient,
            TraceSink traceSink
    ) {
        this.config = Objects.requireNonNull(config);
        this.eventBus = new InMemoryEventBus();
        this.blackboardStore = Objects.requireNonNull(blackboardStore);
        this.decisionGate = Objects.requireNonNull(decisionGate);
        this.plannerClient = Objects.requireNonNull(plannerClient);
        this.traceSink = Objects.requireNonNull(traceSink);
        this.eventBus.subscribe(this::handleEvent);
    }

    /**
     * 对外暴露内部事件总线，供宿主层发布事件。
     */
    public EventBus eventBus() {
        return eventBus;
    }

    /**
     * 返回当前运行时配置。
     */
    public RuntimeConfig config() {
        return config;
    }

    /**
     * 处理单条内部事件。
     * <p>
     * 当前逻辑是最小闭环：
     * 先走 DecisionGate，再按需要调用 Planner。
     */
    private void handleEvent(MaidEvent event) {
        BlackboardView blackboard = blackboardStore.snapshot(event.maidId(), event.ownerId());
        DecisionResult decision = decisionGate.route(event, blackboard);
        String linkId = UUID.randomUUID().toString();
        trace(event, linkId, EventStage.GATE, decision);
        if (decision.route() == DecisionRoute.HYBRID_PLAN) {
            ActionPlan plan = plannerClient.plan(new PlannerRequest(event, blackboard));
            traceSink.accept(new TraceEvent(
                    traceSequence.incrementAndGet(),
                    Instant.now(),
                    event.maidId(),
                    linkId,
                    event.type(),
                    event.priority(),
                    EventStage.PLAN,
                    decision.route().name(),
                    plan.summary()
            ));
        }
    }

    /**
     * 记录一条 trace。
     */
    private void trace(MaidEvent event, String linkId, EventStage stage, DecisionResult decision) {
        traceSink.accept(new TraceEvent(
                traceSequence.incrementAndGet(),
                Instant.now(),
                event.maidId(),
                linkId,
                event.type(),
                event.priority(),
                stage,
                decision.route().name(),
                decision.reason()
        ));
    }
}
