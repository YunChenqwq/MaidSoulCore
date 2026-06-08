package com.maidsoulcore.forge.conversation;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.maidsoulcore.forge.config.MaidSoulCommonConfig;

import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Lightweight guard for visible replies.
 */
public final class ConversationReplyGuardService {
    private static final ConcurrentMap<UUID, String> LAST_REPLY_BY_MAID = new ConcurrentHashMap<>();
    private static final ConcurrentMap<UUID, String> LAST_OPENING_BY_MAID = new ConcurrentHashMap<>();

    private ConversationReplyGuardService() {
    }

    public static String polish(EntityMaid maid, String reply) {
        return polish(maid, reply, "");
    }

    public static String polish(EntityMaid maid, String reply, String latestOwnerMessage) {
        String cleaned = reply == null ? "" : reply.trim();
        if (cleaned.isBlank()) {
            return MaidSoulCommonConfig.CONVERSATION_EMPTY_REPLY_FALLBACK.get();
        }
        cleaned = repairUnclearShortInputReply(cleaned, latestOwnerMessage);
        cleaned = reduceRepeatedOpening(maid, cleaned);
        int maxChars = MaidSoulCommonConfig.CONVERSATION_REPLY_MAX_CHARS.get();
        if (cleaned.length() > maxChars) {
            cleaned = trimToSentenceBoundary(cleaned.substring(0, maxChars));
        }
        if (maid != null && MaidSoulCommonConfig.CONVERSATION_REPEAT_GUARD_ENABLED.get()) {
            String key = normalize(cleaned);
            String previous = LAST_REPLY_BY_MAID.put(maid.getUUID(), key);
            if (previous != null && previous.equals(key)) {
                return MaidSoulCommonConfig.CONVERSATION_REPEAT_REPLY_FALLBACK.get();
            }
        }
        return cleaned;
    }

    private static String repairUnclearShortInputReply(String reply, String latestOwnerMessage) {
        String latest = latestOwnerMessage == null ? "" : latestOwnerMessage.trim();
        if (!isUnclearShortInput(latest)) {
            return reply;
        }
        String normalizedReply = reply.replaceAll("\\s+", "");
        if (normalizedReply.contains("发呆")
                || normalizedReply.contains("不好好说话")
                || normalizedReply.contains("随口问问")
                || normalizedReply.contains("突然发什么")) {
            return "主人刚刚那是什么意思呀？我没听懂啦。";
        }
        return reply;
    }

    private static boolean isUnclearShortInput(String text) {
        if (text.isBlank()) {
            return false;
        }
        String compact = text.replaceAll("\\s+", "");
        if (compact.length() > 2) {
            return false;
        }
        return compact.matches("[A-Za-z0-9?？!！。,.，、]+");
    }

    private static String reduceRepeatedOpening(EntityMaid maid, String reply) {
        if (maid == null || reply.isBlank()) {
            return reply;
        }
        String opening = openingToken(reply);
        if (opening.isBlank()) {
            LAST_OPENING_BY_MAID.remove(maid.getUUID());
            return reply;
        }
        String previous = LAST_OPENING_BY_MAID.put(maid.getUUID(), opening);
        if (!opening.equals(previous)) {
            return reply;
        }
        String stripped = stripOpening(reply, opening).trim();
        return stripped.isBlank() ? reply : stripped;
    }

    private static String openingToken(String reply) {
        String compact = reply.stripLeading();
        String[] openings = {"嗯嗯", "嗯", "唔", "欸", "诶", "啊", "呃"};
        for (String opening : openings) {
            if (compact.startsWith(opening)) {
                return opening;
            }
        }
        return "";
    }

    private static String stripOpening(String reply, String opening) {
        String stripped = reply.stripLeading();
        if (!stripped.startsWith(opening)) {
            return reply;
        }
        stripped = stripped.substring(opening.length()).stripLeading();
        while (!stripped.isBlank() && "，,。.!！…~～".indexOf(stripped.charAt(0)) >= 0) {
            stripped = stripped.substring(1).stripLeading();
        }
        return stripped;
    }

    private static String trimToSentenceBoundary(String text) {
        int best = Math.max(
                Math.max(text.lastIndexOf('。'), text.lastIndexOf('！')),
                Math.max(text.lastIndexOf('？'), text.lastIndexOf('.'))
        );
        if (best >= 24) {
            return text.substring(0, best + 1).trim();
        }
        return text.trim() + "...";
    }

    private static String normalize(String text) {
        return text.toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", "")
                .replace("~", "")
                .replace("～", "")
                .trim();
    }
}
