package com.yunchen.maidsoulcore.core.event;

import java.util.Locale;

public enum StructuredEventType {
    INITIATE("initiate", "主动开启互动"),
    AFFECTION("affection", "亲近表达"),
    CARE("care", "照顾关心"),
    APOLOGY("apology", "道歉修复"),
    REPAIR_CHECK("repair_check", "修复确认"),
    FIGHT("fight", "冲突争吵"),
    REJECT("reject", "拒绝疏远"),
    PROMISE("promise", "承诺约定"),
    MEMORY_ANCHOR("memory_anchor", "重要记忆锚点"),
    FATIGUE("fatigue", "疲惫低打扰"),
    BOUNDARY_REQUEST("boundary_request", "边界或安静请求"),
    DANGER("danger", "危险事件"),
    WORLD_CHANGE("world_change", "世界变化"),
    MAID_INTERACT("maid_interact", "女仆交互"),
    OWNER_ATTACK("owner_attack", "主人伤害女仆"),
    MAID_DEATH("maid_death", "女仆死亡"),
    OWNER_MESSAGE("owner_message", "普通主人消息"),
    LONG_MESSAGE("long_message", "主人长消息"),
    LONG_SILENCE("long_silence", "长时间沉默"),
    NEUTRAL_WORLD("neutral_world", "普通事件");

    private final String id;
    private final String zhName;

    StructuredEventType(String id, String zhName) {
        this.id = id;
        this.zhName = zhName;
    }

    public String id() {
        return id;
    }

    public String zhName() {
        return zhName;
    }

    public static StructuredEventType fromId(String id) {
        if (id == null || id.isBlank()) {
            return NEUTRAL_WORLD;
        }
        String normalized = id.toLowerCase(Locale.ROOT);
        for (StructuredEventType type : values()) {
            if (type.id.equals(normalized)) {
                return type;
            }
        }
        return NEUTRAL_WORLD;
    }
}
