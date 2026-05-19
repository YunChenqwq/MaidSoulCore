package com.maidsoul.brain.reply.effect;

import com.maidsoul.brain.util.JsonText;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 一条 reply 工具回复的效果观察记录。
 *
 * <p>结构对齐 maibotdev 的 ReplyEffectRecord：记录会话、目标用户、回复内容、后续反馈、
 * 评分、完成原因和置信说明。这样后续调参可以看数据，而不是凭单轮观感堆补丁。</p>
 */
public final class ReplyEffectRecord {
    private static final int SCHEMA_VERSION = 1;

    private final String effectId = UUID.randomUUID().toString();
    private final Instant createdAt = Instant.now();
    private final String sessionId;
    private final String sessionName;
    private final String targetMessageId;
    private final String targetUserId;
    private final String targetNickname;
    private final String replyText;
    private final List<String> replySegments;
    private final String plannerReasoning;
    private final String referenceInfo;
    private final List<FollowupMessageSnapshot> followups = new ArrayList<>();

    private ReplyEffectStatus status = ReplyEffectStatus.PENDING;
    private Instant updatedAt = createdAt;
    private Instant finalizedAt;
    private String finalizeReason = "";
    private String confidenceNote = "";
    private ReplyEffectScores scores;
    private Path filePath;

    public ReplyEffectRecord(
            String sessionId,
            String sessionName,
            String targetMessageId,
            String targetUserId,
            String targetNickname,
            String replyText,
            List<String> replySegments,
            String plannerReasoning,
            String referenceInfo
    ) {
        this.sessionId = normalize(sessionId);
        this.sessionName = normalize(sessionName);
        this.targetMessageId = normalize(targetMessageId);
        this.targetUserId = normalize(targetUserId);
        this.targetNickname = normalize(targetNickname);
        this.replyText = normalize(replyText);
        this.replySegments = List.copyOf(replySegments == null ? List.of() : replySegments);
        this.plannerReasoning = normalize(plannerReasoning);
        this.referenceInfo = normalize(referenceInfo);
    }

    public String effectId() {
        return effectId;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public String sessionId() {
        return sessionId;
    }

    public String sessionName() {
        return sessionName;
    }

    public String targetMessageId() {
        return targetMessageId;
    }

    public String targetUserId() {
        return targetUserId;
    }

    public String targetNickname() {
        return targetNickname;
    }

    public String replyText() {
        return replyText;
    }

    public List<String> replySegments() {
        return replySegments;
    }

    public String plannerReasoning() {
        return plannerReasoning;
    }

    public String referenceInfo() {
        return referenceInfo;
    }

    public List<FollowupMessageSnapshot> followups() {
        return followups;
    }

    public boolean finalized() {
        return status == ReplyEffectStatus.FINALIZED;
    }

    public ReplyEffectStatus status() {
        return status;
    }

    public Instant finalizedAt() {
        return finalizedAt;
    }

    public String finalizeReason() {
        return finalizeReason;
    }

    public String confidenceNote() {
        return confidenceNote;
    }

    public ReplyEffectScores scores() {
        return scores;
    }

    public Path filePath() {
        return filePath;
    }

    void setFilePath(Path filePath) {
        this.filePath = filePath;
    }

    void addFollowup(FollowupMessageSnapshot followup) {
        followups.add(followup);
        updatedAt = Instant.now();
    }

    void finalizeWith(String reason, ReplyEffectScores scores) {
        this.status = ReplyEffectStatus.FINALIZED;
        this.finalizeReason = normalize(reason);
        this.scores = scores;
        this.finalizedAt = Instant.now();
        this.updatedAt = finalizedAt;
        this.confidenceNote = buildConfidenceNote();
    }

    String buildConfidenceNote() {
        if (followups.isEmpty()) {
            return "没有观察到后续用户消息，行为分使用保守中性信号。";
        }
        for (FollowupMessageSnapshot followup : followups) {
            if (followup.targetUser()) {
                return "行为反馈包含回复对象本人的后续发言。";
            }
        }
        return "行为反馈来自同会话其他用户，不是回复对象本人，置信度较低。";
    }

    public String toJson() {
        StringBuilder builder = new StringBuilder();
        builder.append("{\n");
        field(builder, "schema_version", String.valueOf(SCHEMA_VERSION), false, 2, true);
        field(builder, "effect_id", effectId, true, 2, true);
        field(builder, "status", status.value(), true, 2, true);
        field(builder, "created_at", createdAt.toString(), true, 2, true);
        field(builder, "updated_at", updatedAt.toString(), true, 2, true);
        field(builder, "finalized_at", finalizedAt == null ? "" : finalizedAt.toString(), true, 2, true);
        field(builder, "finalize_reason", finalizeReason, true, 2, true);
        field(builder, "confidence_note", confidenceNote, true, 2, true);
        objectStart(builder, "session", 2);
        field(builder, "session_id", sessionId, true, 4, true);
        field(builder, "session_name", sessionName, true, 4, false);
        objectEnd(builder, 2, true);
        objectStart(builder, "target_user", 2);
        field(builder, "user_id", targetUserId, true, 4, true);
        field(builder, "nickname", targetNickname, true, 4, true);
        field(builder, "target_message_id", targetMessageId, true, 4, false);
        objectEnd(builder, 2, true);
        objectStart(builder, "reply", 2);
        field(builder, "reply_text", replyText, true, 4, true);
        arrayField(builder, "reply_segments", replySegments, 4, true);
        field(builder, "planner_reasoning", plannerReasoning, true, 4, true);
        field(builder, "reference_info", referenceInfo, true, 4, false);
        objectEnd(builder, 2, true);
        followupsField(builder);
        if (scores != null) {
            scoresField(builder);
        } else {
            builder.append("  \"scores\": null\n");
        }
        builder.append("}\n");
        return builder.toString();
    }

    private void followupsField(StringBuilder builder) {
        builder.append("  \"followup_messages\": [\n");
        for (int i = 0; i < followups.size(); i++) {
            FollowupMessageSnapshot followup = followups.get(i);
            builder.append("    {\n");
            field(builder, "message_id", followup.messageId(), true, 6, true);
            field(builder, "timestamp", followup.timestamp() == null ? "" : followup.timestamp().toString(), true, 6, true);
            field(builder, "user_id", followup.userId(), true, 6, true);
            field(builder, "nickname", followup.nickname(), true, 6, true);
            field(builder, "visible_text", followup.visibleText(), true, 6, true);
            field(builder, "plain_text", followup.plainText(), true, 6, true);
            field(builder, "latency_seconds", String.valueOf(followup.latencySeconds()), false, 6, true);
            field(builder, "is_target_user", String.valueOf(followup.targetUser()), false, 6, true);
            arrayField(builder, "quote_target_ids", followup.quoteTargetIds(), 6, false);
            builder.append("    }").append(i + 1 < followups.size() ? "," : "").append('\n');
        }
        builder.append("  ],\n");
    }

    private void scoresField(StringBuilder builder) {
        builder.append("  \"scores\": {\n");
        field(builder, "asi", String.valueOf(scores.asi()), false, 4, true);
        field(builder, "behavior_score", String.valueOf(scores.behaviorScore()), false, 4, true);
        field(builder, "relational_score", String.valueOf(scores.relationalScore()), false, 4, true);
        field(builder, "friction_score", String.valueOf(scores.frictionScore()), false, 4, false);
        builder.append("  }\n");
    }

    private static void objectStart(StringBuilder builder, String name, int indent) {
        indent(builder, indent).append('"').append(name).append("\": {\n");
    }

    private static void objectEnd(StringBuilder builder, int indent, boolean comma) {
        indent(builder, indent).append('}').append(comma ? "," : "").append('\n');
    }

    private static void arrayField(StringBuilder builder, String name, List<String> values, int indent, boolean comma) {
        indent(builder, indent).append('"').append(name).append("\": [");
        List<String> safeValues = values == null ? List.of() : values;
        for (int i = 0; i < safeValues.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append('"').append(JsonText.escape(safeValues.get(i))).append('"');
        }
        builder.append(']').append(comma ? "," : "").append('\n');
    }

    private static void field(StringBuilder builder, String name, String value, boolean quote, int indent, boolean comma) {
        indent(builder, indent).append('"').append(name).append("\": ");
        if (quote) {
            builder.append('"').append(JsonText.escape(value == null ? "" : value)).append('"');
        } else {
            builder.append(value == null || value.isBlank() ? "0" : value);
        }
        builder.append(comma ? "," : "").append('\n');
    }

    private static StringBuilder indent(StringBuilder builder, int count) {
        return builder.append(" ".repeat(Math.max(0, count)));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
