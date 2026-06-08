package com.maidsoulcore.forge.service;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.maidsoulcore.event.EventPriority;
import com.maidsoulcore.forge.plan.MaidSoulEventEnvelope;
import com.maidsoulcore.forge.plan.MaidSoulPlan;
import com.maidsoulcore.forge.plan.MaidSoulPlanPriority;
import com.maidsoulcore.forge.plan.MaidSoulPlanStatus;
import com.maidsoulcore.forge.plan.MaidSoulPlanStep;
import com.maidsoulcore.forge.plan.MaidSoulPlanStepStatus;
import com.maidsoulcore.forge.state.MaidSoulStateRegistry;

import java.util.ArrayDeque;
import java.util.Deque;
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

    private static void executeCurrentStep(EntityMaid maid, RuntimeState state, MaidSoulPlan plan, MaidSoulPlanStep step) {
        MaidSoulChatRuntimeService.PlannerDecision decision = new MaidSoulChatRuntimeService.PlannerDecision(
                true,
                "plan",
                "plan",
                step.description(),
                plan.objective(),
                false,
                "",
                step.actionType(),
                step.actionValue(),
                step.targetEntityId()
        );
        trace(maid, "maidsoul.plan.step.start", plan.planId() + " | " + step.summary());
        MaidSoulStateRegistry.record(maid, "maidsoul.plan.step.start", step.summary(), EventPriority.P1);
        String result = MaidSoulActionExecutorService.execute(maid, decision);
        step.markRunning(maid.tickCount, result);
        trace(maid, "maidsoul.plan.step.result", plan.planId() + " | " + result);

        if (isImmediateFailure(result)) {
            step.markFailed(result);
            failPlan(maid, state, plan, "step failed immediately: " + result);
            return;
        }
        if (isImmediateStep(step)) {
            step.markCompleted(result);
            completeCurrentStep(maid, state, plan, "immediate:" + result);
            return;
        }
    }

    private static void checkRunningStep(EntityMaid maid, RuntimeState state, MaidSoulPlan plan, MaidSoulPlanStep step) {
        if (step.isTimedOut(maid.tickCount)) {
            step.markFailed("timeout");
            failPlan(maid, state, plan, "step timeout: " + step.summary());
            return;
        }
        if (isCombatStep(step) && !MaidSoulActionExecutorService.hasActiveCombatPlan(maid)) {
            step.markCompleted("combat_finished");
            completeCurrentStep(maid, state, plan, "combat_finished");
            return;
        }
        MaidSoulEventEnvelope event = state.recentEvents.peekLast();
        if (event != null && isCompletionEventForStep(step, event)) {
            step.markCompleted(event.eventType() + "|" + event.detail());
            completeCurrentStep(maid, state, plan, "event:" + event.eventType());
        }
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

    private static boolean isImmediateStep(MaidSoulPlanStep step) {
        return switch (step.actionType().toUpperCase()) {
            case "FOLLOW_ON", "FOLLOW_OFF", "SIT_ON", "SIT_OFF", "SET_SCHEDULE", "SET_TASK" -> true;
            default -> false;
        };
    }

    private static boolean isCombatStep(MaidSoulPlanStep step) {
        return switch (step.actionType().toUpperCase()) {
            case "ENTER_COMBAT", "ENTER_COMBAT_GROUP" -> true;
            default -> false;
        };
    }

    private static boolean isImmediateFailure(String result) {
        return result == null
                || result.isBlank()
                || result.startsWith("unknown_action")
                || result.contains("missing")
                || result.contains("unavailable")
                || result.contains("invalid")
                || result.contains("FAIL");
    }

    private static boolean isCompletionEventForStep(MaidSoulPlanStep step, MaidSoulEventEnvelope event) {
        if (event == null) {
            return false;
        }
        if (isCombatStep(step)) {
            return event.eventType().startsWith("maidsoul.attack.plan.clear")
                    || event.eventType().startsWith("maidsoul.plan.step.completed");
        }
        return false;
    }

    private static RuntimeState stateFor(EntityMaid maid) {
        return STATES.computeIfAbsent(maid.getUUID(), id -> new RuntimeState());
    }

    private static void trace(EntityMaid maid, String type, String detail) {
        MaidSoulStateRegistry.echoTraceToOwnerChat(maid, type, detail);
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
     * 便捷工厂：创建一条普通主人命令计划。
     */
    public static MaidSoulPlan ownerCommandPlan(String source, String objective, List<MaidSoulPlanStep> steps) {
        return new MaidSoulPlan(source, objective, MaidSoulPlanPriority.OWNER_COMMAND, steps);
    }
}
