package com.maidsoulcore.forge.service;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.maidsoulcore.event.EventPriority;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Durable but lightweight understandings formed from repeated experience.
 * <p>
 * This is the small in-memory version of "episode -> understanding": it does
 * not summarize every line, but it lets prompt assembly see stable patterns.
 */
public final class MaidSoulUnderstandingService {
    private static final ConcurrentMap<UUID, UnderstandingState> STATES = new ConcurrentHashMap<>();

    private MaidSoulUnderstandingService() {
    }

    public static void observe(EntityMaid maid,
                               String eventType,
                               String detail,
                               EventPriority priority,
                               MaidSoulCognitionService.CognitiveFrame frame,
                               MaidSoulMindLoopService.MindDecision decision) {
        if (maid == null || eventType == null || eventType.isBlank()) {
            return;
        }
        UnderstandingState state = STATES.computeIfAbsent(maid.getUUID(), id -> new UnderstandingState());
        synchronized (state) {
            long now = System.currentTimeMillis();
            if (eventType.startsWith("maid.attacked.by_owner")) {
                state.upsert("owner_boundary",
                        "主人刚才伤害了我，这不是普通话题；在被安抚前，我会更需要解释、道歉或安全感。",
                        5,
                        now);
            } else if (eventType.startsWith("maid.attacked")) {
                state.upsert("danger_safety",
                        "我最近遇到过危险或受伤；回应时要先考虑安全和疼痛，而不是闲聊。",
                        4,
                        now);
            } else if (eventType.startsWith("owner.view.risk_mob") || eventType.startsWith("world.hostile")) {
                state.upsert("danger_safety",
                        "主人附近出现危险时，我应该简短提醒并关注安全。",
                        4,
                        now);
            } else if (eventType.startsWith("maid.idle.")) {
                state.upsert("companionship_pacing",
                        "主人长时间没说话时，我可以轻轻搭话；如果没有回应，就减少打扰。",
                        2,
                        now);
            } else if (eventType.startsWith("owner.view.")) {
                state.upsert("shared_attention",
                        "我会把主人的视角当作共同注意力，只在有新意或有用时开口。",
                        2,
                        now);
            } else if (eventType.contains("failed") || eventType.contains("missing") || eventType.contains("not_allowed")) {
                state.upsert("action_limits",
                        "行动失败或条件不足时，我应该说明缺什么，而不是假装完成。",
                        3,
                        now);
            }

            if (frame != null && frame.repeatCount() > 1) {
                state.upsert("repeat_control",
                        "同一主题在短时间内反复出现时，我应该延续或沉默，不要换个说法重复播报。",
                        4,
                        now);
            }
            if (decision != null && decision.action() == MaidSoulMindLoopService.MindAction.SILENCE) {
                state.upsert("inner_silence",
                        "不是每次注意到事情都要说出口；沉默也可以是自然陪伴的一部分。",
                        2,
                        now);
            }
        }
    }

    public static void observeOwnerComfort(EntityMaid maid) {
        if (maid == null) {
            return;
        }
        UnderstandingState state = STATES.computeIfAbsent(maid.getUUID(), id -> new UnderstandingState());
        synchronized (state) {
            state.upsert("owner_repair",
                    "主人愿意安抚或道歉时，我可以逐步放松，但不用立刻假装什么都没发生。",
                    4,
                    System.currentTimeMillis());
        }
    }

    public static void observeOwnerMessage(EntityMaid maid, String message) {
        if (maid == null || message == null || message.isBlank()) {
            return;
        }
        String normalized = message.toLowerCase(java.util.Locale.ROOT);
        UnderstandingState state = STATES.computeIfAbsent(maid.getUUID(), id -> new UnderstandingState());
        synchronized (state) {
            long now = System.currentTimeMillis();
            if (containsAny(normalized, "结婚", "婚礼", "婚约", "老婆", "妻子", "嫁给", "娶你", "伴侣", "恋人")) {
                state.upsert("owner_relationship_commitment",
                        "主人刚表达过婚恋或伴侣关系相关的话题；后续对话要把它当作重要关系上下文，不要转头当没发生。",
                        5,
                        now);
            }
        }
    }

    public static String promptBlock(EntityMaid maid) {
        if (maid == null) {
            return "none";
        }
        UnderstandingState state = STATES.get(maid.getUUID());
        if (state == null || state.items.isEmpty()) {
            return "none";
        }
        synchronized (state) {
            StringBuilder builder = new StringBuilder();
            state.items.values().stream()
                    .sorted((left, right) -> Integer.compare(right.importance, left.importance))
                    .limit(5)
                    .forEach(item -> builder.append("- ")
                            .append(item.key)
                            .append(": ")
                            .append(item.content)
                            .append(" (importance=")
                            .append(item.importance)
                            .append(")\n"));
            String result = builder.toString().trim();
            return result.isBlank() ? "none" : result;
        }
    }

    private static final class UnderstandingState {
        private final Map<String, UnderstandingItem> items = new LinkedHashMap<>();

        private void upsert(String key, String content, int importance, long now) {
            UnderstandingItem existing = items.get(key);
            int nextImportance = existing == null ? importance : Math.max(existing.importance, importance);
            items.put(key, new UnderstandingItem(key, content, nextImportance, now));
            while (items.size() > 12) {
                String first = items.keySet().iterator().next();
                items.remove(first);
            }
        }
    }

    private static boolean containsAny(String text, String... needles) {
        for (String needle : needles) {
            if (text.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private record UnderstandingItem(
            String key,
            String content,
            int importance,
            long updatedMillis
    ) {
    }
}
