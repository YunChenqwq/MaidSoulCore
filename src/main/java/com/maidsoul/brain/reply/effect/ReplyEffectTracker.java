package com.maidsoul.brain.reply.effect;

import com.maidsoul.brain.message.ChatMessage;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 会话级回复效果追踪器。
 *
 * <p>对齐 上游参考系统 的 ReplyEffectTracker：每条已发回复进入 pending，后续用户消息作为行为反馈；
 * 出现明确负反馈、修复循环、目标用户后续轮数足够或观察窗口超时后 finalize 并写回 JSON。</p>
 */
public final class ReplyEffectTracker {
    private static final int TARGET_USER_FOLLOWUP_LIMIT = 2;
    private static final int SESSION_FOLLOWUP_LIMIT = 5;
    private static final long OBSERVATION_WINDOW_SECONDS = 600L;

    private final String sessionId;
    private final String sessionName;
    private final ReplyEffectStorage storage;
    private final List<ReplyEffectRecord> pendingRecords = new ArrayList<>();
    private ReplyEffectRecord latestFinalizedRecord;

    public ReplyEffectTracker() {
        this("prototype-session", "MaidSoulCore Brain Test", new ReplyEffectStorage());
    }

    public ReplyEffectTracker(String sessionId, String sessionName, ReplyEffectStorage storage) {
        this.sessionId = normalize(sessionId, "prototype-session");
        this.sessionName = normalize(sessionName, this.sessionId);
        this.storage = storage == null ? new ReplyEffectStorage() : storage;
    }

    public synchronized void recordReply(
            ChatMessage targetMessage,
            String replyText,
            List<String> replySegments,
            String plannerReasoning,
            String referenceInfo
    ) {
        String targetMessageId = targetMessage == null ? "" : targetMessage.id();
        String targetUserId = targetMessage == null ? "" : targetMessage.speaker();
        ReplyEffectRecord record = new ReplyEffectRecord(
                sessionId,
                sessionName,
                targetMessageId,
                targetUserId,
                targetUserId,
                replyText,
                replySegments,
                plannerReasoning,
                referenceInfo
        );
        pendingRecords.add(record);
        safeSaveNew(record);
    }

    public synchronized void observeUserMessage(ChatMessage message) {
        if (message == null || pendingRecords.isEmpty()) {
            return;
        }
        for (ReplyEffectRecord record : new ArrayList<>(pendingRecords)) {
            if (record.finalized()) {
                continue;
            }
            FollowupMessageSnapshot followup = buildFollowupSnapshot(message, record);
            record.addFollowup(followup);
            safeSave(record);

            String reason = resolveFinalizeReason(record);
            if (!reason.isBlank()) {
                finalizeRecord(record, reason);
            }
        }
        pendingRecords.removeIf(ReplyEffectRecord::finalized);
    }

    public synchronized void finalizeExpired() {
        for (ReplyEffectRecord record : new ArrayList<>(pendingRecords)) {
            if (!record.finalized() && observationWindowExpired(record)) {
                finalizeRecord(record, "window_timeout");
            }
        }
        pendingRecords.removeIf(ReplyEffectRecord::finalized);
    }

    public synchronized void finalizeAll(String reason) {
        String normalizedReason = reason == null || reason.isBlank() ? "runtime_stop" : reason;
        for (ReplyEffectRecord record : new ArrayList<>(pendingRecords)) {
            if (!record.finalized()) {
                finalizeRecord(record, normalizedReason);
            }
        }
        pendingRecords.removeIf(ReplyEffectRecord::finalized);
    }

    public synchronized ReplyEffectSummary latestSummary() {
        finalizeExpired();
        ReplyEffectRecord record = latestFinalizedRecord;
        if (record == null || record.scores() == null) {
            return ReplyEffectSummary.neutral();
        }
        FrictionSignals friction = record.scores().frictionSignals();
        return new ReplyEffectSummary(
                record.finalizeReason(),
                record.scores().asi(),
                record.scores().frictionScore(),
                friction.explicitNegative() >= 1.0,
                friction.repairLoop() >= 1.0,
                record.followups().size()
        );
    }

    public synchronized List<ReplyEffectRecord> pendingRecords() {
        return List.copyOf(pendingRecords);
    }

    private FollowupMessageSnapshot buildFollowupSnapshot(ChatMessage message, ReplyEffectRecord record) {
        double latency = Math.max(0.0, Duration.between(record.createdAt(), Instant.now()).toMillis() / 1000.0);
        boolean targetUser = !record.targetUserId().isBlank() && record.targetUserId().equals(message.speaker());
        return new FollowupMessageSnapshot(
                message.id(),
                message.timestamp(),
                message.speaker(),
                message.speaker(),
                message.content(),
                message.content(),
                latency,
                targetUser,
                List.of()
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
            if (record.followups().size() >= SESSION_FOLLOWUP_LIMIT) {
                return "session_followups_limit";
            }
        }
        if (observationWindowExpired(record)) {
            return "window_timeout";
        }
        return "";
    }

    private void finalizeRecord(ReplyEffectRecord record, String reason) {
        ReplyEffectScores scores = ReplyEffectScoring.score(record.followups(), record.targetUserId());
        record.finalizeWith(reason, scores);
        latestFinalizedRecord = record;
        safeSave(record);
    }

    private boolean observationWindowExpired(ReplyEffectRecord record) {
        return Duration.between(record.createdAt(), Instant.now()).toSeconds() >= OBSERVATION_WINDOW_SECONDS;
    }

    private void safeSaveNew(ReplyEffectRecord record) {
        try {
            storage.createRecordFile(record);
        } catch (RuntimeException ignored) {
            // reply_effect 是观察侧能力，落盘失败不应打断聊天。
        }
    }

    private void safeSave(ReplyEffectRecord record) {
        try {
            storage.saveRecord(record);
        } catch (RuntimeException ignored) {
            // reply_effect 是观察侧能力，落盘失败不应打断聊天。
        }
    }

    private static String normalize(String value, String fallback) {
        String text = value == null ? "" : value.trim();
        return text.isBlank() ? fallback : text;
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
            return explicitNegative || repairLoop || frictionScore >= 0.45;
        }
    }
}
