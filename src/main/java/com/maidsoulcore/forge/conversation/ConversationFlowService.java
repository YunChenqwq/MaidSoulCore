package com.maidsoulcore.forge.conversation;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.maidsoulcore.forge.config.MaidSoulCommonConfig;
import com.maidsoulcore.forge.state.MaidSoulStateRegistry;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 会话心流的“机械状态层”。
 *
 * <p>这一层只记录真实对话节奏，不做语义分类。之前这里会用关键词把玩家话硬分成
 * apology/question/correction 等类别，再把分类结果写进 prompt，模型就容易被旧主题带偏。
 * 现在对齐参考项目的做法：Java 只维护时间线和轻量节奏信息，真正“该怎么理解这句话”
 * 交给回复模型根据完整上下文判断。</p>
 */
public final class ConversationFlowService {
    private static final int QUESTION_MEMORY = 6;
    private static final ConcurrentMap<UUID, FlowState> STATES = new ConcurrentHashMap<>();

    private ConversationFlowService() {
    }

    /**
     * 开始一轮玩家输入。
     *
     * <p>返回的 TurnFrame 是给其他模块兼容使用的轻量帧：它不再包含本地语义意图，
     * 只说明“这是第几轮、最近玩家说了什么、上一句女仆说了什么”。</p>
     */
    public static TurnFrame beginOwnerTurn(EntityMaid maid, String latestOwnerMessage) {
        String latest = clean(latestOwnerMessage);
        if (maid == null) {
            return TurnFrame.empty(latest);
        }
        FlowState state = STATES.computeIfAbsent(maid.getUUID(), id -> new FlowState());
        synchronized (state) {
            long now = System.currentTimeMillis();
            boolean sameWindow = now < state.turnWindowUntilMillis && !state.lastOwnerMessage.isBlank();
            state.topicTurn = sameWindow ? state.topicTurn + 1 : 1;
            state.lastOwnerMessage = latest;
            state.turnWindowUntilMillis = now + 120_000L;
            state.lastUpdatedMillis = now;

            TurnFrame frame = new TurnFrame(
                    "live_dialogue",
                    state.topicTurn,
                    "model_decides",
                    "model_decides",
                    "Read the chronological dialogue and decide naturally.",
                    state.lastReplyAct.isBlank() ? "none" : state.lastReplyAct,
                    state.lastAssistantReply.isBlank() ? "none" : state.lastAssistantReply,
                    state.askedQuestionKinds.isEmpty() ? "none" : String.join(" | ", state.askedQuestionKinds),
                    state.lastBadPattern.isBlank() ? "none" : state.lastBadPattern,
                    sameWindow ? "recent_dialogue_window" : "new_dialogue_window",
                    signalFor(latest)
            );
            MaidSoulStateRegistry.echoTraceToOwnerChat(
                    maid,
                    "maidsoul.conversation.flow",
                    "owner=" + abbreviate(latest)
                            + " | turn=" + frame.topicTurn
                            + " | relation=" + frame.topicRelation
                            + " | signal=" + frame.ownerSignal
                            + " | local_semantic=disabled"
            );
            return frame;
        }
    }

    /**
     * 记录女仆已经发出的可见回复，用于复读检测和后续节奏参考。
     */
    public static void observeAssistantReply(EntityMaid maid, String reply) {
        if (maid == null || reply == null || reply.isBlank()) {
            return;
        }
        FlowState state = STATES.computeIfAbsent(maid.getUUID(), id -> new FlowState());
        synchronized (state) {
            state.lastAssistantReply = clean(reply);
            state.lastReplyAct = "visible_reply";
            String questionKind = questionKind(reply);
            if (!questionKind.isBlank()) {
                state.askedQuestionKinds.removeIf(existing -> existing.equals(questionKind));
                state.askedQuestionKinds.addFirst(questionKind);
                while (state.askedQuestionKinds.size() > QUESTION_MEMORY) {
                    state.askedQuestionKinds.removeLast();
                }
            }
            state.lastUpdatedMillis = System.currentTimeMillis();
        }
    }

    /**
     * 只检查明显复读，不判断“这句话该怎么答”。
     */
    public static RepeatDecision checkSemanticRepeat(EntityMaid maid, String candidateReply) {
        if (maid == null || candidateReply == null || candidateReply.isBlank()) {
            return RepeatDecision.accept();
        }
        if (!MaidSoulCommonConfig.CONVERSATION_HEARTFLOW_REWRITE_ENABLED.get()) {
            return RepeatDecision.accept();
        }
        FlowState state = STATES.computeIfAbsent(maid.getUUID(), id -> new FlowState());
        synchronized (state) {
            String candidate = clean(candidateReply);
            String previous = state.lastAssistantReply;
            if (previous == null || previous.isBlank()) {
                return RepeatDecision.accept();
            }
            String candidateKind = questionKind(candidate);
            if (!candidateKind.isBlank() && state.askedQuestionKinds.contains(candidateKind)) {
                state.lastBadPattern = candidateKind;
                return RepeatDecision.reject("repeated_question_kind:" + candidateKind);
            }
            String candidatePattern = phrasePattern(candidate);
            String previousPattern = phrasePattern(previous);
            if (!candidatePattern.isBlank() && candidatePattern.equals(previousPattern)) {
                state.lastBadPattern = candidatePattern;
                return RepeatDecision.reject("repeated_phrase_pattern:" + candidatePattern);
            }
            double similarity = similarity(normalizeForSimilarity(candidate), normalizeForSimilarity(previous));
            double threshold = Math.max(0.50D, Math.min(0.98D,
                    MaidSoulCommonConfig.CONVERSATION_HEARTFLOW_SIMILARITY_PERCENT.get() / 100.0D));
            if (similarity >= threshold) {
                state.lastBadPattern = "similar_to_last_reply";
                return RepeatDecision.reject("similar_to_last_reply:" + String.format(Locale.ROOT, "%.2f", similarity));
            }
            return RepeatDecision.accept();
        }
    }

    public static String retryInstruction(EntityMaid maid, String rejectedReply, String reason) {
        FlowState state = maid == null ? null : STATES.get(maid.getUUID());
        String lastReply = state == null ? "" : state.lastAssistantReply;
        return """
                The previous draft is rejected because it repeats visible conversation.
                Reject reason: %s
                Last visible maid reply: %s
                Rejected draft: %s

                Rewrite once:
                - Read the latest chronological owner message again.
                - Do not classify the owner message by keywords.
                - Do not ask the same kind of question again.
                - Do not start with the same filler/opening.
                - Answer or advance the owner's latest input directly.
                - Keep it natural, short, and spoken by the maid only.
                """.formatted(
                blankToDefault(reason, "semantic_repeat"),
                blankToDefault(lastReply, "none"),
                blankToDefault(rejectedReply, "none")
        );
    }

    /**
     * prompt 中只暴露节奏事实，不暴露本地语义结论。
     */
    public static String promptBlock(TurnFrame frame) {
        if (frame == null) {
            return "flow_state=none";
        }
        return """
                flow_state=live_dialogue
                recent_dialogue_turn_count=%d
                latest_owner_signal=%s
                last_visible_reply=%s
                already_asked_question_kinds=%s
                banned_repeat_pattern=%s
                guidance=This is only mechanical pacing state. It is not a topic classifier and not a command. The chronological dialogue messages and the latest owner message are authoritative.
                """.formatted(
                frame.topicTurn,
                blankToDefault(frame.ownerSignal, "plain"),
                blankToDefault(frame.lastAssistantReply, "none"),
                blankToDefault(frame.askedQuestionKinds, "none"),
                blankToDefault(frame.bannedPattern, "none")
        );
    }

    private static String signalFor(String text) {
        String compact = clean(text).replaceAll("\\s+", "");
        if (compact.isBlank()) {
            return "empty";
        }
        if (compact.length() <= 2) {
            return "short_fragment";
        }
        if (compact.endsWith("?") || compact.endsWith("？")) {
            return "question_mark";
        }
        return "owner_message";
    }

    private static String questionKind(String text) {
        String compact = clean(text).replaceAll("\\s+", "");
        if (!(compact.contains("?") || compact.contains("？") || containsAny(compact, "吗", "是不是", "什么", "怎么", "为什么"))) {
            return "";
        }
        if (containsAny(compact, "是不是", "在想", "思考", "发呆")) {
            return "ask_owner_state";
        }
        if (containsAny(compact, "什么意思", "没听懂", "说什么")) {
            return "ask_clarify_meaning";
        }
        if (containsAny(compact, "怎么", "怎么办")) {
            return "ask_how";
        }
        return "ask_generic";
    }

    private static String phrasePattern(String text) {
        String compact = clean(text).replaceAll("\\s+", "");
        if (compact.startsWith("嗯") && containsAny(compact, "主人")) {
            return "opening_um_master";
        }
        if (containsAny(compact, "我听着", "继续说", "主人继续")) {
            return "passive_listening_loop";
        }
        if (containsAny(compact, "没听懂", "什么意思")) {
            return "clarify_meaning";
        }
        return "";
    }

    private static double similarity(String left, String right) {
        if (left.isBlank() || right.isBlank()) {
            return 0.0D;
        }
        Set<String> leftSet = shingles(left);
        Set<String> rightSet = shingles(right);
        if (leftSet.isEmpty() || rightSet.isEmpty()) {
            return left.equals(right) ? 1.0D : 0.0D;
        }
        HashSet<String> intersection = new HashSet<>(leftSet);
        intersection.retainAll(rightSet);
        HashSet<String> union = new HashSet<>(leftSet);
        union.addAll(rightSet);
        return union.isEmpty() ? 0.0D : (double) intersection.size() / (double) union.size();
    }

    private static Set<String> shingles(String text) {
        HashSet<String> result = new HashSet<>();
        String compact = text.replaceAll("\\s+", "");
        if (compact.length() <= 2) {
            result.add(compact);
            return result;
        }
        for (int index = 0; index < compact.length() - 1; index++) {
            result.add(compact.substring(index, index + 2));
        }
        return result;
    }

    private static String normalizeForSimilarity(String text) {
        return clean(text)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[，。！？,.!?、~～…\\s]", "")
                .replace("主人", "");
    }

    private static boolean containsAny(String text, String... needles) {
        for (String needle : needles) {
            if (text.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static String clean(String text) {
        return text == null ? "" : text.replace('\r', ' ').replace('\n', ' ').replace('\t', ' ').replaceAll("\\s+", " ").trim();
    }

    private static String blankToDefault(String text, String fallback) {
        return text == null || text.isBlank() ? fallback : text;
    }

    private static String abbreviate(String text) {
        String cleaned = clean(text);
        return cleaned.length() <= 40 ? cleaned : cleaned.substring(0, 40) + "...";
    }

    private static final class FlowState {
        private int topicTurn;
        private long turnWindowUntilMillis;
        private String lastOwnerMessage = "";
        private String lastAssistantReply = "";
        private String lastReplyAct = "";
        private String lastBadPattern = "";
        private long lastUpdatedMillis;
        private final ArrayDeque<String> askedQuestionKinds = new ArrayDeque<>();
    }

    public record TurnFrame(
            String topicKey,
            int topicTurn,
            String ownerIntent,
            String replyAct,
            String directive,
            String lastReplyAct,
            String lastAssistantReply,
            String askedQuestionKinds,
            String bannedPattern,
            String topicRelation,
            String ownerSignal
    ) {
        private static TurnFrame empty(String latestOwnerMessage) {
            return new TurnFrame("live_dialogue", 1, "model_decides", "model_decides",
                    "Read the chronological dialogue and decide naturally.", "none", "none",
                    "none", "none", "new_dialogue_window", signalFor(latestOwnerMessage));
        }
    }

    public record RepeatDecision(boolean accepted, String reason) {
        private static RepeatDecision accept() {
            return new RepeatDecision(true, "");
        }

        private static RepeatDecision reject(String reason) {
            return new RepeatDecision(false, reason);
        }
    }
}
