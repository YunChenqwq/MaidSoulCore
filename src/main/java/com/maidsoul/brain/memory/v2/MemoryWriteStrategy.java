package com.maidsoul.brain.memory.v2;

import com.maidsoul.brain.memory.MemoryType;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 长期记忆写入策略。
 *
 * <p>这里故意不用角色 prompt 兜底，而是把记忆拆成几条可维护的“轨道”：
 * 短期上下文、用户画像、关系事件、角色自我记忆、世界事实、情绪债务/修复记录。
 * 规则层先保证可解释和可调试，后续可以在同一接口下替换成 LLM 打分或向量分类。</p>
 */
public final class MemoryWriteStrategy {
    public MemoryWritePlan plan(String role, String text, MemoryType type, int importance, List<String> originalTags) {
        String value = text == null ? "" : text.trim();
        if (value.isBlank()) {
            return MemoryWritePlan.skip("empty_text");
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        Set<String> tags = new LinkedHashSet<>();
        if (originalTags != null) {
            tags.addAll(originalTags.stream().filter(tag -> tag != null && !tag.isBlank()).map(String::trim).toList());
        }

        String layer = "short_context";
        String sourceType = "short_context";
        int salience = Math.max(1, Math.min(10, importance * 2));
        boolean protect = false;
        boolean permanent = false;
        String reason = "default_dialogue";

        if (type == MemoryType.WORLD || hasAny(normalized, "世界", "地点", "任务", "物品", "天气", "房间", "位置")) {
            layer = "world_fact";
            sourceType = "world";
            salience = Math.max(salience, 6);
            tags.add("world_fact");
            reason = "world_fact";
        }
        if (type == MemoryType.PREFERENCE || hasAny(normalized, "喜欢", "不喜欢", "讨厌", "希望", "想要", "偏好", "别再")) {
            layer = "user_profile";
            sourceType = "profile";
            salience = Math.max(salience, 7);
            tags.add("user_profile");
            tags.add(hasAny(normalized, "不喜欢", "讨厌", "别再") ? "boundary" : "preference");
            protect = true;
            reason = "user_profile_preference_or_boundary";
        }
        if (type == MemoryType.PROMISE || hasAny(normalized, "约定", "答应", "记住", "别忘", "以后", "承诺")) {
            layer = "relationship_event";
            sourceType = "relationship";
            salience = Math.max(salience, 8);
            tags.add("promise");
            tags.add("relationship_event");
            protect = true;
            reason = "promise_or_long_term_contract";
        }
        if (type == MemoryType.RELATION || hasAny(normalized, "在一起", "恋人", "喜欢你", "最喜欢", "关系", "告白", "原谅", "和好")) {
            layer = "relationship_event";
            sourceType = "relationship";
            salience = Math.max(salience, 8);
            tags.add("relationship_event");
            protect = true;
            reason = "relationship_milestone";
        }
        if (type == MemoryType.EMOTION || hasAny(normalized, "对不起", "抱歉", "伤心", "受伤", "生气", "骂", "修复", "原谅")) {
            layer = "repair_debt";
            sourceType = "affect";
            salience = Math.max(salience, 7);
            tags.add("repair_debt");
            tags.add("affect_event");
            protect = true;
            reason = "affect_or_repair_event";
        }
        if (isAssistant(role) && hasAny(normalized, "我会记住", "我答应", "我不想", "我喜欢", "我害怕", "我的边界")) {
            layer = "self_memory";
            sourceType = "self";
            salience = Math.max(salience, 7);
            tags.add("self_memory");
            protect = true;
            reason = "assistant_self_commitment";
        }
        if (hasAny(normalized, "不是", "不对", "错了", "记错", "说错", "其实是", "改成")) {
            salience = Math.max(salience, 8);
            tags.add("correction");
            protect = true;
            reason = reason + "+correction";
        }

        if (tags.contains("boundary") || tags.contains("promise") || tags.contains("relationship_event")) {
            permanent = salience >= 9;
        }
        tags.add(layer);
        return new MemoryWritePlan(true, layer, sourceType, salience, new ArrayList<>(tags), protect, permanent, reason);
    }

    private static boolean isAssistant(String role) {
        String value = role == null ? "" : role.toLowerCase(Locale.ROOT);
        return value.contains("assistant") || value.contains("bot") || value.contains("maid");
    }

    private static boolean hasAny(String text, String... needles) {
        for (String needle : needles) {
            if (needle != null && !needle.isBlank() && text.contains(needle.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
