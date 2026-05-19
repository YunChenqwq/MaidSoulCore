package com.maidsoul.brain.reply.effect;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 等待观察的回复效果记录。
 *
 * <p>每次可见回复成功发出后创建一条记录；之后用户继续说话时，追踪器把后续消息追加进来，
 * 满足负反馈、修复循环、后续轮数上限等条件后完成评分。</p>
 */
public final class ReplyEffectRecord {
    private final String effectId = UUID.randomUUID().toString();
    private final Instant createdAt = Instant.now();
    private final String targetMessageId;
    private final String targetUserId;
    private final String replyText;
    private final List<String> replySegments;
    private final String plannerReasoning;
    private final String referenceInfo;
    private final List<FollowupMessageSnapshot> followups = new ArrayList<>();

    private boolean finalized;
    private String finalizeReason = "";
    private ReplyEffectScores scores;

    public ReplyEffectRecord(
            String targetMessageId,
            String targetUserId,
            String replyText,
            List<String> replySegments,
            String plannerReasoning,
            String referenceInfo
    ) {
        this.targetMessageId = targetMessageId == null ? "" : targetMessageId;
        this.targetUserId = targetUserId == null ? "" : targetUserId;
        this.replyText = replyText == null ? "" : replyText;
        this.replySegments = List.copyOf(replySegments == null ? List.of() : replySegments);
        this.plannerReasoning = plannerReasoning == null ? "" : plannerReasoning;
        this.referenceInfo = referenceInfo == null ? "" : referenceInfo;
    }

    public String effectId() {
        return effectId;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public String targetMessageId() {
        return targetMessageId;
    }

    public String targetUserId() {
        return targetUserId;
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
        return finalized;
    }

    public String finalizeReason() {
        return finalizeReason;
    }

    public ReplyEffectScores scores() {
        return scores;
    }

    void addFollowup(FollowupMessageSnapshot followup) {
        followups.add(followup);
    }

    void finalizeWith(String reason, ReplyEffectScores scores) {
        this.finalized = true;
        this.finalizeReason = reason == null ? "" : reason;
        this.scores = scores;
    }
}
