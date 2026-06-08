package com.maidsoulcore.forge.service;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.maidsoulcore.event.EventPriority;
import com.maidsoulcore.forge.plan.MaidSoulEventEnvelope;
import com.maidsoulcore.forge.plan.MaidSoulPlan;
import com.maidsoulcore.forge.plan.MaidSoulPlanPriority;
import com.maidsoulcore.forge.plan.MaidSoulPlanStatus;
import com.maidsoulcore.forge.plan.MaidSoulPlanStep;
import com.maidsoulcore.forge.plan.MaidSoulPlanStepStatus;
import com.maidsoulcore.forge.execution.MaidSoulExecutionEvent;
import com.maidsoulcore.forge.execution.MaidSoulExecutionEventType;
import com.maidsoulcore.forge.state.MaidSoulStateRegistry;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * MaidSoulCore 本地计划调度服务。
 * <p>
 * 这是第一版“真正规划层”：
 * - 所有事件先进入统一计划服务
 * - 多步骤命令会被压成计划
 * - 计划按优先级排队、抢占、推进
 * - 计划链全过程写入 trace 便于调试
 */
public final class MaidSoulPlanService {
    private static final ConcurrentMap<UUID, RuntimeState> STATES = new ConcurrentHashMap<>();

    private MaidSoulPlanService() {
    }

    /**
     * 发布一条运行时事件给计划层。
     */
    public static void publishEvent(EntityMaid maid, String eventType, String detail, EventPriority priority) {
        if (maid == null) {
            return;
        }
        MaidSoulEventEnvelope event = MaidSoulEventEnvelope.of(eventType, detail, priority);
        RuntimeState state = stateFor(maid);
        state.recentEvents.addLast(event);
        while (state.recentEvents.size() > 32) {
            state.recentEvents.removeFirst();
        }
        MaidSoulPlan activePlan = state.activePlan;
        if (activePlan != null) {
            activePlan.updateLastEvent(event);
        }
        if (!event.eventType().startsWith("maidsoul.plan.")) {
            trace(maid, "maidsoul.plan.event", event.eventType() + " | " + event.detail());
        }
    }

    /**
     * 提交一条新计划。
     */
    public static String submitPlan(EntityMaid maid, MaidSoulPlan plan) {
        if (maid == null || plan == null || plan.steps().isEmpty()) {
            return "plan_rejected";
        }
        RuntimeState state = stateFor(maid);
        MaidSoulPlan activePlan = state.activePlan;
        if (activePlan != null && shouldPreempt(plan, activePlan)) {
            MaidSoulPlanStep currentStep = activePlan.currentStep();
            if (currentStep != null && currentStep.status() == MaidSoulPlanStepStatus.RUNNING) {
                currentStep.resetToPending("preempted");
            }
            MaidSoulExecutionManager.cancelActiveExecution(maid, "plan_preempted");
            activePlan.setStatus(MaidSoulPlanStatus.PAUSED);
            state.queue.addFirst(activePlan);
            trace(maid, "maidsoul.plan.preempt", "incoming=" + plan.summary() + " | paused=" + activePlan.summary());
            state.activePlan = plan;
            plan.setStatus(MaidSoulPlanStatus.RUNNING);
            trace(maid, "maidsoul.plan.activated", plan.summary());
            MaidSoulStateRegistry.record(maid, "maidsoul.plan.activated", plan.summary(), EventPriority.P1);
            return "plan_preempted:" + plan.planId();
        }
        if (state.activePlan == null) {
            state.activePlan = plan;
            plan.setStatus(MaidSoulPlanStatus.RUNNING);
            trace(maid, "maidsoul.plan.activated", plan.summary());
            MaidSoulStateRegistry.record(maid, "maidsoul.plan.activated", plan.summary(), EventPriority.P1);
            return "plan_activated:" + plan.planId();
        }
        plan.setStatus(MaidSoulPlanStatus.QUEUED);
        state.queue.addLast(plan);
        trace(maid, "maidsoul.plan.queued", plan.summary());
        MaidSoulStateRegistry.record(maid, "maidsoul.plan.queued", plan.summary(), EventPriority.P1);
        return "plan_queued:" + plan.planId();
    }

    /**
     * 每 tick 推进一次计划链。
     */
    public static void onMaidTick(EntityMaid maid) {
        if (maid == null || !maid.isAlive()) {
            return;
        }
        RuntimeState state = stateFor(maid);
        drainExecutionEvents(maid, state);
        if (state.activePlan == null) {
            activateNextPlan(maid, state);
            return;
        }

        MaidSoulPlan plan = state.activePlan;
        MaidSoulPlanStep step = plan.currentStep();
        if (step == null) {
            finishPlan(maid, state, plan, "all steps finished");
            return;
        }

        if (step.status() == MaidSoulPlanStepStatus.PENDING) {
            executeCurrentStep(maid, state, plan, step);
            return;
        }
        if (step.status() == MaidSoulPlanStepStatus.RUNNING) {
            checkRunningStep(maid, state, plan, step);
        }
    }

    /**
     * 返回当前计划调试摘要。
     */
    public static String describePlanState(EntityMaid maid) {
        RuntimeState state = stateFor(maid);
        if (state.activePlan == null && state.queue.isEmpty()) {
            return "no active plan";
        }
        String active = state.activePlan == null ? "none" : state.activePlan.summary();
        return "active=" + active + ", queued=" + state.queue.size();
    }

    /**
     * 为客户端调试叠加层导出当前计划快照。
     * <p>
     * 当前实现仅用于本地集成服务端场景，不承担跨网络同步职责。
     */
    public static PlanDebugSnapshot snapshotFor(EntityMaid maid) {
        RuntimeState state = STATES.get(maid.getUUID());
        if (state == null || state.activePlan == null) {
            return null;
        }
        MaidSoulPlan activePlan = state.activePlan;
        ArrayList<PlanStepView> stepViews = new ArrayList<>();
        List<MaidSoulPlanStep> steps = activePlan.steps();
        for (int index = 0; index < steps.size(); index++) {
            MaidSoulPlanStep step = steps.get(index);
            stepViews.add(new PlanStepView(
                    index,
                    step.description(),
                    step.actionType(),
                    step.actionValue(),
                    step.status().name(),
                    index == activePlan.currentStepIndex()
            ));
        }
        return new PlanDebugSnapshot(
                maid.getUUID(),
                activePlan.planId(),
                activePlan.source(),
                activePlan.objective(),
                activePlan.status().name(),
                activePlan.currentStepIndex(),
                state.queue.size(),
                List.copyOf(stepViews)
        );
    }

    private static void executeCurrentStep(EntityMaid maid, RuntimeState state, MaidSoulPlan plan, MaidSoulPlanStep step) {
        trace(maid, "maidsoul.plan.step.dispatch", plan.planId() + " | " + step.summary());
        MaidSoulStateRegistry.record(maid, "maidsoul.plan.step.start", step.summary(), EventPriority.P1);
        MaidSoulExecutionManager.DispatchResult dispatchResult = MaidSoulExecutionManager.dispatchStep(maid, plan, step);
        if (!dispatchResult.accepted()) {
            step.markFailed(dispatchResult.result());
            failPlan(maid, state, plan, "step dispatch rejected: " + dispatchResult.result());
            return;
        }
        step.markRunning(maid.tickCount, dispatchResult.result());
        trace(maid, "maidsoul.plan.step.dispatched", plan.planId() + " | " + dispatchResult.result());
        drainExecutionEvents(maid, state);
    }

    private static void checkRunningStep(EntityMaid maid, RuntimeState state, MaidSoulPlan plan, MaidSoulPlanStep step) {
        if (step.isTimedOut(maid.tickCount)) {
            MaidSoulExecutionManager.cancelActiveExecution(maid, "step_timeout");
            step.markFailed("timeout");
            failPlan(maid, state, plan, "step timeout: " + step.summary());
            return;
        }
        drainExecutionEvents(maid, state);
    }

    private static void completeCurrentStep(EntityMaid maid, RuntimeState state, MaidSoulPlan plan, String detail) {
        MaidSoulPlanStep finishedStep = plan.currentStep();
        if (finishedStep != null) {
            trace(maid, "maidsoul.plan.step.completed", plan.planId() + " | " + finishedStep.summary() + " | " + detail);
            MaidSoulStateRegistry.record(maid, "maidsoul.plan.step.completed", finishedStep.summary() + " | " + detail, EventPriority.P1);
        }
        plan.advanceStep();
        if (!plan.hasMoreSteps()) {
            finishPlan(maid, state, plan, detail);
            return;
        }
        MaidSoulPlanStep nextStep = plan.currentStep();
        trace(maid, "maidsoul.plan.step.advance", plan.planId() + " | next=" + nextStep.summary());
        MaidSoulStateRegistry.record(maid, "maidsoul.plan.step.advance", nextStep.summary(), EventPriority.P1);
    }

    private static void finishPlan(EntityMaid maid, RuntimeState state, MaidSoulPlan plan, String detail) {
        plan.setStatus(MaidSoulPlanStatus.COMPLETED);
        trace(maid, "maidsoul.plan.completed", plan.summary() + " | " + detail);
        MaidSoulStateRegistry.record(maid, "maidsoul.plan.completed", plan.summary() + " | " + detail, EventPriority.P1);
        state.activePlan = null;
        activateNextPlan(maid, state);
    }

    private static void failPlan(EntityMaid maid, RuntimeState state, MaidSoulPlan plan, String detail) {
        plan.setStatus(MaidSoulPlanStatus.FAILED);
        trace(maid, "maidsoul.plan.failed", plan.summary() + " | " + detail);
        MaidSoulStateRegistry.record(maid, "maidsoul.plan.failed", plan.summary() + " | " + detail, EventPriority.P0);
        state.activePlan = null;
        activateNextPlan(maid, state);
    }

    private static void activateNextPlan(EntityMaid maid, RuntimeState state) {
        MaidSoulPlan next = state.queue.pollFirst();
        if (next == null) {
            return;
        }
        next.setStatus(MaidSoulPlanStatus.RUNNING);
        state.activePlan = next;
        trace(maid, "maidsoul.plan.activated", next.summary());
        MaidSoulStateRegistry.record(maid, "maidsoul.plan.activated", next.summary(), EventPriority.P1);
    }

    private static boolean shouldPreempt(MaidSoulPlan incoming, MaidSoulPlan active) {
        return incoming.priority().weight() > active.priority().weight();
    }

    private static RuntimeState stateFor(EntityMaid maid) {
        return STATES.computeIfAbsent(maid.getUUID(), id -> new RuntimeState());
    }

    private static void trace(EntityMaid maid, String type, String detail) {
        MaidSoulStateRegistry.echoTraceToOwnerChat(maid, type, detail);
    }

    private static void drainExecutionEvents(EntityMaid maid, RuntimeState state) {
        while (true) {
            MaidSoulExecutionEvent event = MaidSoulExecutionManager.pollEvent(maid);
            if (event == null) {
                return;
            }
            trace(maid, "maidsoul.plan.execution_event", event.eventType() + " | " + event.detail());
            MaidSoulPlan activePlan = state.activePlan;
            if (activePlan == null || !activePlan.planId().equals(event.planId())) {
                continue;
            }
            MaidSoulPlanStep step = activePlan.currentStep();
            if (step == null || activePlan.currentStepIndex() != event.stepIndex()) {
                continue;
            }
            if (step.status() != MaidSoulPlanStepStatus.RUNNING && event.eventType() != MaidSoulExecutionEventType.STEP_STARTED) {
                continue;
            }
            switch (event.eventType()) {
                case STEP_COMPLETED -> {
                    step.markCompleted(event.detail());
                    completeCurrentStep(maid, state, activePlan, "execution:" + event.detail());
                }
                case STEP_FAILED, STEP_CANCELLED -> {
                    step.markFailed(event.detail());
                    failPlan(maid, state, activePlan, "execution:" + event.detail());
                }
                default -> {
                }
            }
        }
    }

    /**
     * 运行时状态。
     */
    private static final class RuntimeState {
        private final Deque<MaidSoulPlan> queue = new ArrayDeque<>();
        private final Deque<MaidSoulEventEnvelope> recentEvents = new ArrayDeque<>();
        private MaidSoulPlan activePlan;
    }

    /**
     * 调试叠加层用的计划快照。
     */
    public record PlanDebugSnapshot(
            UUID maidUuid,
            String planId,
            String source,
            String objective,
            String status,
            int currentStepIndex,
            int queuedPlanCount,
            List<PlanStepView> steps
    ) {
    }

    /**
     * 调试叠加层用的步骤视图。
     */
    public record PlanStepView(
            int index,
            String description,
            String actionType,
            String actionValue,
            String status,
            boolean active
    ) {
        public String displayText() {
            if (description != null && !description.isBlank()) {
                return description;
            }
            if (actionValue != null && !actionValue.isBlank()) {
                return actionType + " -> " + actionValue;
            }
            return actionType;
        }
    }

    /**
     * 便捷工厂：创建一条普通主人命令计划。
     */
    public static MaidSoulPlan ownerCommandPlan(String source, String objective, List<MaidSoulPlanStep> steps) {
        return new MaidSoulPlan(source, objective, MaidSoulPlanPriority.OWNER_COMMAND, steps);
    }
}
