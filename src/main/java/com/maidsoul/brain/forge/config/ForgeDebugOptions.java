package com.maidsoul.brain.forge.config;

import com.maidsoul.brain.config.ConfigFiles;

import java.nio.file.Path;
import java.util.Properties;

public record ForgeDebugOptions(
        boolean echoTraceToOwnerChat,
        boolean echoAffectToOwnerChat,
        boolean echoReplyToOwnerChat,
        int maxChatEchoChars
) {
    public static ForgeDebugOptions load(Path configRoot) {
        Properties p = ConfigFiles.load(configRoot.resolve("debug").resolve("trace.properties"));
        return new ForgeDebugOptions(
                ConfigFiles.bool(p, "echoTraceToOwnerChat", false),
                ConfigFiles.bool(p, "echoAffectToOwnerChat", false),
                ConfigFiles.bool(p, "echoReplyToOwnerChat", false),
                ConfigFiles.integer(p, "maxChatEchoChars", 220)
        );
    }
}
