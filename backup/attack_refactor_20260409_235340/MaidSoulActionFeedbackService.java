package com.maidsoulcore.forge.service;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.maidsoulcore.event.EventPriority;
import com.maidsoulcore.forge.state.MaidSoulStateRegistry;

/**
 * 动作执行反馈服务。
 * <p>
 * Planner 只负责决定“做什么”，执行器负责真正切换状态，
 * 而这层负责把执行结果翻译成主人可感知的主动反馈事件。
 */
public final class MaidSoulActionFeedbackService {
    private MaidSoulActionFeedbackService() {
    }

    /**
     * 根据动作执行结果，向主动陪伴链路广播一条更适合说人话的事件。
     */
    public static void onActionExecuted(EntityMaid maid, String actionType, String actionResult) {
        if (actionType == null || actionType.isBlank() || actionResult == null || actionResult.isBlank()) {
            return;
        }
        String normalizedAction = actionType.toUpperCase();
        if ("no_action".equals(actionResult) || actionResult.startsWith("unknown_action")) {
            return;
        }

        String eventType = switch (normalizedAction) {
            case "FOLLOW_ON" -> "maid.action.follow";
            case "FOLLOW_OFF" -> "maid.action.home";
            case "SIT_ON", "SIT_OFF" -> "maid.action.sit";
            case "SET_SCHEDULE" -> "maid.action.schedule";
            case "SET_TASK", "ENTER_COMBAT", "ENTER_COMBAT_GROUP" -> "maid.action.task";
            default -> "maid.action.executed";
        };

        EventPriority priority = actionResult.contains("target_missing")
                || actionResult.contains("invalid_")
                || actionResult.contains("missing_")
                || actionResult.contains("not_allowed")
                || actionResult.contains("switch_result=FAIL")
                ? EventPriority.P0
                : EventPriority.P1;

        MaidSoulStateRegistry.record(maid, eventType, actionResult, priority);
        MaidSoulCompanionService.onExplicitEvent(maid, eventType, actionResult, priority);
    }
}
