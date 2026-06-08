package com.yunchen.maidsoulcore.forge.tlm.llm;

import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.LLMCallback;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.LLMClient;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.LLMMessage;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.Role;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.yunchen.maidsoulcore.core.config.DialogueConfigLoader;
import com.yunchen.maidsoulcore.core.config.DialogueCoreConfig;
import com.yunchen.maidsoulcore.forge.debug.OwnerChatDebugEcho;
import com.yunchen.maidsoulcore.forge.runtime.MaidRuntimeRegistry;
import net.minecraftforge.fml.loading.FMLPaths;

import java.util.List;
import java.util.regex.Pattern;

public final class MaidSoulRuntimeClient implements LLMClient {
    private static final Pattern CONTEXT_BLOCK = Pattern.compile("^\\s*<context>[\\s\\S]*?</context>\\s*", Pattern.CASE_INSENSITIVE);

    @Override
    public void chat(LLMCallback callback) {
        if (callback == null || callback.getMaid() == null) {
            return;
        }
        EntityMaid maid = callback.getMaid();
        DialogueCoreConfig config = DialogueConfigLoader.loadOrCreate(FMLPaths.CONFIGDIR.get().resolve("maidsoulcore").resolve("dialogue-config.json"));
        if (config.debug != null && config.debug.echoRawTlmMessages) {
            echoRawMessages(maid, config, callback.getMessages());
        }
        String latestUserMessage = extractLatestUserMessage(callback.getMessages());
        if (!latestUserMessage.isBlank()) {
            MaidRuntimeRegistry.receiveOwnerChat(maid, latestUserMessage);
        }
        callback.runOnServerThread(() -> maid.getChatBubbleManager().removeChatBubble(callback.getWaitingChatBubbleId()));
    }

    private static void echoRawMessages(EntityMaid maid, DialogueCoreConfig config, List<LLMMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        int start = Math.max(0, messages.size() - 4);
        for (int i = start; i < messages.size(); i++) {
            LLMMessage message = messages.get(i);
            if (message != null) {
                OwnerChatDebugEcho.echo(maid, config, "tlm.raw", message.role() + " | " + sanitize(message.message()));
            }
        }
    }

    private static String extractLatestUserMessage(List<LLMMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        for (int index = messages.size() - 1; index >= 0; index--) {
            LLMMessage message = messages.get(index);
            if (message != null && message.role() == Role.USER && message.message() != null) {
                String text = stripTlmContext(sanitize(message.message()));
                if (!text.isBlank() && isRealOwnerMessage(text)) {
                    return text;
                }
            }
        }
        return "";
    }

    private static String stripTlmContext(String text) {
        return CONTEXT_BLOCK.matcher(text == null ? "" : text).replaceFirst("").trim();
    }

    private static boolean isRealOwnerMessage(String text) {
        String normalized = text.strip();
        return !normalized.startsWith("Time:")
                && !normalized.startsWith("Weather:")
                && !normalized.contains("Nearby entities:");
    }

    private static String sanitize(String text) {
        return text == null ? "" : text
                .replace('\r', ' ')
                .replace('\n', ' ')
                .replace('\t', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }
}
