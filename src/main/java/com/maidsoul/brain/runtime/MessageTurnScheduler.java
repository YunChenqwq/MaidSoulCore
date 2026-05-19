package com.maidsoul.brain.runtime;

/**
 * 消息流触发调度器。
 *
 * <p>这部分复刻 maibotdev 的核心控频思想：说话频率不是直接决定“说不说”，
 * 而是折算成“积累多少条新消息才唤醒一次主循环”。如果消息不足，但已经空了一段时间，
 * 则用最近平均回复耗时做空窗补偿，避免低频配置下私聊一直不触发。</p>
 */
public final class MessageTurnScheduler {
    public Decision decide(
            int pendingCount,
            double talkFrequency,
            boolean forced,
            long idleMillis,
            Long averageReplyLatencyMillis
    ) {
        if (pendingCount <= 0) {
            return Decision.skip();
        }
        if (forced) {
            return Decision.triggerNow("强制触发");
        }

        int threshold = messageTriggerThreshold(talkFrequency);
        if (pendingCount >= threshold) {
            return Decision.triggerNow("待处理消息数达到触发阈值 " + pendingCount + "/" + threshold);
        }
        if (averageReplyLatencyMillis == null || averageReplyLatencyMillis <= 0) {
            return Decision.skip();
        }

        double equivalentMessageCount = pendingCount + idleMillis / (double) averageReplyLatencyMillis;
        if (equivalentMessageCount >= threshold) {
            return Decision.triggerNow("空窗补偿达到触发阈值 "
                    + String.format(java.util.Locale.ROOT, "%.2f", equivalentMessageCount)
                    + "/" + threshold);
        }

        long missingMillis = Math.round((threshold - equivalentMessageCount) * averageReplyLatencyMillis);
        return Decision.delay(Math.max(1L, missingMillis), "等待空窗补偿");
    }

    public int messageTriggerThreshold(double talkFrequency) {
        double effectiveFrequency = Math.max(0.01, Math.min(1.0, talkFrequency));
        return Math.max(1, (int) Math.ceil(1.0 / effectiveFrequency));
    }

    public record Decision(boolean triggerNow, long delayMillis, String reason) {
        static Decision triggerNow(String reason) {
            return new Decision(true, 0, reason);
        }

        static Decision delay(long delayMillis, String reason) {
            return new Decision(false, delayMillis, reason);
        }

        static Decision skip() {
            return new Decision(false, -1, "");
        }
    }
}
