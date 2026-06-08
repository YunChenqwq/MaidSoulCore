package com.maidsoulcore.forge.service;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.maidsoulcore.forge.execution.MaidSoulExecutionEvent;
import com.maidsoulcore.forge.execution.MaidSoulExecutionEventType;
import com.maidsoulcore.forge.execution.MaidSoulExecutionStatus;
import com.maidsoulcore.forge.plan.MaidSoulPlan;
import com.maidsoulcore.forge.plan.MaidSoulPlanStep;
import com.maidsoulcore.forge.state.MaidSoulStateRegistry;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 统一的执行层入口。
 * <p>
 * 第一阶段只做“收口”：
 * - 计划层不再直接调用具体动作执行细节
 * - 计划层改为向执行层分发 step
 * - 执行层用统一事件回传 started / completed / failed / cancelled
 * <p>
 * 为了降低改造风险，底层动作暂时继续复用现有 `MaidSoulActionExecutorService`
 * 和战斗执行控制器；本类先把它们包装成稳定的执行事件流。
 */
public final class MaidSoulExecutionManager {
    private static final ConcurrentMap<UUID, ActiveExecution> ACTIVE_EXECUTIONS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<UUID, Deque<MaidSoulExecutionEvent>> EVENTS = new ConcurrentHashMap<>();

    private MaidSoulExecutionManager() {
    }

    /**
     * 分发当前 step 到执行层。
     */
    public static DispatchResult dispatchStep(EntityMaid maid, MaidSoulPlan plan, MaidSoulPlanStep step) {
        if (maid == null || plan == null || step == null) {
            return new DispatchResult(false, "execution_rejected");
        }
        ActiveExecution activeExecution = ACTIVE_EXECUTIONS.get(maid.getUUID());
        if (activeExecution != null) {
            return new DispatchResult(false, "execution_busy:" + activeExecution.actionType());
        }

        MaidSoulActionValidationService.ValidationResult validationResult =
                MaidSoulActionValidationService.validate(maid, plan, step);
        if (!validationResult.valid()) {
            return new DispatchResult(false, validationResult.reason());
        }

        String actionType = step.actionType();
        emit(maid, MaidSoulExecutionEvent.of(
                plan.planId(),
                plan.currentStepIndex(),
                actionType,
                MaidSoulExecutionEventType.STEP_DISPATCHED,
                MaidSoulExecutionStatus.RUNNING,
                step.summary()
        ));

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
        String result = MaidSoulActionExecutorService.execute(maid, decision);

        emit(maid, MaidSoulExecutionEvent.of(
                plan.planId(),
                plan.currentStepIndex(),
                actionType,
                MaidSoulExecutionEventType.STEP_STARTED,
                MaidSoulExecutionStatus.RUNNING,
                result
        ));

        if (isImmediateFailure(result)) {
            emit(maid, MaidSoulExecutionEvent.of(
                    plan.planId(),
                    plan.currentStepIndex(),
                    actionType,
                    MaidSoulExecutionEventType.STEP_FAILED,
                    MaidSoulExecutionStatus.FAILED,
                    result
            ));
            return new DispatchResult(true, result);
        }

        if (isImmediateAction(step)) {
            emit(maid, MaidSoulExecutionEvent.of(
                    plan.planId(),
                    plan.currentStepIndex(),
                    actionType,
                    MaidSoulExecutionEventType.STEP_COMPLETED,
                    MaidSoulExecutionStatus.COMPLETED,
                    result
            ));
            return new DispatchResult(true, result);
        }

        ACTIVE_EXECUTIONS.put(
                maid.getUUID(),
                new ActiveExecution(plan.planId(), plan.currentStepIndex(), actionType, result)
        );
        emit(maid, MaidSoulExecutionEvent.of(
                plan.planId(),
                plan.currentStepIndex(),
                actionType,
                MaidSoulExecutionEventType.STEP_RUNNING,
                MaidSoulExecutionStatus.RUNNING,
                result
        ));
        return new DispatchResult(true, result);
    }

    /**
     * 每 tick 推进一步执行层。
     */
    public static void onMaidTick(EntityMaid maid) {
        if (maid == null) {
            return;
        }
        ActiveExecution activeExecution = ACTIVE_EXECUTIONS.get(maid.getUUID());
        if (activeExecution == null) {
            MaidSoulCombatExecutionController.consumeFinishedResult(maid);
            return;
        }

        if (!isCombatAction(activeExecution.actionType())) {
            ACTIVE_EXECUTIONS.remove(maid.getUUID());
            return;
        }

        MaidSoulCombatExecutionController.CombatExecutionResult finishedResult =
                MaidSoulCombatExecutionController.consumeFinishedResult(maid);
        if (finishedResult == null) {
            return;
        }

        ACTIVE_EXECUTIONS.remove(maid.getUUID());
        MaidSoulExecutionEventType eventType = switch (finishedResult.status()) {
            case COMPLETED -> MaidSoulExecutionEventType.STEP_COMPLETED;
            case FAILED -> MaidSoulExecutionEventType.STEP_FAILED;
            case CANCELLED -> MaidSoulExecutionEventType.STEP_CANCELLED;
            case RUNNING -> MaidSoulExecutionEventType.STEP_RUNNING;
        };
        emit(maid, MaidSoulExecutionEvent.of(
                activeExecution.planId(),
                activeExecution.stepIndex(),
                activeExecution.actionType(),
                eventType,
                finishedResult.status(),
                finishedResult.result()
        ));
    }

    /**
     * 抢占计划时取消当前执行。
     */
    public static void cancelActiveExecution(EntityMaid maid, String reason) {
        if (maid == null) {
            return;
        }
        ActiveExecution activeExecution = ACTIVE_EXECUTIONS.remove(maid.getUUID());
        if (activeExecution == null) {
            return;
        }
        if (isCombatAction(activeExecution.actionType())) {
            MaidSoulCombatExecutionController.cancelActiveSession(maid, reason);
        }
        emit(maid, MaidSoulExecutionEvent.of(
                activeExecution.planId(),
                activeExecution.stepIndex(),
                activeExecution.actionType(),
                MaidSoulExecutionEventType.STEP_CANCELLED,
                MaidSoulExecutionStatus.CANCELLED,
                reason
        ));
    }

    /**
     * 计划层轮询执行事件。
     */
    public static MaidSoulExecutionEvent pollEvent(EntityMaid maid) {
        Deque<MaidSoulExecutionEvent> queue = EVENTS.get(maid.getUUID());
        return queue == null ? null : queue.pollFirst();
    }

    private static void emit(EntityMaid maid, MaidSoulExecutionEvent event) {
        EVENTS.computeIfAbsent(maid.getUUID(), id -> new ArrayDeque<>()).addLast(event);
        MaidSoulStateRegistry.echoTraceToOwnerChat(
                maid,
                "maidsoul.execution.event",
                event.eventType().name().toLowerCase(Locale.ROOT)
                        + " | plan=" + event.planId()
                        + " | step=" + event.stepIndex()
                        + " | action=" + event.actionType()
                        + " | detail=" + event.detail()
        );
    }

    private static boolean isImmediateAction(MaidSoulPlanStep step) {
        MaidSoulActionMetadataService.ActionMetadata metadata =
                MaidSoulActionMetadataService.forAction(step.actionType());
        return metadata != null && metadata.immediate();
    }

    private static boolean isCombatAction(String actionType) {
        MaidSoulActionMetadataService.ActionMetadata metadata =
                MaidSoulActionMetadataService.forAction(actionType);
        return metadata != null && metadata.combat();
    }

    private static boolean isImmediateFailure(String result) {
        return result == null
                || result.isBlank()
                || result.startsWith("unknown_action")
                || result.contains("missing")
                || result.contains("unavailable")
                || result.contains("invalid")
                || result.contains("FAIL")
                || result.contains("busy");
    }

    /**
     * 分发结果。
     */
    public record DispatchResult(boolean accepted, String result) {
    }

    /**
     * 当前运行中的执行记录。
     */
    private record ActiveExecution(String planId, int stepIndex, String actionType, String startResult) {
    }
}
