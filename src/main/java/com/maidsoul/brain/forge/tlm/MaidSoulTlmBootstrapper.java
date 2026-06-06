package com.maidsoul.brain.forge.tlm;

import com.github.tartaricacid.touhoulittlemaid.ai.manager.site.AvailableSites;
import com.github.tartaricacid.touhoulittlemaid.ai.service.SerializableSite;
import com.github.tartaricacid.touhoulittlemaid.ai.service.SerializerRegister;
import com.github.tartaricacid.touhoulittlemaid.ai.service.ServiceType;
import com.github.tartaricacid.touhoulittlemaid.ai.service.Site;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.LLMSite;
import com.github.tartaricacid.touhoulittlemaid.config.subconfig.AIConfig;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.maidsoul.brain.forge.MaidSoulCoreForgeMod;
import com.maidsoul.brain.forge.tlm.llm.MaidSoulRuntimeSite;

import java.util.LinkedHashMap;

/**
 * 把车万女仆的 AI 聊天入口稳定桥接到 MaidSoulCore。
 *
 * <p>这里同时维护三层 TLM 状态：</p>
 * <ul>
 *     <li>SerializerRegister：客户端和服务端解码站点同步包时必须认识 maidsoul_runtime。</li>
 *     <li>AvailableSites：TLM 的 getLLMSite() 只从这里取当前可用站点。</li>
 *     <li>MaidAIChatManager：每只女仆身上保存的 llmSite / llmModel / customSetting。</li>
 * </ul>
 *
 * <p>不能只依赖 {@code @LittleMaidExtension}。TLM 会在 common setup 里重建站点表，
 * 如果我们的注入早于它的 AvailableSites.init()，就会被 clear 掉；如果晚于它的
 * SyncMaidAIDataMessage，客户端聊天框又会看到空站点。这个类的职责就是在所有入口反复兜底，
 * 让 TLM 只负责输入框和气泡显示，真正的人设、记忆、情绪和回复仍然全部走 MaidSoulCore。</p>
 */
public final class MaidSoulTlmBootstrapper {
    public static final String SENTINEL_SETTING = """
            MaidSoulCore runtime bridge is enabled.
            Do not generate Touhou Little Maid character settings here.
            The actual persona, memory, affect, planner and replyer are handled by MaidSoulCore.
            """.trim();

    private MaidSoulTlmBootstrapper() {
    }

    /**
     * 把单只女仆当前的 TLM 聊天配置强制指向 MaidSoulCore。
     *
     * <p>TLM 的聊天框发包后仍会调用 maid.getAiChatManager().chat(...)。所以服务端收到消息前，
     * 这只女仆身上的 llmSite 必须已经是 maidsoul_runtime；否则 TLM 会退回 player2，
     * 然后玩家就会看到“没有可用的 LLM 站点”。</p>
     */
    public static void ensureMaidSoulRuntime(EntityMaid maid) {
        if (maid == null || maid.level().isClientSide()) {
            return;
        }
        ensureTlmChatGateOpen();
        ensureRuntimeSite();
        var manager = maid.getAiChatManager();
        manager.llmSite = MaidSoulRuntimeSite.API_TYPE;
        manager.llmModel = MaidSoulRuntimeSite.DEFAULT_MODEL;
        manager.customSetting = SENTINEL_SETTING;
    }

    /**
     * TLM 原生聊天框在调用站点 client() 之前，会先检查它自己的 AI 开关和角色设定生成逻辑。
     *
     * <p>我们的目标是“只借用 TLM 的聊天框，不借用它的 LLM/人设逻辑”。如果玩家曾经在 TLM
     * 配置里关掉 LLM，或者开启了自动生成人设，消息会在进入 MaidSoulCore 前就被拦住。
     * 因此这里在接管女仆时把必要的入口闸门打开，并关闭 TLM 的自动人设生成。</p>
     */
    private static void ensureTlmChatGateOpen() {
        try {
            if (AIConfig.LLM_ENABLED != null && !AIConfig.LLM_ENABLED.get()) {
                AIConfig.LLM_ENABLED.set(true);
            }
            if (AIConfig.AUTO_GEN_SETTING_ENABLED != null && AIConfig.AUTO_GEN_SETTING_ENABLED.get()) {
                AIConfig.AUTO_GEN_SETTING_ENABLED.set(false);
            }
        } catch (RuntimeException e) {
            MaidSoulCoreForgeMod.LOGGER.warn("Failed to adjust TLM AI chat gate for MaidSoulCore bridge.", e);
        }
    }

    /**
     * 保证 TLM 的可用站点表里存在并启用 maidsoul_runtime。
     *
     * <p>这个方法故意写成幂等：站点存在时不会重复创建，站点被 TLM 配置保存/重载清掉时会补回。
     * 这样玩家不需要手动在 TLM 的 LLM 站点配置里创建任何东西。</p>
     */
    public static void ensureRuntimeSite() {
        ensureRuntimeSerializer();
        LLMSite site = AvailableSites.LLM_SITES.get(MaidSoulRuntimeSite.API_TYPE);
        if (site == null) {
            AvailableSites.LLM_SITES.put(MaidSoulRuntimeSite.API_TYPE, new MaidSoulRuntimeSite.Serializer().defaultSite());
            MaidSoulCoreForgeMod.LOGGER.info("Injected MaidSoulCore runtime site into TLM AvailableSites: {}", MaidSoulRuntimeSite.API_TYPE);
            return;
        }
        if (!site.enabled()) {
            site.setEnabled(true);
            MaidSoulCoreForgeMod.LOGGER.info("Re-enabled MaidSoulCore runtime site in TLM AvailableSites: {}", MaidSoulRuntimeSite.API_TYPE);
        }
    }

    /**
     * 保证 TLM 的站点序列化器认识 maidsoul_runtime。
     *
     * <p>TLM 的 SyncAISitesMessage 会通过 apiType 反序列化站点；如果客户端没有这个 serializer，
     * 同步包里的站点会被丢弃，聊天框右侧 LLM 下拉框就会变空。某些阶段 TLM 会把 serializer map
     * 冻结成 ImmutableMap，所以这里发现 put 失败时会复制成新的 LinkedHashMap 再替换回去。</p>
     */
    public static void ensureRuntimeSerializer() {
        if (SerializerRegister.getSerializer(ServiceType.LLM, MaidSoulRuntimeSite.API_TYPE) != null) {
            return;
        }
        try {
            SerializerRegister.LLM_SERIALIZER.put(MaidSoulRuntimeSite.API_TYPE, runtimeSerializer());
        } catch (UnsupportedOperationException ignored) {
            SerializerRegister.LLM_SERIALIZER = new LinkedHashMap<>(SerializerRegister.LLM_SERIALIZER);
            SerializerRegister.LLM_SERIALIZER.put(MaidSoulRuntimeSite.API_TYPE, runtimeSerializer());
        }
        MaidSoulCoreForgeMod.LOGGER.info("Injected MaidSoulCore runtime serializer into TLM SerializerRegister: {}", MaidSoulRuntimeSite.API_TYPE);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static SerializableSite<LLMSite> runtimeSerializer() {
        // TLM 的注册表类型是 SerializableSite<LLMSite>，但具体 serializer 自然会返回具体站点实现。
        // 这里等价于 TLM 自己 register(...) 内部的泛型擦除转换。
        return (SerializableSite<LLMSite>) (SerializableSite<? extends Site>) new MaidSoulRuntimeSite.Serializer();
    }
}
