package com.maidsoul.brain.reply.effect;

import com.maidsoul.brain.message.ChatMessage;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 会话级回复效果追踪器。
 *
 * <p>这是 maibotdev ReplyEffectTracker 的原型机落地版：每条已发回复先进入 pending，
 * 后续用户消息作为反馈进入记录；一旦出现明确负反馈、修复循环或达到观察轮数，就完成评分。</p>
 */
public final class ReplyEffectTracker {
    private static final int TARGET_USER_FOLLOWUP_LIMIT = 2;
    private static final int SESSION_FOLLOWUP_LIMIT = 5;
    private static final long OBSERVATION_WINDOW_SECONDS = 600L;

    private final List<ReplyEffectRecord> pendingRecords = new ArrayList<>();
    private ReplyEffectRecord latestFinalizedRecord;

    public synchronized void recordReply(
            ChatMessage targetMessage,
            String replyText,
            List<String> replySegments,
            String plannerReasoning,
            String referenceInfo
    ) {
        String targetMessageId = targetMessage == null ? "" : targetMessage.id();
        String targetUserId = targetMessage == null ? "" : targetMessage.speaker();
        pendingRecords.add(new ReplyEffectRecord(
                targetMessageId,
                targetUserId,
                replyText,
                replySegments,
                plannerReasoning,
                referenceInfo
        ));
    }

    public synchronized void observeUserMessage(ChatMessage message) {
        if (message == null || pendingRecords.isEmpty()) {
            return;
        }
        for (ReplyEffectRecord record : new ArrayList<>(pendingRecords)) {
            if (record.finalized()) {
                continue;
            }
            double latency = Math.max(0.0, Duration.between(record.createdAt(), Instant.now()).toMillis() / 1000.0);
            FollowupMessageSnapshot followup = new FollowupMessageSnapshot(
                    message.id(),
                    message.timestamp(),
                    message.speaker(),
                    message.content(),
                    latency,
                    !record.targetUserId().isBlank() && record.targetUserId().equals(message.speaker())
            );
            record.addFollowup(followup);
            String reason = resolveFinalizeReason(record);
            if (!reason.isBlank()) {
                finalizeRecord(record, reason);
            }
        }
        pendingRecords.removeIf(ReplyEffectRecord::finalized);
    }

    public synchronized ReplyEffectSummary latestSummary() {
        ReplyEffectRecord record = latestFinalizedRecord;
        if (record == null || record.scores() == null) {
            return ReplyEffectSummary.neutral();
        }
        FrictionSignals friction = record.scores().frictionSignals();
        boolean localNegative = "maidsoul_explicit_negative".equals(record.finalizeReason());
        boolean localRepair = "maidsoul_repair_loop".equals(record.finalizeReason());
        return new ReplyEffectSummary(
                record.finalizeReason(),
                record.scores().asi(),
                record.scores().frictionScore(),
                friction.explicitNegative() >= 1.0 || localNegative,
                friction.repairLoop() >= 1.0 || localRepair,
                record.followups().size()
        );
    }

    private String resolveFinalizeReason(ReplyEffectRecord record) {
        String targetUserId = record.targetUserId();
        List<FollowupMessageSnapshot> targetFollowups = record.followups().stream()
                .filter(item -> !targetUserId.isBlank() && targetUserId.equals(item.userId()))
                .toList();
        boolean hasTargetFeedback = !targetFollowups.isEmpty();
        if (ReplyEffectScoring.hasExplicitNegativeFeedback(targetFollowups, targetUserId, false)) {
            return "explicit_negative";
        }
        if (ReplyEffectScoring.hasRepairLoop(targetFollowups, targetUserId, false)) {
            return "repair_loop";
        }
        if (containsLocalNegative(targetFollowups)) {
            return "maidsoul_explicit_negative";
        }
        if (containsLocalRepair(targetFollowups)) {
            return "maidsoul_repair_loop";
        }
        if (targetFollowups.size() >= TARGET_USER_FOLLOWUP_LIMIT) {
            return "target_user_followups";
        }
        if (targetUserId.isBlank() || !hasTargetFeedback) {
            boolean allowIndirect = targetUserId.isBlank();
            if (ReplyEffectScoring.hasExplicitNegativeFeedback(record.followups(), targetUserId, allowIndirect)) {
                return "explicit_negative";
            }
            if (ReplyEffectScoring.hasRepairLoop(record.followups(), targetUserId, allowIndirect)) {
                return "repair_loop";
            }
            if (containsLocalNegative(record.followups())) {
                return "maidsoul_explicit_negative";
            }
            if (containsLocalRepair(record.followups())) {
                return "maidsoul_repair_loop";
            }
            if (record.followups().size() >= SESSION_FOLLOWUP_LIMIT) {
                return "session_followups_limit";
            }
        }
        if (Duration.between(record.createdAt(), Instant.now()).toSeconds() >= OBSERVATION_WINDOW_SECONDS) {
            return "window_timeout";
        }
        return "";
    }

    private void finalizeRecord(ReplyEffectRecord record, String reason) {
        ReplyEffectScores scores = ReplyEffectScoring.score(record.followups(), record.targetUserId());
        record.finalizeWith(reason, scores);
        latestFinalizedRecord = record;
    }

    private static boolean containsLocalNegative(List<FollowupMessageSnapshot> followups) {
        for (FollowupMessageSnapshot followup : followups) {
            if (MaidSoulReplyEffectPatterns.hasLocalNegative(followup.plainText())) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsLocalRepair(List<FollowupMessageSnapshot> followups) {
        for (FollowupMessageSnapshot followup : followups) {
            if (MaidSoulReplyEffectPatterns.hasLocalRepair(followup.plainText())) {
                return true;
            }
        }
        return false;
    }

    public record ReplyEffectSummary(
            String finalizeReason,
            double asi,
            double frictionScore,
            boolean explicitNegative,
            boolean repairLoop,
            int followupCount
    ) {
        public static ReplyEffectSummary neutral() {
            return new ReplyEffectSummary("", 50.0, 0.15, false, false, 0);
        }

        public boolean hasFriction() {
            return explicitNegative || repairLoop || finalizeReason.startsWith("maidsoul_") || frictionScore >= 0.45;
        }
    }
}
