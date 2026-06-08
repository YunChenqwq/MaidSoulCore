package com.maidsoulcore.forge.conversation;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.maidsoulcore.forge.config.MaidSoulCommonConfig;
import com.maidsoulcore.forge.service.MaidSoulSpeechService;

import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 聊天节奏门。
 * <p>
 * 参考项目里的 Timing Gate 不是“生成回复”，而是先决定本轮应该继续、等待还是不说。
 * 这里保留 Java 快路径：只处理显式静默、显式结束和明显没说完的情况，不做语义意图分类。
 */
public final class ConversationTimingGateService {
    private static final ConcurrentMap<UUID, GateState> STATES = new ConcurrentHashMap<>();

    private ConversationTimingGateService() {
    }

    public static TimingDecision decide(EntityMaid maid,
                                        String latestOwnerMessage,
                                        ConversationFlowService.TurnFrame turnFrame) {
        String latest = latestOwnerMessage == null ? "" : latestOwnerMessage.trim();
        if (latest.isBlank()) {
            return TimingDecision.noAction("empty_owner_input");
        }
        if (maid != null && MaidSoulSpeechService.hasPendingSpeech(maid)) {
            return TimingDecision.waitFor("speech_queue_active", 400L);
        }

        GateState state = maid == null ? new GateState() : STATES.computeIfAbsent(maid.getUUID(), id -> new GateState());
        long now = System.currentTimeMillis();
        synchronized (state) {
            if (now < state.nonContinueCooldownUntilMillis && isSoftInput(latest)) {
                return TimingDecision.noAction("non_continue_cooldown_soft_input");
            }
        }

        String compact = latest.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
        if (matchesExplicitTrigger(compact, MaidSoulCommonConfig.CONVERSATION_NO_REPLY_TRIGGERS.get())) {
            markNonContinue(state, now);
            return TimingDecision.noAction("owner_requested_silence");
        }
        if (matchesExplicitTrigger(compact, MaidSoulCommonConfig.CONVERSATION_FINISH_TRIGGERS.get())) {
            markNonContinue(state, now);
            return TimingDecision.finish("owner_finished_topic");
        }
        if (looksIncomplete(latest, compact)) {
            synchronized (state) {
                // 等待去重只看原始输入，不再混入本地 topic，避免旧主题污染新消息。
                String waitKey = compact;
                if (!waitKey.equals(state.lastWaitKey)) {
                    state.lastWaitKey = waitKey;
                    markNonContinue(state, now);
                    return TimingDecision.waitFor("owner_may_continue", MaidSoulCommonConfig.CONVERSATION_PACING_WAIT_MILLIS.get());
                }
            }
        }
        return TimingDecision.continueNow("ready");
    }

    private static void markNonContinue(GateState state, long now) {
        if (state == null) {
            return;
        }
        synchronized (state) {
            state.nonContinueCooldownUntilMillis = now
                    + Math.max(0L, MaidSoulCommonConfig.CONVERSATION_TIMING_NON_CONTINUE_COOLDOWN_MILLIS.get());
        }
    }

    private static boolean looksIncomplete(String raw, String compact) {
        String trimmed = raw.trim();
        if (trimmed.endsWith("，") || trimmed.endsWith(",") || trimmed.endsWith("、")) {
            return true;
        }
        if (trimmed.endsWith("...") || trimmed.endsWith("…")) {
            return true;
        }
        return matchesWaitTrigger(compact, MaidSoulCommonConfig.CONVERSATION_WAIT_TRIGGERS.get());
    }

    private static boolean isSoftInput(String text) {
        String compact = text.replaceAll("\\s+", "");
        return compact.length() <= 2 || "嗯".equals(compact) || "好".equals(compact) || "哦".equals(compact);
    }

    private static boolean containsAny(String text, java.util.List<? extends String> keywords) {
        for (String keyword : keywords) {
            if (keyword != null && !keyword.isBlank() && text.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesExplicitTrigger(String compact, java.util.List<? extends String> keywords) {
        String text = compact == null ? "" : compact.replaceAll("\\s+", "");
        for (String keyword : keywords) {
            if (keyword == null || keyword.isBlank()) {
                continue;
            }
            String key = keyword.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
            if (key.length() <= 2) {
                if (text.equals(key)) {
                    return true;
                }
            } else if (text.equals(key) || text.contains(key)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesWaitTrigger(String compact, java.util.List<? extends String> keywords) {
        String text = compact == null ? "" : compact.replaceAll("\\s+", "");
        for (String keyword : keywords) {
            if (keyword == null || keyword.isBlank()) {
                continue;
            }
            String key = keyword.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
            if (text.equals(key) || text.endsWith(key)) {
                return true;
            }
        }
        return false;
    }

    private static final class GateState {
        private String lastWaitKey = "";
        private long nonContinueCooldownUntilMillis;
    }

    public record TimingDecision(TimingAction action, String reason, long waitMillis) {
        public static TimingDecision continueNow(String reason) {
            return new TimingDecision(TimingAction.CONTINUE, reason, 0L);
        }

        public static TimingDecision waitFor(String reason, long waitMillis) {
            return new TimingDecision(TimingAction.WAIT, reason, Math.max(0L, waitMillis));
        }

        public static TimingDecision noAction(String reason) {
            return new TimingDecision(TimingAction.NO_ACTION, reason, 0L);
        }

        public static TimingDecision finish(String reason) {
            return new TimingDecision(TimingAction.FINISH, reason, 0L);
        }
    }

    public enum TimingAction {
        CONTINUE,
        WAIT,
        NO_ACTION,
        FINISH
    }
}
