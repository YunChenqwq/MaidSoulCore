package com.yunchen.maidsoulcore.core.affect;

import java.util.Locale;

public enum RelationshipStage {
    COURTING("courting", "初识试探"),
    SWEET("sweet", "甜蜜亲近"),
    PASSIONATE("passionate", "热烈依恋"),
    STABLE("stable", "稳定陪伴"),
    COLD("cold", "冷淡受伤"),
    REPAIRING("repairing", "修复关系");

    private final String id;
    private final String zhName;

    RelationshipStage(String id, String zhName) {
        this.id = id;
        this.zhName = zhName;
    }

    public String id() {
        return id;
    }

    public String zhName() {
        return zhName;
    }

    public static RelationshipStage fromId(String id) {
        if (id == null || id.isBlank()) {
            return COURTING;
        }
        String normalized = id.toLowerCase(Locale.ROOT);
        for (RelationshipStage stage : values()) {
            if (stage.id.equals(normalized)) {
                return stage;
            }
        }
        return COURTING;
    }
}
