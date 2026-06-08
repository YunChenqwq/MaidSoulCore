package com.maidsoulcore.forge.service;

import com.github.tartaricacid.touhoulittlemaid.ai.manager.site.AvailableSites;
import com.github.tartaricacid.touhoulittlemaid.ai.service.SerializerRegister;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.openai.LLMOpenAISite;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.maidsoulcore.forge.config.MaidSoulCommonConfig;
import com.maidsoulcore.forge.MaidSoulCoreMod;
import com.maidsoulcore.forge.tlm.llm.MaidSoulRuntimeSite;
import com.maidsoulcore.sim.SimulationMaiBotConfigLoader;
import com.maidsoulcore.sim.SimulationMaiBotRuntimeConfig;
import com.maidsoulcore.sim.SimulationResolvedModel;
import net.minecraft.resources.ResourceLocation;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * TLM LLM 站点与女仆聊天预设同步服务。
 * <p>
 * 它负责两件核心工作：
 * 1. 把 MaiBot 模型配置同步成一个 TLM 可见、但实际由 MaidSoulCore runtime 驱动的 LLM site；
 * 2. 把 MaidSoulCore 的角色设定写入女仆 `customSetting`，并绑定到刚同步的站点与模型。
 * <p>
 * 这样做以后，玩家打开 TLM 原版聊天界面时，就已经是在和 MaidSoulCore 预设好的女仆聊天。
 */
public final class MaidSoulSiteService {
    private static final ConcurrentMap<UUID, Integer> APPLIED_PROMPT_HASH = new ConcurrentHashMap<>();
    private static volatile boolean siteSynchronized = false;
    private static volatile int synchronizedSiteHash = 0;

    private MaidSoulSiteService() {
    }

    /**
     * 确保 TLM 里存在一份来自 MaiBot 配置的 MaidSoulCore runtime 站点。
     * <p>
     * 这里同步的不是“原版 OpenAI 站点”，而是一个自定义站点壳：
     * - 让 TLM UI 能正常选择、保存与加载；
     * - 但实际聊天推理走 MaidSoulCore 自己的 planner + reply runtime。
     */
    public static void synchronizeTlmSiteFromMaiBot() {
        if (!MaidSoulCommonConfig.AUTO_SYNC_TLM_SITE.get()) {
            return;
        }
        SimulationMaiBotRuntimeConfig runtimeConfig = loadRuntimeConfig();
        if (!runtimeConfig.available()) {
            return;
        }
        SimulationResolvedModel resolved = runtimeConfig.resolveTask(runtimeConfig.replyTask());
        int siteHash = Objects.hash(
                MaidSoulCommonConfig.TLM_SITE_ID.get(),
                resolved.providerConfig().baseUrl(),
                resolved.modelConfig().modelIdentifier()
        );
        if (siteSynchronized && synchronizedSiteHash == siteHash) {
            return;
        }
        MaidSoulRuntimeSite site = buildSite(runtimeConfig, resolved);
        AvailableSites.LLM_SITES.put(site.id(), site);
        if (SerializerRegister.getLLMSerializer(site.getApiType()) != null) {
            AvailableSites.saveSites();
        }
        siteSynchronized = true;
        synchronizedSiteHash = siteHash;
    }

    /**
     * 对单只女仆应用聊天绑定预设。
     * <p>
     * 这里会在服务端持续兜底，保证：
     * - 使用我们同步进去的 TLM 站点；
     * - 使用 MaiBot reply 模型；
     * - 使用 MaidSoulCore 生成的 customSetting。
     */
    public static void ensureChatPreset(EntityMaid maid) {
        if (!MaidSoulCommonConfig.AUTO_APPLY_CHAT_PRESET.get()) {
            return;
        }
        synchronizeTlmSiteFromMaiBotIfNeeded();
        SimulationMaiBotRuntimeConfig runtimeConfig = loadRuntimeConfig();
        if (!runtimeConfig.available()) {
            return;
        }
        SimulationResolvedModel resolved = runtimeConfig.resolveTask(runtimeConfig.replyTask());
        String customSetting = MaidSoulPromptService.buildTlmCustomSetting(maid, runtimeConfig);
        int promptHash = Objects.hash(
                customSetting,
                resolved.modelConfig().modelIdentifier(),
                MaidSoulCommonConfig.TLM_SITE_ID.get()
        );
        Integer previous = APPLIED_PROMPT_HASH.get(maid.getUUID());
        if (previous != null && previous == promptHash) {
            return;
        }

        maid.getAiChatManager().llmSite = MaidSoulCommonConfig.TLM_SITE_ID.get();
        maid.getAiChatManager().llmModel = resolved.modelConfig().modelIdentifier();
        maid.getAiChatManager().ownerName = maid.getOwner() == null ? "" : maid.getOwner().getName().getString();
        maid.getAiChatManager().customSetting = customSetting;
        APPLIED_PROMPT_HASH.put(maid.getUUID(), promptHash);
    }

    /**
     * 为服务端启动或懒初始化场景提供一次性同步。
     */
    public static void synchronizeTlmSiteFromMaiBotIfNeeded() {
        synchronizeTlmSiteFromMaiBot();
    }

    /**
     * 基于 MaiBot reply 槽位生成一个 MaidSoulCore runtime 站点。
     */
    private static MaidSoulRuntimeSite buildSite(SimulationMaiBotRuntimeConfig runtimeConfig, SimulationResolvedModel resolved) {
        Map<String, String> headers = new LinkedHashMap<>();
        return new MaidSoulRuntimeSite(
                MaidSoulCommonConfig.TLM_SITE_ID.get(),
                new ResourceLocation(MaidSoulCoreMod.MOD_ID, "textures/gui/maidsoulcore.png"),
                blankToDefault(resolved.providerConfig().baseUrl(), "maidsoul://runtime"),
                true,
                headers,
                java.util.List.of(resolved.modelConfig().modelIdentifier())
        );
    }

    /**
     * 基于 MaiBot 某个任务槽位临时构建真实 OpenAI 兼容 site。
     * <p>
     * 用于命令型 tool loop，直接让 TLM 原生 tool 链走到真实模型。
     */
    public static LLMOpenAISite buildOpenAiCompatSite(String siteId, SimulationResolvedModel resolvedModel) {
        Map<String, String> headers = new LinkedHashMap<>();
        String modelIdentifier = resolvedModel.modelConfig().modelIdentifier();
        return new LLMOpenAISite(
                siteId,
                new ResourceLocation(MaidSoulCoreMod.MOD_ID, "textures/gui/maidsoulcore.png"),
                blankToDefault(resolvedModel.providerConfig().baseUrl(), "https://api.openai.com/v1/chat/completions"),
                true,
                blankToDefault(resolvedModel.providerConfig().apiKey(), ""),
                looksLikeReasoningModel(modelIdentifier),
                headers,
                java.util.List.of(new LLMOpenAISite.ModelEntry(modelIdentifier, looksLikeReasoningModel(modelIdentifier)))
        );
    }

    /**
     * 读取完整 MaiBot 运行时配置。
     */
    private static SimulationMaiBotRuntimeConfig loadRuntimeConfig() {
        return SimulationMaiBotConfigLoader.loadFromDirectory(Path.of(MaidSoulCommonConfig.MAIBOT_CONFIG_DIR.get()));
    }

    private static String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static boolean looksLikeReasoningModel(String modelIdentifier) {
        if (modelIdentifier == null || modelIdentifier.isBlank()) {
            return false;
        }
        String text = modelIdentifier.toLowerCase(java.util.Locale.ROOT);
        return text.contains("gpt-5")
                || text.contains("glm-5")
                || text.contains("r1")
                || text.contains("reason");
    }
}
