package com.maidsoul.brain.affect;

/**
 * 情绪关系更新规则。
 *
 * <p>原型阶段先用确定性规则，保证速度和可解释性。之后如果要上 LLM 评估，也应当先产出
 * event score，再由这里统一落到数值，避免模型直接乱改关系。</p>
 */
public final class AffectEngine {
    public void observeOwnerMessage(AffectProfile profile, String text) {
        String value = normalize(text);
        profile.familiarity = clamp(profile.familiarity + 1);
        profile.mood = clamp(profile.mood + 1);
        profile.tension = clamp(profile.tension - 1);
        profile.curiosity = approach(profile.curiosity, 45, 1);

        if (containsAny(value, "对不起", "抱歉", "不该", "我错了")) {
            profile.hurt = clamp(profile.hurt - 8);
            profile.anger = clamp(profile.anger - 8);
            profile.tension = clamp(profile.tension - 6);
            profile.trust = clamp(profile.trust + 2);
            profile.security = clamp(profile.security + 1);
            profile.curiosity = clamp(profile.curiosity + 2);
        }
        if (containsAny(value, "笨蛋", "傻", "蠢", "废物", "滚")) {
            profile.mood = clamp(profile.mood - 5);
            profile.anger = clamp(profile.anger + 5);
            profile.tension = clamp(profile.tension + 4);
            profile.curiosity = clamp(profile.curiosity - 6);
        }
        if (containsAny(value, "生气", "烦", "难受", "委屈", "不开心")) {
            profile.mood = clamp(profile.mood - 4);
            profile.tension = clamp(profile.tension + 5);
            profile.curiosity = clamp(profile.curiosity + 8);
        }
        if (containsAny(value, "喜欢", "谢谢", "辛苦", "可爱", "陪我")) {
            profile.mood = clamp(profile.mood + 4);
            profile.affection = clamp(profile.affection + 2);
            profile.trust = clamp(profile.trust + 1);
            profile.curiosity = clamp(profile.curiosity + 5);
        }
        if (containsAny(value, "为什么", "怎么", "咋", "如何", "你觉得", "你猜", "想不想", "要不要")) {
            profile.curiosity = clamp(profile.curiosity + 6);
        }
        if (containsAny(value, "嗯", "哦", "。", "...", "……")) {
            profile.curiosity = clamp(profile.curiosity + 3);
        }
    }

    public void observeWorldEvent(AffectProfile profile, String eventType) {
        String type = normalize(eventType);
        if (type.contains("maid.attacked.by_owner") || type.contains("owner_attack")) {
            profile.mood = clamp(profile.mood - 15);
            profile.hurt = clamp(profile.hurt + 18);
            profile.anger = clamp(profile.anger + 12);
            profile.tension = clamp(profile.tension + 18);
            profile.trust = clamp(profile.trust - 5);
            profile.security = clamp(profile.security - 8);
            profile.curiosity = clamp(profile.curiosity - 12);
        } else if (type.contains("maid.attacked") || type.contains("hurt")) {
            profile.mood = clamp(profile.mood - 8);
            profile.hurt = clamp(profile.hurt + 10);
            profile.tension = clamp(profile.tension + 6);
            profile.curiosity = clamp(profile.curiosity - 5);
        }
    }

    public void observeAssistantReply(AffectProfile profile, String text) {
        // 说出来以后情绪会轻微释放，但受伤不会立刻消失。
        profile.anger = clamp(profile.anger - 1);
        profile.tension = clamp(profile.tension - 1);
        profile.curiosity = clamp(profile.curiosity - 1);
    }

    public void recoverAfterQuietTime(AffectProfile profile) {
        profile.mood = approach(profile.mood, 60, 2);
        profile.anger = approach(profile.anger, 0, 2);
        profile.tension = approach(profile.tension, 10, 2);
        profile.hurt = approach(profile.hurt, 0, 1);
        profile.curiosity = approach(profile.curiosity, 45, 1);
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

    private static String normalize(String text) {
        return text == null ? "" : text.trim().toLowerCase();
    }

    private static boolean containsAny(String text, String... needles) {
        for (String needle : needles) {
            if (needle != null && !needle.isBlank() && text.contains(needle.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
}
