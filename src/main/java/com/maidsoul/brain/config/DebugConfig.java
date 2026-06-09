package com.maidsoul.brain.config;

import java.nio.file.Path;
import java.util.Properties;

public record DebugConfig(
        boolean enableConsoleTrace,
        boolean recordPrompt,
        int maxTraceChars
) {
    public static DebugConfig load(Path path) {
        Properties p = ConfigFiles.load(path);
        return new DebugConfig(
                ConfigFiles.bool(p, "enableConsoleTrace", true),
                ConfigFiles.bool(p, "recordPrompt", false),
                ConfigFiles.integer(p, "maxTraceChars", 500)
        );
    }
}

