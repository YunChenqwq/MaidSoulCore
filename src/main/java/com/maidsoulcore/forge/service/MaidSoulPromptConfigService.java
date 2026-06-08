package com.maidsoulcore.forge.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.maidsoulcore.forge.config.MaidSoulPromptTemplateConfig;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 提示词 JSON 配置服务。
 * <p>
 * 规则如下：
 * 1. 首次启动如果不存在配置文件，则自动写入 `config/maidsoulcore-prompts.json`；
 * 2. 后续每次运行优先读取这个 JSON，方便你直接改文案；
 * 3. 如果用户本地 JSON 缺字段、字段为 null、或读取失败，则自动回退到默认模板对应字段；
 * 4. 这样升级 prompt 结构时，不会因为老配置文件缺字段导致运行时报空指针。
 */
public final class MaidSoulPromptConfigService {
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();
    private static final Path CONFIG_PATH = Path.of("config", "maidsoulcore-prompts.json");

    private static volatile MaidSoulPromptTemplateConfig cached;

    private MaidSoulPromptConfigService() {
    }

    /**
     * 读取提示词模板配置。
     */
    public static MaidSoulPromptTemplateConfig load() {
        MaidSoulPromptTemplateConfig local = cached;
        if (local != null) {
            return local;
        }
        synchronized (MaidSoulPromptConfigService.class) {
            if (cached != null) {
                return cached;
            }
            cached = loadInternal();
            return cached;
        }
    }

    /**
     * 强制重新读取。
     * <p>
     * 后续如果你想做“游戏内重载提示词”，可以复用这个入口。
     */
    public static MaidSoulPromptTemplateConfig reload() {
        synchronized (MaidSoulPromptConfigService.class) {
            cached = loadInternal();
            return cached;
        }
    }

    /**
     * 真正执行读取逻辑，并把缺失字段与默认模板合并。
     */
    private static MaidSoulPromptTemplateConfig loadInternal() {
        try {
            ensureDefaultFileExists();
            String json = Files.readString(CONFIG_PATH, StandardCharsets.UTF_8);
            MaidSoulPromptTemplateConfig loaded = GSON.fromJson(json, MaidSoulPromptTemplateConfig.class);
            return mergeWithDefaults(loaded);
        } catch (Exception exception) {
            return MaidSoulPromptTemplateConfig.defaults();
        }
    }

    /**
     * 如果配置文件不存在，则写入默认配置。
     */
    private static void ensureDefaultFileExists() throws IOException {
        if (Files.exists(CONFIG_PATH)) {
            return;
        }
        Files.createDirectories(CONFIG_PATH.getParent());
        Files.writeString(
                CONFIG_PATH,
                GSON.toJson(MaidSoulPromptTemplateConfig.defaults()),
                StandardCharsets.UTF_8
        );
    }

    /**
     * 将外部加载结果与默认模板做字段级合并。
     * <p>
     * 这样做是为了兼容旧版 JSON：
     * - 老配置没有新增字段时，自动用默认值补齐；
     * - 已存在的用户自定义字段仍然保留。
     */
    private static MaidSoulPromptTemplateConfig mergeWithDefaults(MaidSoulPromptTemplateConfig loaded) {
        MaidSoulPromptTemplateConfig defaults = MaidSoulPromptTemplateConfig.defaults();
        if (loaded == null) {
            return defaults;
        }
        return new MaidSoulPromptTemplateConfig(
                pick(loaded.personaRules(), defaults.personaRules()),
                pick(loaded.proactiveRules(), defaults.proactiveRules()),
                pick(loaded.proactiveNeedRules(), defaults.proactiveNeedRules()),
                pick(loaded.eventInterpretationRules(), defaults.eventInterpretationRules()),
                pick(loaded.plannerRules(), defaults.plannerRules()),
                pick(loaded.replyRules(), defaults.replyRules()),
                pick(loaded.toolLoopRules(), defaults.toolLoopRules()),
                pick(loaded.visionRules(), defaults.visionRules()),
                pick(loaded.exampleEventHints(), defaults.exampleEventHints()),
                pick(loaded.timingGateRules(), defaults.timingGateRules()),
                pick(loaded.historyRules(), defaults.historyRules()),
                pick(loaded.expressionRules(), defaults.expressionRules())
        );
    }

    /**
     * 选择优先使用外部配置，缺失时回退默认值。
     */
    private static java.util.List<String> pick(java.util.List<String> loaded, java.util.List<String> fallback) {
        return loaded == null || loaded.isEmpty() ? fallback : loaded;
    }
}
