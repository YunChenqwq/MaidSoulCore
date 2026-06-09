package com.yunchen.maidsoulcore.forge.tlm.llm;

import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.LLMCallback;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.AutoGenSettingCallback;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.UserPromptContexts;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.LLMClient;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.LLMMessage;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.Role;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.yunchen.maidsoulcore.MaidSoulCoreMod;
import com.yunchen.maidsoulcore.forge.config.ForgeBrainConfigInstaller;
import com.yunchen.maidsoulcore.forge.config.ForgeDebugOptions;
import com.yunchen.maidsoulcore.forge.debug.OwnerChatDebugEcho;
import com.yunchen.maidsoulcore.forge.runtime.MaidBrainRuntimeRegistry;
import com.yunchen.maidsoulcore.forge.speech.MaidSpeechDispatcher;
import com.yunchen.maidsoulcore.forge.tlm.MaidSoulTlmBootstrapper;

import java.util.List;

public final class MaidSoulRuntimeClient implements LLMClient {
    private final MaidSoulRuntimeSite site;

    public MaidSoulRuntimeClient(MaidSoulRuntimeSite site) {
        this.site = site;
    }

    @Override
    public void chat(LLMCallback callback) {
        if (callback == null || callback.getMaid() == null) {
            return;
        }
        EntityMaid maid = callback.getMaid();
        try {
            MaidSoulTlmBootstrapper.ensureMaidSoulRuntime(maid);
            ForgeDebugOptions debug = ForgeDebugOptions.load(ForgeBrainConfigInstaller.configRoot());
            MaidSoulCoreMod.LOGGER.info("MaidSoulRuntimeClient.chat called. maid={} llmSite={} llmModel={} availableRuntimeSite={} messages={}",
                    maid.getUUID(),
                    callback.getChatManager().llmSite,
                    callback.getChatManager().llmModel,
                    com.github.tartaricacid.touhoulittlemaid.ai.manager.site.AvailableSites.LLM_SITES.containsKey(MaidSoulRuntimeSite.API_TYPE),
                    callback.getMessages() == null ? 0 : callback.getMessages().size());
            OwnerChatDebugEcho.echo(maid, debug, "tlm.entry", "MaidSoulRuntimeClient.chat called");
            if (callback instanceof AutoGenSettingCallback || isTlmAutoGenSettingPrompt(callback.getMessages())) {
                installMaidSoulSentinelSetting(callback);
                return;
            }
            String latestUserMessage = extractLatestUserMessage(callback.getMessages());
            if (!latestUserMessage.isBlank()) {
                MaidBrainRuntimeRegistry.receiveOwnerChat(maid, latestUserMessage, callback);
            } else {
                MaidSoulCoreMod.LOGGER.warn("MaidSoul runtime received an LLM callback without a real owner message. maid={}", maid.getUUID());
                MaidSpeechDispatcher.queueSpeechOnServer(maid, "我听到了调用，但没读到真正的主人消息。检查一下 TLM 的模型站点配置。");
            }
        } catch (Exception e) {
            MaidSoulCoreMod.LOGGER.error("MaidSoul runtime bridge failed. maid={}", maid.getUUID(), e);
            MaidSpeechDispatcher.queueSpeechOnServer(maid, "我的聊天桥接出错了，日志里有具体原因。");
        }
    }

    private static void installMaidSoulSentinelSetting(LLMCallback callback) {
        EntityMaid maid = callback.getMaid();
        MaidSoulTlmBootstrapper.ensureMaidSoulRuntime(maid);
        callback.getChatManager().customSetting = MaidSoulTlmBootstrapper.SENTINEL_SETTING;
        callback.runOnServerThread(() -> {
            maid.getChatBubbleManager().removeChatBubble(callback.getWaitingChatBubbleId());
        });
        MaidSoulCoreMod.LOGGER.info("Installed MaidSoulCore sentinel setting for maid {}, bypassing TLM auto setting generation.", maid.getUUID());
    }

    private static String extractLatestUserMessage(List<LLMMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        for (int index = messages.size() - 1; index >= 0; index--) {
            LLMMessage message = messages.get(index);
            if (message != null && message.role() == Role.USER && message.message() != null) {
                String text = sanitize(UserPromptContexts.removeContext(message.message()));
                if (!text.isBlank() && isRealOwnerMessage(text)) {
                    return text;
                }
            }
        }
        return "";
    }

    private static boolean isRealOwnerMessage(String text) {
        String normalized = text.strip();
        return !normalized.startsWith("Time:")
                && !normalized.startsWith("Weather:")
                && !normalized.contains("Nearby entities:")
                && !looksLikeTlmAutoGenSetting(normalized);
    }

    private static boolean isTlmAutoGenSettingPrompt(List<LLMMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return false;
        }
        for (LLMMessage message : messages) {
            if (message != null && message.message() != null && looksLikeTlmAutoGenSetting(message.message())) {
                return true;
            }
        }
        return false;
    }

    private static boolean looksLikeTlmAutoGenSetting(String text) {
        String normalized = text == null ? "" : text.toLowerCase();
        return normalized.contains("model_name")
                || normalized.contains("chat_language")
                || normalized.contains("auto gen")
                || normalized.contains("generate the maid")
                || normalized.contains("generate maid")
                || normalized.contains("character setting");
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
