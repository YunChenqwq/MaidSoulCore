package com.maidsoul.brain.config;

import java.nio.file.Path;
import java.util.Properties;

public record IdentityConfig(
        String botName,
        String aliases,
        String ownerName,
        String personality,
        String replyStyle
) {
    public static IdentityConfig load(Path path) {
        Properties p = ConfigFiles.load(path);
        return new IdentityConfig(
                ConfigFiles.text(p, "bot.name", "灵汐"),
                ConfigFiles.text(p, "bot.aliases", ""),
                ConfigFiles.text(p, "owner.name", "主人"),
                ConfigFiles.text(p, "personality", "你是一位自然聊天的女仆。"),
                ConfigFiles.text(p, "reply.style", "自然、简短、像真人聊天。")
        );
    }

    public String renderPrompt() {
        String aliasText = aliases == null || aliases.isBlank() ? "" : "，也有人叫你" + aliases;
        return "你的名字是" + botName + aliasText + "。\n" + personality;
    }
}

