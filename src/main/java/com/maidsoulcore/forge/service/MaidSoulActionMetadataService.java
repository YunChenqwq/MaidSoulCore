package com.maidsoulcore.forge.service;

import java.util.Locale;
import java.util.Map;

/**
 * Action metadata registry for execution layer validation and scheduling.
 */
public final class MaidSoulActionMetadataService {
    private static final Map<String, ActionMetadata> METADATA = Map.ofEntries(
            Map.entry("FOLLOW_ON", new ActionMetadata("FOLLOW_ON", true, false, false, false, false)),
            Map.entry("FOLLOW_OFF", new ActionMetadata("FOLLOW_OFF", true, false, false, false, false)),
            Map.entry("SIT_ON", new ActionMetadata("SIT_ON", true, false, false, false, false)),
            Map.entry("SIT_OFF", new ActionMetadata("SIT_OFF", true, false, false, false, false)),
            Map.entry("SET_SCHEDULE", new ActionMetadata("SET_SCHEDULE", true, false, true, false, false)),
            Map.entry("SET_TASK", new ActionMetadata("SET_TASK", true, false, true, false, false)),
            Map.entry("ENTER_COMBAT", new ActionMetadata("ENTER_COMBAT", false, true, false, true, false)),
            Map.entry("ENTER_COMBAT_GROUP", new ActionMetadata("ENTER_COMBAT_GROUP", false, true, false, false, true))
    );

    private MaidSoulActionMetadataService() {
    }

    public static ActionMetadata forAction(String actionType) {
        if (actionType == null || actionType.isBlank()) {
            return null;
        }
        return METADATA.get(actionType.trim().toUpperCase(Locale.ROOT));
    }

    public record ActionMetadata(
            String actionType,
            boolean immediate,
            boolean combat,
            boolean requiresActionValue,
            boolean requiresTargetEntity,
            boolean supportsGroupFilter
    ) {
    }
}

