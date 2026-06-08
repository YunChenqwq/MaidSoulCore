package com.maidsoulcore.forge.conversation;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.maidsoulcore.forge.config.MaidSoulCommonConfig;
import com.maidsoulcore.forge.state.MaidSoulStateRegistry;

import java.util.Locale;

/**
 * 回复效果本地评估。
 *
 * <p>这里只做“输出质量”检查，例如空回复、明显复读、只说我听着却不推进。
 * 不再根据关键词判断玩家这句话属于提问/道歉/纠正，避免本地分类器抢模型的理解权。</p>
 */
public final class ConversationReplyEffectService {
    private ConversationReplyEffectService() {
    }

    public static EffectDecision evaluate(EntityMaid maid,
                                          String latestOwnerMessage,
                                          ConversationFlowService.TurnFrame turnFrame,
                                          String candidateReply) {
        String reply = candidateReply == null ? "" : candidateReply.trim();
        if (reply.isBlank()) {
            return EffectDecision.retry("empty_reply");
        }
        ConversationFlowService.RepeatDecision repeat = ConversationFlowService.checkSemanticRepeat(maid, reply);
        if (!repeat.accepted()) {
            return EffectDecision.retry(repeat.reason());
        }
        String compactReply = normalize(reply);
        String latest = normalize(latestOwnerMessage);
        if (isHollowReply(compactReply) && latest.length() >= 3) {
            return EffectDecision.retry("hollow_reply_without_progress");
        }
        if (looksLikeQuestionMark(latestOwnerMessage) && looksLikeAvoidingAnswer(compactReply)) {
            return EffectDecision.retry("question_like_input_not_advanced");
        }
        if (turnFrame != null && turnFrame.topicTurn() >= 3 && containsAny(compactReply, "是不是", "没听懂", "什么意思")) {
            return EffectDecision.retry("late_topic_still_clarifying");
        }
        return EffectDecision.accept("ok");
    }

    public static String buildRetryInstruction(EntityMaid maid,
                                               String latestOwnerMessage,
                                               ConversationFlowService.TurnFrame turnFrame,
                                               String rejectedReply,
                                               String reason) {
        String flowInstruction = ConversationFlowService.retryInstruction(maid, rejectedReply, reason);
        return flowInstruction + "\n"
                + "Owner latest input: " + (latestOwnerMessage == null ? "" : latestOwnerMessage) + "\n"
                + "Extra quality rule: rewrite as a real next conversational move. Do not only say you are listening. "
                + "If the owner asks something, answer from the visible context. If the owner corrects you, update from the latest text itself. "
                + "Do not rely on any local keyword label; use the chronological dialogue.\n";
    }

    public static void trace(EntityMaid maid, EffectDecision decision) {
        if (maid == null || decision == null) {
            return;
        }
        MaidSoulStateRegistry.echoTraceToOwnerChat(
                maid,
                "maidsoul.reply_effect",
                decision.action() + " | " + decision.reason()
        );
    }

    private static boolean isHollowReply(String text) {
        return containsAny(text,
                "我在呢", "我听着", "继续说", "你继续", "随口问问", "不知道怎么回",
                "主人是不是", "发生什么了");
    }

    private static boolean looksLikeAvoidingAnswer(String text) {
        return containsAny(text, "我也不太清楚", "你是想问", "是不是在想", "没听懂", "什么意思")
                && !containsAny(text, "因为", "就是", "可以", "不能", "会", "不会");
    }

    private static boolean looksLikeQuestionMark(String text) {
        if (text == null) {
            return false;
        }
        String trimmed = text.trim();
        return trimmed.endsWith("?") || trimmed.endsWith("？");
    }

    private static boolean containsAny(String text, String... needles) {
        for (String needle : needles) {
            if (text.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT).replaceAll("[\\s，。！？,.!?、~～…]", "");
    }

    public record EffectDecision(EffectAction action, String reason) {
        public static EffectDecision accept(String reason) {
            return new EffectDecision(EffectAction.ACCEPT, reason);
        }

        public static EffectDecision retry(String reason) {
            if (!MaidSoulCommonConfig.CONVERSATION_HEARTFLOW_REWRITE_ENABLED.get()) {
                return accept("rewrite_disabled:" + reason);
            }
            return new EffectDecision(EffectAction.RETRY, reason);
        }
    }

    public enum EffectAction {
        ACCEPT,
        RETRY
    }
}
