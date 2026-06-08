package com.maidsoulcore.forge.service;

import com.github.tartaricacid.touhoulittlemaid.api.task.FunctionCallSwitchResult;
import com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.MaidSchedule;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import com.maidsoulcore.event.EventPriority;
import com.maidsoulcore.forge.state.MaidSoulStateRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.schedule.Activity;

import java.util.Locale;
import java.util.Optional;

/**
 * MaidSoulCore 动作执行门面。
 * <p>
 * 这个类现在只保留两类职责：
 * 1. 执行非持续性的即时动作，例如跟随、坐下、切日程；
 * 2. 把持续性动作转交给独立执行层，例如战斗执行控制器。
 * <p>
 * 这样做的目的是把“会持续推进的任务”从聊天链路里剥离出去，
 * 避免 planner 每次只触发一次动作后就失去控制。
 */
public final class MaidSoulActionExecutorService {
    private MaidSoulActionExecutorService() {
    }

    /**
     * 每 tick 推进一次动作执行层。
     * <p>
     * 首版只先推进战斗执行会话。
     */
    public static void onMaidTick(EntityMaid maid) {
        MaidSoulCombatExecutionController.onMaidTick(maid);
    }

    /**
     * 执行一条 planner 输出的动作指令。
     */
    public static String execute(EntityMaid maid, MaidSoulChatRuntimeService.PlannerDecision plannerDecision) {
        String actionType = plannerDecision.actionType();
        if (actionType == null || actionType.isBlank() || "NONE".equalsIgnoreCase(actionType)) {
            return "no_action";
        }
        String result = switch (actionType.toUpperCase(Locale.ROOT)) {
            case "FOLLOW_ON" -> followOn(maid);
            case "FOLLOW_OFF" -> followOff(maid);
            case "SIT_ON" -> sit(maid, true);
            case "SIT_OFF" -> sit(maid, false);
            case "SET_SCHEDULE" -> setSchedule(maid, plannerDecision.actionValue());
            case "SET_TASK" -> setTask(maid, plannerDecision.actionValue());
            case "ENTER_COMBAT" -> enterCombat(maid, plannerDecision.actionValue(), plannerDecision.targetEntityId());
            case "ENTER_COMBAT_GROUP" -> enterCombatGroup(maid, plannerDecision.actionValue(), plannerDecision.targetEntityId());
            default -> "unknown_action:" + actionType;
        };
        MaidSoulStateRegistry.echoTraceToOwnerChat(
                maid,
                "maidsoul.chat.action.execute",
                "type=" + actionType
                        + ", value=" + plannerDecision.actionValue()
                        + ", target=" + plannerDecision.targetEntityId()
                        + ", result=" + result
        );
        return result;
    }

    /**
     * 直接进入单目标战斗。
     * <p>
     * 该入口给 tool loop 和调试工具复用。
     */
    public static String enterCombatDirectly(EntityMaid maid, String preferredTaskId, int targetEntityId) {
        return enterCombat(maid, preferredTaskId, targetEntityId);
    }

    /**
     * 直接进入群体战斗。
     */
    public static String enterCombatGroupDirectly(EntityMaid maid, String preferredTaskId, String entityTypeId, int targetEntityId) {
        return enterCombatGroup(maid, preferredTaskId, entityTypeId, targetEntityId);
    }

    /**
     * 导出当前战斗执行摘要。
     */
    public static String describeAttackPlan(EntityMaid maid) {
        return MaidSoulCombatExecutionController.describeSession(maid);
    }

    /**
     * 当前是否仍有活跃战斗执行会话。
     */
    public static boolean hasActiveCombatPlan(EntityMaid maid) {
        return MaidSoulCombatExecutionController.hasActiveSession(maid);
    }

    private static String followOn(EntityMaid maid) {
        if (!maid.isHomeModeEnable()) {
            return "already_following";
        }
        maid.setHomeModeEnable(false);
        MaidSoulStateRegistry.record(maid, "maidsoul.action.follow_on", "follow owner", EventPriority.P1);
        return "follow_enabled";
    }

    private static String followOff(EntityMaid maid) {
        if (maid.isHomeModeEnable()) {
            return "already_home_mode";
        }
        maid.getSchedulePos().setHomeModeEnable(maid, maid.blockPosition());
        maid.setHomeModeEnable(true);
        MaidSoulStateRegistry.record(maid, "maidsoul.action.follow_off", "stay at current position", EventPriority.P1);
        return "home_mode_enabled";
    }

    private static String sit(EntityMaid maid, boolean toSit) {
        boolean isSitting = maid.isMaidInSittingPose();
        if (toSit == isSitting) {
            return toSit ? "already_sitting" : "already_standing";
        }
        maid.setInSittingPose(toSit);
        MaidSoulStateRegistry.record(maid, "maidsoul.action.sit", "sit=" + toSit, EventPriority.P1);
        return toSit ? "sit_enabled" : "sit_disabled";
    }

    private static String setSchedule(EntityMaid maid, String scheduleName) {
        if (scheduleName == null || scheduleName.isBlank()) {
            return "missing_schedule";
        }
        MaidSchedule target;
        try {
            target = MaidSchedule.valueOf(scheduleName.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return "invalid_schedule:" + scheduleName;
        }
        if (maid.getSchedule() == target) {
            return "schedule_unchanged:" + target.name();
        }
        maid.setSchedule(target);
        MaidSoulStateRegistry.record(maid, "maidsoul.action.schedule", target.name(), EventPriority.P1);
        return "schedule_switched:" + target.name();
    }

    /**
     * 只做任务切换，不在这里直接附带战斗目标逻辑。
     * <p>
     * 战斗目标和持续推进统一交给执行层控制器。
     */
    private static String setTask(EntityMaid maid, String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return "missing_task_id";
        }
        Optional<IMaidTask> optionalTask = TaskManager.findTask(new ResourceLocation(taskId));
        if (optionalTask.isEmpty()) {
            return "invalid_task:" + taskId;
        }

        IMaidTask task = optionalTask.get();
        IMaidTask currentTask = maid.getTask();
        if (task != currentTask) {
            maid.setTask(task);
        }
        FunctionCallSwitchResult switchResult = task.onFunctionCallSwitch(maid);
        Activity currentActivity = maid.getScheduleDetail();
        String result = "task_switched:" + task.getUid();
        if (currentActivity != Activity.WORK) {
            result += "|not_work_time=" + maid.getSchedule().name();
        }
        result += "|switch_result=" + switchResult.name();
        MaidSoulStateRegistry.record(maid, "maidsoul.action.task", result, EventPriority.P1);
        return result;
    }

    /**
     * 进入单目标战斗。
     * <p>
     * 流程改为：
     * 1. 先确保切到攻击任务；
     * 2. 再由执行控制器创建单目标执行会话；
     * 3. 后续持续推进不再依赖聊天链路重复触发。
     */
    private static String enterCombat(EntityMaid maid, String preferredTaskId, int targetEntityId) {
        String resolvedTaskId = preferredTaskId;
        if (resolvedTaskId == null || resolvedTaskId.isBlank()) {
            resolvedTaskId = MaidSoulCombatExecutionController.findDefaultAttackTaskId();
        }
        String switchResult = setTask(maid, resolvedTaskId);
        if (!switchResult.startsWith("task_switched")) {
            return switchResult;
        }
        return MaidSoulCombatExecutionController.startSingle(maid, resolvedTaskId, targetEntityId);
    }

    /**
     * 进入群体战斗，使用默认攻击任务。
     */
    private static String enterCombatGroup(EntityMaid maid, String entityTypeId, int targetEntityId) {
        return enterCombatGroup(maid, MaidSoulCombatExecutionController.findDefaultAttackTaskId(), entityTypeId, targetEntityId);
    }

    /**
     * 进入群体战斗，并允许显式指定攻击任务 id。
     */
    private static String enterCombatGroup(EntityMaid maid, String preferredTaskId, String entityTypeId, int targetEntityId) {
        String resolvedTaskId = preferredTaskId;
        if (resolvedTaskId == null || resolvedTaskId.isBlank()) {
            resolvedTaskId = MaidSoulCombatExecutionController.findDefaultAttackTaskId();
        }
        String switchResult = setTask(maid, resolvedTaskId);
        if (!switchResult.startsWith("task_switched")) {
            return switchResult;
        }
        return MaidSoulCombatExecutionController.startGroup(maid, resolvedTaskId, entityTypeId, targetEntityId);
    }
}
