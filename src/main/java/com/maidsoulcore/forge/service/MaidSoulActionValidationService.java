package com.maidsoulcore.forge.service;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.maidsoulcore.forge.plan.MaidSoulPlan;
import com.maidsoulcore.forge.plan.MaidSoulPlanStep;

import java.util.Locale;

/**
 * Centralized pre-dispatch validation for action steps.
 */
public final class MaidSoulActionValidationService {
    private MaidSoulActionValidationService() {
    }

    public static ValidationResult validate(EntityMaid maid, MaidSoulPlan plan, MaidSoulPlanStep step) {
        if (maid == null || plan == null || step == null) {
            return ValidationResult.reject("step_validation_failed:null_input");
        }

        String actionType = normalize(step.actionType());
        if (actionType.isBlank() || "NONE".equals(actionType)) {
            return ValidationResult.reject("step_validation_failed:empty_action");
        }

        MaidSoulActionMetadataService.ActionMetadata metadata = MaidSoulActionMetadataService.forAction(actionType);
        if (metadata == null) {
            return ValidationResult.reject("step_validation_failed:unknown_action:" + actionType);
        }

        String actionValue = step.actionValue() == null ? "" : step.actionValue().trim();
        if (metadata.requiresActionValue() && actionValue.isBlank()) {
            return ValidationResult.reject("step_validation_failed:missing_action_value:" + actionType);
        }
        if (metadata.requiresTargetEntity() && step.targetEntityId() < 0) {
            return ValidationResult.reject("step_validation_failed:missing_target:" + actionType);
        }

        if ("SET_SCHEDULE".equals(actionType) && !isScheduleValue(actionValue)) {
            return ValidationResult.reject("step_validation_failed:invalid_schedule:" + actionValue);
        }

        if ("ENTER_COMBAT_GROUP".equals(actionType)) {
            boolean hasFilterOrTarget = !actionValue.isBlank() || step.targetEntityId() >= 0;
            if (!hasFilterOrTarget) {
                return ValidationResult.reject("step_validation_failed:group_filter_missing");
            }
            if (!actionValue.isBlank() && !MaidSoulCombatExecutionController.isValidGroupFilterSpec(actionValue)) {
                return ValidationResult.reject("step_validation_failed:invalid_group_filter:" + actionValue);
            }
        }

        return ValidationResult.accept();
    }

    private static String normalize(String actionType) {
        return actionType == null ? "" : actionType.trim().toUpperCase(Locale.ROOT);
    }

    private static boolean isScheduleValue(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return "DAY".equals(normalized) || "NIGHT".equals(normalized) || "ALL".equals(normalized);
    }

    public record ValidationResult(boolean valid, String reason) {
        public static ValidationResult accept() {
            return new ValidationResult(true, "ok");
        }

        public static ValidationResult reject(String reason) {
            return new ValidationResult(false, reason == null || reason.isBlank() ? "invalid" : reason);
        }
    }
}

