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
 * <p>这层只消费结构化输入：MemoryType 和显式 tags。它不读取自然语言内容做语义判断，
 * 因为自然语言分类应该由 planner / 语义事件抽取器 / 人工工具产生。底层存储只负责
 * 按已经声明的结构写入，避免把中文关键词硬编码成“人格/纠错/关系”的事实。</p>
 */
public final class MemoryWriteStrategy {
    public MemoryWritePlan plan(String role, String text, MemoryType type, int importance, List<String> originalTags) {
        String value = text == null ? "" : text.trim();
        if (value.isBlank()) {
            return MemoryWritePlan.skip("empty_text");
        }

        Set<String> tags = normalizeTags(originalTags);
        MemoryType safeType = type == null ? MemoryType.DIALOGUE : type;
        String layer = layerFrom(safeType, tags);
        String sourceType = sourceTypeFrom(layer);
        int salience = salienceFrom(safeType, importance, tags);
        boolean protect = shouldProtect(tags, safeType);
        boolean permanent = shouldPermanent(tags, salience);
        tags.add(layer);

        return new MemoryWritePlan(
                true,
                layer,
                sourceType,
                salience,
                new ArrayList<>(tags),
                protect,
                permanent,
                "structured_type=" + safeType.name().toLowerCase(Locale.ROOT)
        );
    }

    private static Set<String> normalizeTags(List<String> originalTags) {
        Set<String> tags = new LinkedHashSet<>();
        if (originalTags == null) {
            return tags;
        }
        for (String tag : originalTags) {
            if (tag != null && !tag.isBlank()) {
                tags.add(tag.trim().toLowerCase(Locale.ROOT));
            }
        }
        return tags;
    }

    private static String layerFrom(MemoryType type, Set<String> tags) {
        if (tags.contains("world_fact") || type == MemoryType.WORLD) {
            return "world_fact";
        }
        if (tags.contains("self_memory")) {
            return "self_memory";
        }
        if (tags.contains("repair_debt") || tags.contains("affect_event") || type == MemoryType.EMOTION) {
            return "repair_debt";
        }
        if (tags.contains("relationship_event") || tags.contains("promise") || type == MemoryType.RELATION || type == MemoryType.PROMISE) {
            return "relationship_event";
        }
        if (tags.contains("user_profile") || tags.contains("preference") || tags.contains("boundary") || type == MemoryType.PREFERENCE) {
            return "user_profile";
        }
        if (tags.contains("summary") || type == MemoryType.SUMMARY) {
            return "summary";
        }
        return "short_context";
    }

    private static String sourceTypeFrom(String layer) {
        return switch (layer) {
            case "world_fact" -> "world";
            case "self_memory" -> "self";
            case "repair_debt" -> "affect";
            case "relationship_event" -> "relationship";
            case "user_profile" -> "profile";
            case "summary" -> "summary";
            default -> "short_context";
        };
    }

    private static int salienceFrom(MemoryType type, int importance, Set<String> tags) {
        int salience = Math.max(1, Math.min(10, importance * 2));
        if (type == MemoryType.PROMISE || type == MemoryType.RELATION || tags.contains("relationship_event")) {
            salience = Math.max(salience, 8);
        }
        if (type == MemoryType.PREFERENCE || tags.contains("preference") || tags.contains("boundary")) {
            salience = Math.max(salience, 7);
        }
        if (type == MemoryType.EMOTION || tags.contains("repair_debt") || tags.contains("affect_event")) {
            salience = Math.max(salience, 7);
        }
        if (type == MemoryType.WORLD || tags.contains("world_fact")) {
            salience = Math.max(salience, 6);
        }
        return salience;
    }

    private static boolean shouldProtect(Set<String> tags, MemoryType type) {
        return type == MemoryType.PREFERENCE
                || type == MemoryType.PROMISE
                || type == MemoryType.RELATION
                || type == MemoryType.EMOTION
                || tags.contains("boundary")
                || tags.contains("promise")
                || tags.contains("relationship_event")
                || tags.contains("repair_debt")
                || tags.contains("self_memory")
                || tags.contains("correction");
    }

    private static boolean shouldPermanent(Set<String> tags, int salience) {
        return salience >= 9 && (tags.contains("boundary")
                || tags.contains("promise")
                || tags.contains("relationship_event")
                || tags.contains("self_memory"));
    }
}
