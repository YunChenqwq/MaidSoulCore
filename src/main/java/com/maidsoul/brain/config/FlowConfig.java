package com.maidsoul.brain.config;

import java.nio.file.Path;
import java.util.Properties;

public record FlowConfig(
        int historyWindow,
        long messageDebounceMillis,
        int maxInternalRounds,
        boolean enableIndependentTimingGate,
        int defaultWaitSeconds,
        double talkFrequency,
        int plannerInterruptMaxConsecutiveCount,
        long timingGateNonContinueCooldownMillis,
        boolean directReplyOnUserMessage,
        boolean enableProactiveRhythm,
        int proactiveInputProtectionSeconds,
        int proactiveLightFollowupAfterSeconds,
        int proactiveTopicPushAfterSeconds,
        int proactiveWorldObserveAfterSeconds,
        int proactiveIdleMinIntervalSeconds
) {
    public static FlowConfig load(Path path) {
        Properties p = ConfigFiles.load(path);
        return new FlowConfig(
                ConfigFiles.integer(p, "historyWindow", 36),
                ConfigFiles.number(p, "messageDebounceMillis", 800),
                ConfigFiles.integer(p, "maxInternalRounds", 4),
                ConfigFiles.bool(p, "enableIndependentTimingGate", false),
                ConfigFiles.integer(p, "defaultWaitSeconds", 8),
                ConfigFiles.decimal(p, "talkFrequency", 1.0),
                ConfigFiles.integer(p, "plannerInterruptMaxConsecutiveCount", 2),
                ConfigFiles.number(p, "timingGateNonContinueCooldownMillis", 3000),
                ConfigFiles.bool(p, "directReplyOnUserMessage", false),
                ConfigFiles.bool(p, "enableProactiveRhythm", true),
                ConfigFiles.integer(p, "proactiveInputProtectionSeconds", 12),
                ConfigFiles.integer(p, "proactiveLightFollowupAfterSeconds", 30),
                ConfigFiles.integer(p, "proactiveTopicPushAfterSeconds", 75),
                ConfigFiles.integer(p, "proactiveWorldObserveAfterSeconds", 180),
                ConfigFiles.integer(p, "proactiveIdleMinIntervalSeconds", 300)
        );
    }
}
