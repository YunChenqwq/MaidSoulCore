package com.maidsoul.brain;

import com.maidsoul.brain.message.ChatMessage;
import com.maidsoul.brain.reply.effect.ReplyEffectStorage;
import com.maidsoul.brain.reply.effect.ReplyEffectTracker;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 回复效果追踪器测试。
 */
public final class ReplyEffectTrackerSmokeTest {
    private ReplyEffectTrackerSmokeTest() {
    }

    public static void main(String[] args) throws Exception {
        Path tempDir = Files.createTempDirectory("maidsoul-reply-effect");
        ReplyEffectTracker tracker = new ReplyEffectTracker(
                "test-session",
                "测试会话",
                new ReplyEffectStorage(tempDir)
        );
        ChatMessage target = ChatMessage.user("用户A", "你怎么看？");
        tracker.recordReply(target, "我先试着回答一下。", List.of("我先试着回答一下。"), "测试回复", "");
        tracker.observeUserMessage(ChatMessage.user("用户A", "不是这个意思，你没懂"));
        ReplyEffectTracker.ReplyEffectSummary summary = tracker.latestSummary();
        if (!"explicit_negative".equals(summary.finalizeReason()) || !summary.explicitNegative()) {
            throw new IllegalStateException("负反馈 pattern 没有按 上游参考系统 规则结算: " + summary);
        }
        long jsonCount;
        try (var stream = Files.walk(tempDir)) {
            jsonCount = stream.filter(path -> path.toString().endsWith(".json")).count();
        }
        if (jsonCount <= 0) {
            throw new IllegalStateException("reply_effect 没有落盘 JSON。");
        }
        System.out.println("REPLY_EFFECT_TRACKER_SMOKE_OK");
    }
}
