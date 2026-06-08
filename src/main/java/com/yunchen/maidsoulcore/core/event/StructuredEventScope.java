package com.yunchen.maidsoulcore.core.event;

import java.util.Locale;

public enum StructuredEventScope {
    OWNER_TO_MAID("owner_to_maid", "主人对女仆"),
    MAID_TO_OWNER("maid_to_owner", "女仆对主人"),
    WORLD_TO_MAID("world_to_maid", "世界对女仆"),
    WORLD_TO_OWNER("world_to_owner", "世界对主人"),
    SYSTEM("system", "系统事件"),
    UNKNOWN("unknown", "未知");

    private final String id;
    private final String zhName;

    StructuredEventScope(String id, String zhName) {
        this.id = id;
        this.zhName = zhName;
    }

    public String id() {
        return id;
    }

    public String zhName() {
        return zhName;
    }

    public static StructuredEventScope fromId(String id) {
        if (id == null || id.isBlank()) {
            return UNKNOWN;
        }
        String normalized = id.toLowerCase(Locale.ROOT);
        for (StructuredEventScope scope : values()) {
            if (scope.id.equals(normalized)) {
                return scope;
            }
        }
        return UNKNOWN;
    }
}
