package com.maidsoul.brain.config;

import java.nio.file.Path;
import java.util.Properties;

public record SplitterConfig(
        boolean enable,
        int maxLength,
        int maxSentenceNum,
        int minSegmentLength,
        long bubbleDelayMillis
) {
    public static SplitterConfig load(Path path) {
        Properties p = ConfigFiles.load(path);
        return new SplitterConfig(
                ConfigFiles.bool(p, "enable", true),
                ConfigFiles.integer(p, "maxLength", 90),
                ConfigFiles.integer(p, "maxSentenceNum", 6),
                ConfigFiles.integer(p, "minSegmentLength", 2),
                ConfigFiles.number(p, "bubbleDelayMillis", 550)
        );
    }
}

