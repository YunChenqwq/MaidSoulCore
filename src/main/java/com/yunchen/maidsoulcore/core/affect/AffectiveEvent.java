package com.yunchen.maidsoulcore.core.affect;

import java.util.Locale;

/**
 * 心情系统只吃结构事件，不在这里做关键词硬识别。
 *
 * <p>玩家文本如果要识别成 affection/apology/fight，应该由上游 classifier、
 * planner tool 或明确的游戏事件给出，而不是在 AffectEngine 里写一堆 contains。</p>
 */
public enum AffectiveEvent {
    OWNER_MESSAGE("owner_message", "主人发来消息"),
    LONG_MESSAGE("long_message", "主人发来较长消息"),
    INITIATE("initiate", "主人主动联系"),
    AFFECTION("affection", "亲昵表达"),
    APOLOGY("apology", "道歉修复"),
    REPAIR_CHECK("repair_check", "修复确认"),
    REJECT("reject", "拒绝疏远"),
    FIGHT("fight", "冲突争吵"),
    PROMISE("promise", "承诺约定"),
    MEMORY_ANCHOR("memory_anchor", "重要记忆锚点"),
    FATIGUE("fatigue", "疲惫低打扰"),
    BOUNDARY_REQUEST("boundary_request", "边界或安静请求"),
    LONG_SILENCE("long_silence", "长时间沉默"),
    MAID_INTERACT("maid_interact", "主人交互"),
    CARE("care", "照顾/喂食"),
    DANGER("danger", "外部危险"),
    WORLD_CHANGE("world_change", "世界变化"),
    OWNER_ATTACK("owner_attack", "主人伤害女仆"),
    MAID_DEATH("maid_death", "女仆死亡"),
    NEUTRAL_WORLD("neutral_world", "普通世界事件");

    private final String id;
    private final String zhName;

    AffectiveEvent(String id, String zhName) {
        this.id = id;
        this.zhName = zhName;
    }

    public String id() {
        return id;
    }

    public String zhName() {
        return zhName;
    }

    public static AffectiveEvent fromId(String id) {
        if (id == null || id.isBlank()) {
            return NEUTRAL_WORLD;
        }
        String normalized = id.toLowerCase(Locale.ROOT);
        for (AffectiveEvent event : values()) {
            if (event.id.equals(normalized)) {
                return event;
            }
        }
        return NEUTRAL_WORLD;
    }
}
