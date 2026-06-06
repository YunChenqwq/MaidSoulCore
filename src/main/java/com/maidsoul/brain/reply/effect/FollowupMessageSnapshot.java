package com.maidsoul.brain.reply.effect;

import java.time.Instant;

/**
 * 回复之后观察到的用户后续消息。
 *
 * <p>这是从 上游参考系统 的 FollowupMessageSnapshot 收敛过来的轻量版本。
 * 原型机暂时没有平台、引用、图片等完整消息结构，所以先保留做行为评分真正需要的字段。</p>
 */
public record FollowupMessageSnapshot(
        String messageId,
        Instant timestamp,
        String userId,
        String nickname,
        String visibleText,
        String plainText,
        double latencySeconds,
        boolean targetUser,
        java.util.List<String> quoteTargetIds
) {
    public FollowupMessageSnapshot(
            String messageId,
            Instant timestamp,
            String userId,
            String plainText,
            double latencySeconds,
            boolean targetUser
    ) {
        this(messageId, timestamp, userId, userId, plainText, plainText, latencySeconds, targetUser, java.util.List.of());
    }
}
