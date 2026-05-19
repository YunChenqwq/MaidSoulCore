package com.maidsoul.brain.reply.effect;

import java.util.List;

/**
 * MaidSoul 原型机自己的关系反馈模式。
 *
 * <p>maibotdev 的 NEGATIVE/REPAIR 模式保持原样移植；这里单独承载本项目暴露出来的
 * “口癖投诉、不可爱、乱编、不理人”等特有反馈，避免把本地调试语料污染到上游核心规则。</p>
 */
public final class MaidSoulReplyEffectPatterns {
    public static final List<String> LOCAL_NEGATIVE_PATTERNS = List.of(
            "不可爱", "冷淡", "不理我", "真服了", "谁家女仆", "口癖",
            "老哼", "老啧", "啧啧啧", "乱编", "没根据", "一点都", "我生气了"
    );
    public static final List<String> LOCAL_REPAIR_PATTERNS = List.of(
            "你理解错", "不是说我", "我说你", "我不是问", "你自己看着办"
    );

    private MaidSoulReplyEffectPatterns() {
    }

    public static boolean hasLocalNegative(String text) {
        return containsAny(text, LOCAL_NEGATIVE_PATTERNS);
    }

    public static boolean hasLocalRepair(String text) {
        return containsAny(text, LOCAL_REPAIR_PATTERNS);
    }

    private static boolean containsAny(String text, List<String> patterns) {
        String value = text == null ? "" : text;
        for (String pattern : patterns) {
            if (pattern != null && !pattern.isBlank() && value.contains(pattern)) {
                return true;
            }
        }
        return false;
    }
}
