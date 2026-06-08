package com.maidsoulcore.forge.conversation;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.maidsoulcore.forge.config.MaidSoulCommonConfig;
import com.maidsoulcore.forge.memory.MaidSoulLifeMemoryService;
import com.maidsoulcore.forge.state.MaidSoulStateRegistry;
import com.maidsoulcore.sim.SimulationOpenAiChatClient;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 单女仆会话时间线。
 *
 * <p>这条时间线同时保存可见对话和脑内参考，但两者的用途不同：
 * 可见对话会按 user/assistant 进入模型上下文；reference 只会被上下文选择器
 * 汇总到私有参考块，不能被模型当成玩家刚说的话。</p>
 */
public final class ConversationJournalService {
    private static final int MAX_RETAINED_ENTRIES = 240;
    private static final long DUPLICATE_VISIBLE_WINDOW_MILLIS = 5_000L;
    private static final ConcurrentMap<UUID, JournalState> STATES = new ConcurrentHashMap<>();

    private ConversationJournalService() {
    }

    public static void appendOwnerMessage(EntityMaid maid, String text) {
        appendVisibleMessage(maid, RuntimeMessageType.OWNER_VISIBLE, "user", text, "owner");
    }

    public static void appendMaidMessage(EntityMaid maid, String text) {
        appendVisibleMessage(maid, RuntimeMessageType.MAID_VISIBLE, "assistant", text, "maid");
    }

    public static void appendEvent(EntityMaid maid, String eventType, String detail) {
        appendReference(maid, RuntimeMessageType.REFERENCE_EVENT, "event", eventType, detail);
    }

    public static void appendVisionReference(EntityMaid maid, String eventType, String detail) {
        appendReference(maid, RuntimeMessageType.REFERENCE_VISION, "vision", eventType, detail);
    }

    public static void appendRuntimeReference(EntityMaid maid, String eventType, String detail) {
        appendReference(maid, RuntimeMessageType.REFERENCE_RUNTIME, "runtime", eventType, detail);
    }

    public static void appendEmotionReference(EntityMaid maid, String eventType, String detail) {
        appendReference(maid, RuntimeMessageType.REFERENCE_EMOTION, "emotion", eventType, detail);
    }

    /**
     * 兼容旧调用：新代码优先使用 ConversationContextSelector。
     */
    public static List<SimulationOpenAiChatClient.ChatMessage> selectChatMessages(EntityMaid maid, String referencePrompt) {
        return ConversationContextSelector.selectForReply(maid, referencePrompt, List.of());
    }

    public static List<RuntimeMessage> selectVisibleDialogue(EntityMaid maid, int maxCountedMessages) {
        JournalState state = stateOrNull(maid);
        if (state == null) {
            return List.of();
        }
        int maxCount = Math.max(1, maxCountedMessages);
        ArrayList<RuntimeMessage> selected = new ArrayList<>();
        synchronized (state) {
            int counted = 0;
            for (RuntimeMessage entry : state.entries) {
                if (!entry.visibleDialogue()) {
                    continue;
                }
                selected.add(entry);
                if (entry.countInContext()) {
                    counted++;
                    if (counted >= maxCount) {
                        break;
                    }
                }
            }
        }
        java.util.Collections.reverse(selected);
        return List.copyOf(selected);
    }

    public static List<RuntimeMessage> selectRecentReferences(EntityMaid maid, int limit) {
        JournalState state = stateOrNull(maid);
        if (state == null) {
            return List.of();
        }
        int max = Math.max(1, limit);
        ArrayList<RuntimeMessage> selected = new ArrayList<>();
        synchronized (state) {
            for (RuntimeMessage entry : state.entries) {
                if (entry.visibleDialogue()) {
                    continue;
                }
                selected.add(entry);
                if (selected.size() >= max) {
                    break;
                }
            }
        }
        java.util.Collections.reverse(selected);
        return List.copyOf(selected);
    }

    public static String debugTail(EntityMaid maid, int limit) {
        JournalState state = stateOrNull(maid);
        if (state == null) {
            return "none";
        }
        ArrayList<String> lines = new ArrayList<>();
        synchronized (state) {
            int count = 0;
            for (RuntimeMessage entry : state.entries) {
                lines.add(entry.type() + "/" + entry.source() + ": " + entry.content());
                count++;
                if (count >= limit) {
                    break;
                }
            }
        }
        return lines.isEmpty() ? "none" : String.join(" || ", lines);
    }

    private static void appendVisibleMessage(EntityMaid maid,
                                             RuntimeMessageType type,
                                             String role,
                                             String text,
                                             String source) {
        if (maid == null || text == null || text.isBlank()) {
            return;
        }
        String cleaned = shortText(clean(text), MaidSoulCommonConfig.CONVERSATION_MEMORY_LINE_MAX_CHARS.get());
        if (cleaned.isBlank()) {
            return;
        }
        append(maid, new RuntimeMessage(type, role, cleaned, source, "", true, System.currentTimeMillis()));
    }

    private static void appendReference(EntityMaid maid,
                                        RuntimeMessageType type,
                                        String source,
                                        String eventType,
                                        String detail) {
        if (maid == null || type == null) {
            return;
        }
        String content = shortText(clean(detail), 220);
        String normalizedEvent = clean(eventType);
        if (normalizedEvent.isBlank() && content.isBlank()) {
            return;
        }
        append(maid, new RuntimeMessage(type, "system", content, source, normalizedEvent, false, System.currentTimeMillis()));
    }

    private static void append(EntityMaid maid, RuntimeMessage entry) {
        JournalState state = STATES.computeIfAbsent(maid.getUUID(), id -> new JournalState());
        synchronized (state) {
            if (isDuplicateVisibleTurn(state, entry)) {
                return;
            }
            state.entries.addFirst(entry);
            while (state.entries.size() > MAX_RETAINED_ENTRIES) {
                state.entries.removeLast();
            }
        }
        MaidSoulLifeMemoryService.recordJournalEntry(
                maid,
                entry.role(),
                entry.source(),
                entry.eventType(),
                entry.content(),
                entry.countInContext()
        );
        MaidSoulStateRegistry.echoTraceToOwnerChat(maid, "maidsoul.journal.append", entry.type() + "/" + entry.source());
    }

    private static boolean isDuplicateVisibleTurn(JournalState state, RuntimeMessage entry) {
        if (!entry.visibleDialogue()) {
            return false;
        }
        RuntimeMessage latest = state.entries.peekFirst();
        return latest != null
                && latest.type() == entry.type()
                && latest.content().equals(entry.content())
                && entry.createdMillis() - latest.createdMillis() < DUPLICATE_VISIBLE_WINDOW_MILLIS;
    }

    private static JournalState stateOrNull(EntityMaid maid) {
        return maid == null ? null : STATES.get(maid.getUUID());
    }

    private static String clean(String text) {
        return text == null ? "" : text.replace('\r', ' ')
                .replace('\n', ' ')
                .replace('\t', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String shortText(String text, int max) {
        String cleaned = clean(text);
        int safeMax = Math.max(20, max);
        return cleaned.length() <= safeMax ? cleaned : cleaned.substring(0, safeMax) + "...";
    }

    private static final class JournalState {
        private final Deque<RuntimeMessage> entries = new ArrayDeque<>();
    }
}
