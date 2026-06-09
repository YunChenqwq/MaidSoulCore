package com.yunchen.maidsoulcore.forge.config;

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
                boolFromFileOrForge(p, "echoTraceToOwnerChat", MaidSoulForgeConfig.ECHO_TRACE_TO_OWNER_CHAT.get()),
                boolFromFileOrForge(p, "echoAffectToOwnerChat", MaidSoulForgeConfig.ECHO_AFFECT_TO_OWNER_CHAT.get()),
                boolFromFileOrForge(p, "echoReplyToOwnerChat", MaidSoulForgeConfig.ECHO_REPLY_TO_OWNER_CHAT.get()),
                ConfigFiles.integer(p, "maxChatEchoChars", 220)
        );
    }

    private static boolean boolFromFileOrForge(Properties properties, String key, boolean forgeValue) {
        if (!properties.containsKey(key)) {
            return forgeValue;
        }
        return ConfigFiles.bool(properties, key, false) || forgeValue;
    }
}


