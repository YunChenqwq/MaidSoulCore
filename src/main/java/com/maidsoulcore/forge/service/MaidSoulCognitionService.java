package com.maidsoulcore.forge.service;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.maidsoulcore.event.EventPriority;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * A small cognition layer between raw triggers and speech.
 * <p>
 * Raw services report what was perceived; this service keeps one continuous
 * "what I am noticing / thinking / needing" frame so short loops do not behave
 * like unrelated one-shot broadcasts.
 */
public final class MaidSoulCognitionService {
    private static final ConcurrentMap<UUID, CognitiveState> STATES = new ConcurrentHashMap<>();

    private MaidSoulCognitionService() {
    }

    public static CognitiveFrame observePerception(EntityMaid maid, String source, String eventType, String detail, EventPriority priority) {
        if (maid == null || eventType == null || eventType.isBlank()) {
            return CognitiveFrame.empty();
        }
        CognitiveState state = STATES.computeIfAbsent(maid.getUUID(), id -> new CognitiveState());
        synchronized (state) {
            long now = System.currentTimeMillis();
            String topicKey = MaidSoulTopicCooldownService.classifyTopicKey(eventType, detail);
            boolean sameTopic = !topicKey.isBlank() && topicKey.equals(state.activeTopicKey) && now < state.topicUntilMillis;
            state.repeatCount = sameTopic ? state.repeatCount + 1 : 1;
            state.activeTopicKey = topicKey;
            state.lastSource = blankToDefault(source, "event");
            state.lastEventType = eventType;
            state.lastDetail = shortText(detail, 120);
            state.currentPerception = perceptionFor(eventType, detail);
            state.currentThought = thoughtFor(eventType, detail, sameTopic, state.repeatCount);
            state.currentNeed = needFor(eventType, priority);
            state.reactionMode = reactionModeFor(eventType, priority, sameTopic);
            state.topicUntilMillis = now + holdMillis(priority, eventType);
            state.lastUpdatedMillis = now;
            return state.frame();
        }
    }

    public static void observeOwnerMessage(EntityMaid maid, String message) {
        if (maid == null || message == null || message.isBlank()) {
            return;
        }
        CognitiveState state = STATES.computeIfAbsent(maid.getUUID(), id -> new CognitiveState());
        synchronized (state) {
            long now = System.currentTimeMillis();
            state.lastSource = "owner_chat";
            state.lastEventType = "owner.message";
            state.lastDetail = shortText(message, 120);
            state.currentPerception = "主人正在和我说话: " + shortText(message, 80);
            state.currentThought = "先接住主人的话，再决定要不要行动。";
            state.currentNeed = "respond_to_owner";
            state.reactionMode = "owner_turn";
            state.activeTopicKey = "topic.owner_chat";
            state.repeatCount = 1;
            state.topicUntilMillis = now + 30_000L;
            state.lastUpdatedMillis = now;
        }
    }

    public static String promptBlock(EntityMaid maid) {
        if (maid == null) {
            return "cognition=none";
        }
        CognitiveState state = STATES.get(maid.getUUID());
        if (state == null) {
            return "cognition=none";
        }
        synchronized (state) {
            if (System.currentTimeMillis() > state.topicUntilMillis) {
                return "cognition=quiet; no active perceived topic";
            }
            return """
                    active_topic=%s
                    source=%s
                    perception=%s
                    thought=%s
                    need=%s
                    reaction_mode=%s
                    repeat_count=%d
                    rule=Respond from this continuous cognition frame; do not restart the same theme as if it were new.
                    """.formatted(
                    blankToDefault(state.activeTopicKey, "none"),
                    blankToDefault(state.lastSource, "none"),
                    blankToDefault(state.currentPerception, "none"),
                    blankToDefault(state.currentThought, "none"),
                    blankToDefault(state.currentNeed, "none"),
                    blankToDefault(state.reactionMode, "observe"),
                    state.repeatCount
            );
        }
    }

    public static String debugSummary(EntityMaid maid) {
        if (maid == null) {
            return "cognition=none";
        }
        CognitiveState state = STATES.get(maid.getUUID());
        if (state == null) {
            return "cognition=none";
        }
        synchronized (state) {
            return "topic=" + blankToDefault(state.activeTopicKey, "none")
                    + " mode=" + blankToDefault(state.reactionMode, "observe")
                    + " need=" + blankToDefault(state.currentNeed, "none")
                    + " thought=" + blankToDefault(state.currentThought, "none")
                    + " repeat=" + state.repeatCount;
        }
    }

    private static String perceptionFor(String eventType, String detail) {
        if (eventType.startsWith("owner.view.")) {
            return "我从主人的视角注意到: " + shortText(detail, 96);
        }
        if (eventType.startsWith("maid.attacked")) {
            return "我受到了伤害: " + shortText(detail, 96);
        }
        if (eventType.startsWith("world.hostile")) {
            return "附近危险发生变化: " + shortText(detail, 96);
        }
        if (eventType.startsWith("maid.idle.")) {
            return "环境安静，主人在附近但暂时没有说话。";
        }
        if (eventType.startsWith("maid.action.") || eventType.startsWith("owner.command")) {
            return "一个行动或命令状态发生了变化: " + shortText(detail, 96);
        }
        return "我注意到事件 " + eventType + ": " + shortText(detail, 96);
    }

    private static String thoughtFor(String eventType, String detail, boolean sameTopic, int repeatCount) {
        if (sameTopic && repeatCount > 1) {
            return "这是同一主题的延续，不应该换皮重复，只需要补充新的变化或保持沉默。";
        }
        if (eventType.startsWith("owner.view.risk_mob") || eventType.startsWith("world.hostile")) {
            return "先判断主人是否安全，再用简短提醒把注意力拉回危险。";
        }
        if (eventType.startsWith("owner.view.")) {
            return "这是主人当前关注的画面，只有真的值得陪伴开口时才轻轻接一句。";
        }
        if (eventType.startsWith("maid.attacked.by_owner")) {
            return "伤害来自主人，需要先处理关系和情绪，不能直接跳回普通话题。";
        }
        if (eventType.startsWith("maid.attacked")) {
            return "先确认安全和疼痛，再决定是否请求帮助。";
        }
        if (eventType.startsWith("maid.idle.")) {
            return "这是安静陪伴，不要像播报事件，要像自然地试探开口。";
        }
        return "把事件理解成当前处境的一部分，再选择是否回应。";
    }

    private static String needFor(String eventType, EventPriority priority) {
        if (eventType.startsWith("maid.attacked.by_owner")) {
            return "comfort_or_apology";
        }
        if (eventType.startsWith("maid.attacked") || eventType.startsWith("owner.view.risk_mob") || eventType.startsWith("world.hostile")) {
            return "safety";
        }
        if (eventType.startsWith("maid.idle.")) {
            return "companionship";
        }
        if (eventType.contains("failed") || eventType.contains("missing") || eventType.contains("not_allowed")) {
            return "explain_blocker";
        }
        return priority == EventPriority.P0 ? "urgent_attention" : "context_awareness";
    }

    private static String reactionModeFor(String eventType, EventPriority priority, boolean sameTopic) {
        if (priority == EventPriority.P0) {
            return "interrupt";
        }
        if (sameTopic) {
            return "continue_or_silence";
        }
        if (eventType.startsWith("owner.view.")) {
            return "soft_observation";
        }
        if (eventType.startsWith("maid.idle.")) {
            return "gentle_initiation";
        }
        return "react_if_useful";
    }

    private static long holdMillis(EventPriority priority, String eventType) {
        if (priority == EventPriority.P0 || eventType.startsWith("maid.attacked")) {
            return 60_000L;
        }
        if (eventType.startsWith("owner.view.") || eventType.startsWith("maid.idle.")) {
            return 45_000L;
        }
        return 30_000L;
    }

    private static String shortText(String text, int max) {
        if (text == null) {
            return "";
        }
        String clean = text.replace('\n', ' ').replace('\r', ' ').replaceAll("\\s+", " ").trim();
        return clean.length() <= max ? clean : clean.substring(0, max) + "...";
    }

    private static String blankToDefault(String text, String fallback) {
        return text == null || text.isBlank() ? fallback : text;
    }

    public record CognitiveFrame(
            String topicKey,
            String perception,
            String thought,
            String need,
            String reactionMode,
            int repeatCount
    ) {
        private static CognitiveFrame empty() {
            return new CognitiveFrame("", "", "", "", "", 0);
        }
    }

    private static final class CognitiveState {
        private String activeTopicKey = "";
        private String lastSource = "";
        private String lastEventType = "";
        private String lastDetail = "";
        private String currentPerception = "";
        private String currentThought = "";
        private String currentNeed = "";
        private String reactionMode = "";
        private int repeatCount = 0;
        private long topicUntilMillis = 0L;
        private long lastUpdatedMillis = 0L;

        private CognitiveFrame frame() {
            return new CognitiveFrame(activeTopicKey, currentPerception, currentThought, currentNeed, reactionMode, repeatCount);
        }
    }
}
