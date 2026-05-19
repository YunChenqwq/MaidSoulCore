package com.maidsoul.brain.runtime;

import com.maidsoul.brain.config.FlowConfig;

/**
 * 主动候选调度器。
 *
 * <p>它只负责“何时把沉默/世界/关系余韵提交给 planner 看一眼”，不决定 reply、wait 或 no_action。
 * 真正的行动决策仍然只能由 planner 输出，运行时不得覆盖。</p>
 */
public final class ProactiveScheduler {
    private static final int CONTINUE_CANDIDATE_CURIOSITY = 45;
    private static final int ACCELERATE_CANDIDATE_CURIOSITY = 70;

    public static final int STAGE_LIGHT_FOLLOWUP = 0;
    public static final int STAGE_TOPIC_PUSH = 1;
    public static final int STAGE_WORLD_OBSERVE = 2;
    public static final int STAGE_RELATION_CANDIDATE = 3;
    public static final int STAGE_FINAL_NOTICE = 4;
    public static final int STAGE_LONG_SILENCE_CHECK = 5;
    public static final int STAGE_IDLE = 6;

    private final FlowConfig flow;

    public ProactiveScheduler(FlowConfig flow) {
        this.flow = flow;
    }

    public int maxVisibleReplies() {
        return Math.max(1, flow.proactiveMaxVisibleReplies());
    }

    public int maxLongSilenceChecks() {
        return Math.max(0, flow.proactiveMaxLongSilenceChecks());
    }

    public int longSilenceCheckSeconds() {
        return Math.max(1, flow.proactiveLongSilenceCheckSeconds());
    }

    public boolean shouldScheduleLongSilenceCheck(int longSilenceChecks) {
        return longSilenceChecks < maxLongSilenceChecks();
    }

    public boolean shouldScheduleAfterSilentDecision(int activeCuriosity, int silentDecisions, int firedCandidates) {
        if (firedCandidates >= maxVisibleReplies()) {
            return false;
        }
        if (silentDecisions <= 0) {
            return true;
        }
        // 连续沉默判断后，只要主动好奇还没有跌破中位，就继续把候选交给 planner。
        // 运行时仍不决定是否开口，只避免“有情绪余韵但时钟彻底停掉”。
        return activeCuriosity >= CONTINUE_CANDIDATE_CURIOSITY && silentDecisions < maxVisibleReplies();
    }

    public int nextStageAfterFire(int currentStage) {
        return Math.min(currentStage + 1, STAGE_IDLE);
    }

    public int nextDelaySeconds(int stage, int activeCuriosity, int silentDecisions, int firedCandidates) {
        if (firedCandidates >= maxVisibleReplies()) {
            return -1;
        }
        int base = switch (stage) {
            case STAGE_LIGHT_FOLLOWUP -> Math.max(flow.proactiveInputProtectionSeconds(), flow.proactiveLightFollowupAfterSeconds());
            case STAGE_TOPIC_PUSH -> Math.max(flow.proactiveLightFollowupAfterSeconds() + 1, flow.proactiveTopicPushAfterSeconds());
            case STAGE_WORLD_OBSERVE -> Math.max(flow.proactiveTopicPushAfterSeconds() + 1, flow.proactiveWorldObserveAfterSeconds());
            case STAGE_RELATION_CANDIDATE -> Math.max(flow.proactiveTopicPushAfterSeconds(), 45);
            case STAGE_FINAL_NOTICE -> Math.max(flow.proactiveTopicPushAfterSeconds(), flow.proactiveWorldObserveAfterSeconds());
            case STAGE_LONG_SILENCE_CHECK -> longSilenceCheckSeconds();
            default -> Math.max(flow.proactiveWorldObserveAfterSeconds() + 1, flow.proactiveIdleMinIntervalSeconds());
        };
        if (stage == STAGE_LONG_SILENCE_CHECK) {
            return base;
        }
        if (silentDecisions <= 0) {
            return base;
        }
        if (activeCuriosity >= ACCELERATE_CANDIDATE_CURIOSITY) {
            return Math.max(flow.proactiveTopicPushAfterSeconds(), base / 2);
        }
        if (activeCuriosity >= CONTINUE_CANDIDATE_CURIOSITY) {
            return base;
        }
        return -1;
    }

    public String stageName(int stage) {
        return switch (stage) {
            case STAGE_LIGHT_FOLLOWUP -> "light_followup";
            case STAGE_TOPIC_PUSH -> "topic_push";
            case STAGE_WORLD_OBSERVE -> "world_observe";
            case STAGE_RELATION_CANDIDATE -> "proactive_candidate";
            case STAGE_FINAL_NOTICE -> "final_notice";
            case STAGE_LONG_SILENCE_CHECK -> "long_silence_check";
            default -> "idle";
        };
    }

    public String buildEvent(
            long silentSeconds,
            int stage,
            int activeCuriosity,
            int firedCandidates,
            int longSilenceChecks,
            String affectHint
    ) {
        String rule = switch (stage) {
            case STAGE_LIGHT_FOLLOWUP -> "这是轻续话候选。只有上一轮明确问了问题、话题明显未收束，或玩家用短反馈把话语权交回，才考虑补一句；否则 wait 或 no_action。";
            case STAGE_TOPIC_PUSH -> "这是话题推进候选。若上一轮只是安抚/陪伴且没有问出具体问题，可以考虑一个低压力小问题；如果刚问过，就继续等。";
            case STAGE_WORLD_OBSERVE -> "这是环境/记忆候选。只有存在可靠的世界事件、视觉摘要、关系记忆或明显情绪残留时，才考虑主动；没有依据不要编造。";
            case STAGE_RELATION_CANDIDATE -> "这是关系余韵候选。调度器认为主动好奇仍然较高，但这不代表必须发言；请结合最近是否追问过、用户是否可能需要空间来决定。";
            case STAGE_FINAL_NOTICE -> "这是最后收束候选。若前面已经多次主动而玩家仍沉默，只允许短短告知会先安静下来；也可以 no_action 直接进入沉默。";
            case STAGE_LONG_SILENCE_CHECK -> "这是长时间沉默复检。普通话题短期不追问，但玩家很久没回时，第一次复检应明显偏向轻量确认对方是否还在、是否去忙了，语气像被晾了一会儿但不施压；第二次以后再偏向短短收束或 no_action。不要质问，不要连续逼问。";
            default -> "这是低频陪伴候选。除非有明确情绪、环境或关系理由，否则优先 no_action，避免刷屏。";
        };
        return "[主动候选事件] 上一轮对话节奏点后，玩家沉默约 " + silentSeconds + " 秒。"
                + "阶段=" + stageName(stage) + "。"
                + "主动好奇=" + activeCuriosity + "。"
                + "本轮用户沉默后的主动候选次数=" + (firedCandidates + 1) + "/" + maxVisibleReplies() + "。"
                + "长沉默复检次数=" + (longSilenceChecks + 1) + "/" + maxLongSilenceChecks() + "。"
                + rule
                + "当前情绪主动参考：" + (affectHint == null || affectHint.isBlank() ? "none" : affectHint)
                + "这只是提交给 planner 的候选信号，不是可见话题；planner 可以 reply、wait 或 no_action，运行时不会覆盖。";
    }
}
