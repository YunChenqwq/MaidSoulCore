package com.maidsoulcore.forge.conversation;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.maidsoulcore.forge.config.MaidSoulCommonConfig;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Lightweight per-maid conversation memory.
 * <p>
 * This keeps a clean local history for natural replies. It is deliberately
 * small and in-memory so ordinary chat stays fast.
 */
public final class ConversationMemoryService {
    private static final ConcurrentMap<UUID, MemoryState> STATES = new ConcurrentHashMap<>();

    private ConversationMemoryService() {
    }

    public static void observeOwnerMessage(EntityMaid maid, String text) {
        if (!MaidSoulCommonConfig.CONVERSATION_MEMORY_ENABLED.get()) {
            return;
        }
        if (maid == null || text == null || text.isBlank()) {
            return;
        }
        MemoryState state = stateOf(maid);
        String cleaned = clean(text);
        if (cleaned.isBlank()) {
            return;
        }
        addLine(state, "主人: " + shortText(cleaned, MaidSoulCommonConfig.CONVERSATION_MEMORY_LINE_MAX_CHARS.get()));
        extractOwnerNotes(state, cleaned);
        extractRelationshipNotes(state, cleaned);
        state.lastUpdatedMillis = System.currentTimeMillis();
    }

    public static void observeAssistantReply(EntityMaid maid, String text) {
        if (!MaidSoulCommonConfig.CONVERSATION_MEMORY_ENABLED.get()) {
            return;
        }
        if (maid == null || text == null || text.isBlank()) {
            return;
        }
        MemoryState state = stateOf(maid);
        String cleaned = clean(text);
        if (cleaned.isBlank()) {
            return;
        }
        addLine(state, maid.getName().getString() + ": " + shortText(cleaned, MaidSoulCommonConfig.CONVERSATION_MEMORY_LINE_MAX_CHARS.get()));
        state.lastUpdatedMillis = System.currentTimeMillis();
    }

    public static List<String> recentLines(EntityMaid maid, int limit) {
        if (!MaidSoulCommonConfig.CONVERSATION_MEMORY_ENABLED.get()) {
            return List.of();
        }
        MemoryState state = STATES.get(maid.getUUID());
        if (state == null || limit <= 0) {
            return List.of();
        }
        ArrayList<String> result = new ArrayList<>();
        int count = 0;
        for (String line : state.lines) {
            result.add(line);
            count++;
            if (count >= limit) {
                break;
            }
        }
        java.util.Collections.reverse(result);
        return List.copyOf(result);
    }

    public static String notesForPrompt(EntityMaid maid) {
        if (!MaidSoulCommonConfig.CONVERSATION_MEMORY_ENABLED.get()) {
            return "none";
        }
        MemoryState state = STATES.get(maid.getUUID());
        if (state == null || state.ownerNotes.isEmpty()) {
            return "none";
        }
        return String.join("\n", state.ownerNotes);
    }

    public static String historyForPrompt(EntityMaid maid, List<String> fallbackHistory, int limit) {
        List<String> local = recentLines(maid, limit);
        if (!local.isEmpty()) {
            return String.join("\n", local);
        }
        if (fallbackHistory == null || fallbackHistory.isEmpty()) {
            return "(无)";
        }
        return String.join("\n", fallbackHistory);
    }

    private static MemoryState stateOf(EntityMaid maid) {
        return STATES.computeIfAbsent(maid.getUUID(), id -> new MemoryState());
    }

    private static void addLine(MemoryState state, String line) {
        state.lines.removeIf(existing -> existing.equalsIgnoreCase(line));
        state.lines.addFirst(line);
        while (state.lines.size() > MaidSoulCommonConfig.CONVERSATION_MEMORY_MAX_LINES.get()) {
            state.lines.removeLast();
        }
    }

    private static void extractOwnerNotes(MemoryState state, String text) {
        String normalized = text.toLowerCase(Locale.ROOT);
        if (containsAny(normalized, MaidSoulCommonConfig.CONVERSATION_OWNER_NOTE_TRIGGERS.get())) {
            addOwnerNote(state, "- " + shortText(text, MaidSoulCommonConfig.CONVERSATION_MEMORY_NOTE_MAX_CHARS.get()));
        }
    }

    private static void extractRelationshipNotes(MemoryState state, String text) {
        String normalized = text.toLowerCase(Locale.ROOT);
        if (!containsAny(normalized, List.of("结婚", "婚礼", "婚约", "老婆", "妻子", "嫁给", "娶你", "伴侣", "恋人"))) {
            return;
        }
        addOwnerNote(state, "- 关系事实: 主人表达过婚恋/伴侣关系相关的话题: "
                + shortText(text, MaidSoulCommonConfig.CONVERSATION_MEMORY_NOTE_MAX_CHARS.get()));
    }

    private static void addOwnerNote(MemoryState state, String note) {
        state.ownerNotes.removeIf(existing -> existing.equalsIgnoreCase(note));
        state.ownerNotes.add(note);
        while (state.ownerNotes.size() > MaidSoulCommonConfig.CONVERSATION_MEMORY_MAX_OWNER_NOTES.get()) {
            String first = state.ownerNotes.iterator().next();
            state.ownerNotes.remove(first);
        }
    }

    private static boolean containsAny(String text, List<? extends String> keys) {
        for (String key : keys) {
            if (key != null && !key.isBlank() && text.contains(key.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static String clean(String text) {
        return text.replace('\r', ' ')
                .replace('\n', ' ')
                .replace('\t', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String shortText(String text, int max) {
        String cleaned = clean(text);
        return cleaned.length() <= max ? cleaned : cleaned.substring(0, max) + "...";
    }

    private static final class MemoryState {
        private final Deque<String> lines = new ArrayDeque<>();
        private final Set<String> ownerNotes = new LinkedHashSet<>();
        private long lastUpdatedMillis;
    }
}
