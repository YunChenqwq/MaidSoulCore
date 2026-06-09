package com.maidsoul.brain.affect;

import java.util.Locale;

/**
 * 关系阶段的离散 HMM 状态。
 *
 * <p>连续量 intimacy/conflict 负责细腻变化，阶段负责给回复器一个稳定的关系语境：
 * 例如同样是高兴，初识与甜蜜期的表达边界不同。</p>
 */
public enum RelationshipStage {
    COURTING("courting", "初识试探"),
    SWEET("sweet", "亲近甜蜜"),
    PASSIONATE("passionate", "热烈依恋"),
    STABLE("stable", "稳定陪伴"),
    COLD("cold", "冷淡防备"),
    REPAIRING("repairing", "修复中");

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
        String normalized = id.trim().toLowerCase(Locale.ROOT);
        for (RelationshipStage stage : values()) {
            if (stage.id.equals(normalized) || stage.name().toLowerCase(Locale.ROOT).equals(normalized)) {
                return stage;
            }
        }
        return COURTING;
    }
}
