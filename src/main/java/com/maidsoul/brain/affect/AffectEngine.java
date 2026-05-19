package com.maidsoul.brain.affect;

/**
 * 情绪关系积分器。
 *
 * <p>这里不做自然语言 pattern 判断，只根据上游已经判定好的结构化事件调整状态。
 * 这样情绪层不会和中文词表、某个角色口吻或临时 prompt 补丁绑死。</p>
 */
public final class AffectEngine {
    public void observeOwnerMessage(AffectProfile profile, String text) {
        apply(profile, AffectEvent.of(AffectEventKind.OWNER_MESSAGE, 35, "chat", ""));
    }

    public void observeWorldEvent(AffectProfile profile, String eventType) {
        String type = eventType == null ? "" : eventType.trim().toLowerCase();
        if (type.contains("maid.attacked.by_owner") || type.contains("owner_attack")) {
            apply(profile, AffectEvent.of(AffectEventKind.MAID_HURT_BY_OWNER, 75, "world", eventType));
        } else if (type.contains("maid.attacked") || type.contains("hurt")) {
            apply(profile, AffectEvent.of(AffectEventKind.MAID_HURT_BY_WORLD, 55, "world", eventType));
        }
    }

    public void observeAssistantReply(AffectProfile profile, String text) {
        apply(profile, AffectEvent.of(AffectEventKind.ASSISTANT_REPLY, 30, "chat", ""));
    }

    public void recoverAfterQuietTime(AffectProfile profile) {
        apply(profile, AffectEvent.of(AffectEventKind.QUIET_RECOVERY, 30, "runtime", ""));
    }

    public void apply(AffectProfile profile, AffectEvent event) {
        if (profile == null || event == null || event.kind() == null) {
            return;
        }
        int scale = Math.max(1, (int) Math.ceil(event.intensity() / 25.0));
        switch (event.kind()) {
            case OWNER_MESSAGE -> {
                profile.familiarity = clamp(profile.familiarity + 1);
                profile.mood = clamp(profile.mood + 1);
                profile.tension = clamp(profile.tension - 1);
                // 好奇心是短期主动欲望，不是长期好感；普通来信会把它拉回中位，避免长期贴顶。
                profile.curiosity = approach(profile.curiosity, 45, 3);
            }
            case ASSISTANT_REPLY -> {
                // 角色把话说出口以后会轻微释放紧张和主动欲望，但受伤不会立刻消失。
                profile.anger = clamp(profile.anger - 1);
                profile.tension = clamp(profile.tension - 1);
                profile.curiosity = clamp(profile.curiosity - 3);
            }
            case OWNER_APOLOGY -> {
                profile.hurt = clamp(profile.hurt - 4 * scale);
                profile.anger = clamp(profile.anger - 4 * scale);
                profile.tension = clamp(profile.tension - 3 * scale);
                profile.trust = clamp(profile.trust + scale);
                profile.security = clamp(profile.security + scale);
                profile.curiosity = clamp(profile.curiosity + scale);
            }
            case OWNER_ATTACK -> {
                profile.mood = clamp(profile.mood - 3 * scale);
                profile.anger = clamp(profile.anger + 3 * scale);
                profile.tension = clamp(profile.tension + 2 * scale);
                profile.security = clamp(profile.security - scale);
                profile.curiosity = clamp(profile.curiosity - 3 * scale);
            }
            case OWNER_DISTRESS -> {
                profile.mood = clamp(profile.mood - 2 * scale);
                profile.tension = clamp(profile.tension + 2 * scale);
                profile.curiosity = clamp(profile.curiosity + 2 * scale);
            }
            case OWNER_AFFECTION -> {
                profile.mood = clamp(profile.mood + 2 * scale);
                profile.affection = clamp(profile.affection + scale);
                profile.trust = clamp(profile.trust + scale);
                profile.curiosity = clamp(profile.curiosity + scale);
            }
            case OWNER_QUESTION -> profile.curiosity = clamp(profile.curiosity + 2 * scale);
            case OWNER_SHORT_FEEDBACK -> profile.curiosity = clamp(profile.curiosity + scale);
            case MAID_HURT_BY_OWNER -> {
                profile.mood = clamp(profile.mood - 5 * scale);
                profile.hurt = clamp(profile.hurt + 6 * scale);
                profile.anger = clamp(profile.anger + 4 * scale);
                profile.tension = clamp(profile.tension + 6 * scale);
                profile.trust = clamp(profile.trust - 2 * scale);
                profile.security = clamp(profile.security - 3 * scale);
                profile.curiosity = clamp(profile.curiosity - 4 * scale);
            }
            case MAID_HURT_BY_WORLD -> {
                profile.mood = clamp(profile.mood - 3 * scale);
                profile.hurt = clamp(profile.hurt + 4 * scale);
                profile.tension = clamp(profile.tension + 2 * scale);
                profile.curiosity = clamp(profile.curiosity - 2 * scale);
            }
            case QUIET_RECOVERY -> {
                profile.mood = approach(profile.mood, 60, 2);
                profile.anger = approach(profile.anger, 0, 2);
                profile.tension = approach(profile.tension, 10, 2);
                profile.hurt = approach(profile.hurt, 0, 1);
                profile.curiosity = approach(profile.curiosity, 45, 1);
            }
        }
    }

    public void syncBaseAffection(AffectProfile profile, int affection) {
        profile.affection = clamp(affection);
        profile.familiarity = clamp(20 + affection / 3);
    }

    private static int approach(int value, int target, int step) {
        if (value < target) {
            return Math.min(target, value + step);
        }
        if (value > target) {
            return Math.max(target, value - step);
        }
        return value;
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }

}
