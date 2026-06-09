package com.yunchen.maidsoulcore.forge.config;

import com.yunchen.maidsoulcore.core.config.DialogueConfigLoader;
import com.yunchen.maidsoulcore.core.config.DialogueCoreConfig;

import java.nio.file.Path;

public record ForgeDebugOptions(
        boolean echoTraceToOwnerChat,
        boolean echoAffectToOwnerChat,
        boolean echoReplyToOwnerChat,
        int maxChatEchoChars
) {
    public static ForgeDebugOptions load(Path configRoot) {
        DialogueCoreConfig config = DialogueConfigLoader.loadOrCreate(configRoot.resolve("dialogue-config.json"));
        return new ForgeDebugOptions(
                config.debug.echoTraceToOwnerChat || MaidSoulForgeConfig.ECHO_TRACE_TO_OWNER_CHAT.get(),
                config.debug.echoAffectToOwnerChat || MaidSoulForgeConfig.ECHO_AFFECT_TO_OWNER_CHAT.get(),
                config.debug.echoReplyToOwnerChat || MaidSoulForgeConfig.ECHO_REPLY_TO_OWNER_CHAT.get(),
                config.debug.maxChatEchoChars <= 0 ? 220 : config.debug.maxChatEchoChars
        );
    }
}
