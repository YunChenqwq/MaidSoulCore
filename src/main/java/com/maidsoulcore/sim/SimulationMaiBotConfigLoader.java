package com.maidsoulcore.sim;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SimulationMaiBotConfigLoader {
    private static final Path[] CANDIDATE_DIRS = new Path[] {
            Path.of("config"),
            Path.of("E:/wallpaper/MaidSoulCore/config"),
            Path.of("E:/bot/MaiBotOneKey/modules/MaiBot/config")
    };

    private SimulationMaiBotConfigLoader() {
    }

    public static SimulationMaiBotRuntimeConfig load() {
        for (Path dir : CANDIDATE_DIRS) {
            SimulationMaiBotRuntimeConfig loaded = loadFromDirectory(dir);
            if (loaded.available()) {
                return loaded;
            }
        }
        return SimulationMaiBotRuntimeConfig.unavailable(Path.of("config"), "standalone MaiBot config not found");
    }

    /**
     * 从指定目录读取完整 MaiBot 配置。
     * <p>
     * Forge 正式运行时会优先走这个入口，
     * 这样就能明确使用 MaidSoulCore 配置面板里指定的配置目录，而不是依赖硬编码候选路径。
     */
    public static SimulationMaiBotRuntimeConfig loadFromDirectory(Path dir) {
        Path cleanBotConfig = dir.resolve("textgame_bot_config.toml");
        Path botConfig = Files.exists(cleanBotConfig) ? cleanBotConfig : dir.resolve("bot_config.toml");
        Path modelConfig = dir.resolve("model_config.toml");
        if (Files.exists(botConfig) && Files.exists(modelConfig)) {
            return read(dir, botConfig, modelConfig);
        }
        return SimulationMaiBotRuntimeConfig.unavailable(dir, "MaiBot config files not found");
    }

    private static SimulationMaiBotRuntimeConfig read(Path configDir, Path botConfig, Path modelConfig) {
        try (
                CommentedFileConfig bot = CommentedFileConfig.builder(botConfig).sync().build();
                CommentedFileConfig model = CommentedFileConfig.builder(modelConfig).sync().build()
        ) {
            bot.load();
            model.load();

            Map<String, SimulationLlmProviderConfig> providers = parseProviders(model);
            Map<String, SimulationLlmModelConfig> models = parseModels(model);

            return new SimulationMaiBotRuntimeConfig(
                    configDir,
                    stringValue(bot, "bot.nickname", "maid"),
                    stringValue(bot, "personality.personality", "You are a gentle companion maid AI."),
                    stringValue(bot, "personality.reply_style", "Reply briefly and naturally."),
                    stringValue(bot, "personality.plan_style", "Choose appropriate actions based on events and tools."),
                    stringValue(bot, "personality.visual_style", stringValue(bot, "visual_style", "Summarize the scene briefly.")),
                    providers,
                    models,
                    parseTask(model, "model_task_config.planner", 0.3D, 900),
                    parseTask(model, "model_task_config.replyer", 0.3D, 600),
                    parseTask(model, "model_task_config.tool_use", 0.4D, 700),
                    parseTask(model, "model_task_config.vlm", 0.2D, 256),
                    true,
                    "loaded-standalone"
            );
        } catch (Exception exception) {
            return SimulationMaiBotRuntimeConfig.unavailable(configDir, exception.getClass().getSimpleName() + ": " + exception.getMessage());
        }
    }

    private static Map<String, SimulationLlmProviderConfig> parseProviders(UnmodifiableConfig config) {
        Map<String, SimulationLlmProviderConfig> result = new LinkedHashMap<>();
        Object raw = config.get("api_providers");
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof UnmodifiableConfig provider) {
                    SimulationLlmProviderConfig providerConfig = new SimulationLlmProviderConfig(
                            stringValue(provider, "name", ""),
                            stringValue(provider, "base_url", ""),
                            stringValue(provider, "api_key", ""),
                            stringValue(provider, "client_type", "openai"),
                            intValue(provider, "max_retry", 2),
                            intValue(provider, "timeout", 120),
                            intValue(provider, "retry_interval", 5)
                    );
                    if (!providerConfig.name().isBlank()) {
                        result.put(providerConfig.name(), providerConfig);
                    }
                }
            }
        }
        return result;
    }

    private static Map<String, SimulationLlmModelConfig> parseModels(UnmodifiableConfig config) {
        Map<String, SimulationLlmModelConfig> result = new LinkedHashMap<>();
        Object raw = config.get("models");
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof UnmodifiableConfig model) {
                    Map<String, Object> extraParams = new LinkedHashMap<>();
                    Object extra = model.get("extra_params");
                    if (extra instanceof Config childConfig) {
                        extraParams.putAll(childConfig.valueMap());
                    }
                    SimulationLlmModelConfig modelConfig = new SimulationLlmModelConfig(
                            stringValue(model, "name", ""),
                            stringValue(model, "model_identifier", ""),
                            stringValue(model, "api_provider", ""),
                            extraParams,
                            booleanValue(model, "force_stream_mode", false)
                    );
                    if (!modelConfig.name().isBlank()) {
                        result.put(modelConfig.name(), modelConfig);
                    }
                }
            }
        }
        return result;
    }

    private static SimulationLlmTaskConfig parseTask(UnmodifiableConfig config, String path, double defaultTemperature, int defaultMaxTokens) {
        List<String> modelList = stringList(config, path + ".model_list");
        return new SimulationLlmTaskConfig(
                modelList,
                doubleValue(config, path + ".temperature", defaultTemperature),
                intValue(config, path + ".max_tokens", defaultMaxTokens),
                stringValue(config, path + ".selection_strategy", "random")
        );
    }

    private static String stringValue(UnmodifiableConfig config, String path, String defaultValue) {
        Object value = config.get(path);
        return value == null ? defaultValue : String.valueOf(value).trim();
    }

    private static int intValue(UnmodifiableConfig config, String path, int defaultValue) {
        Object value = config.get(path);
        if (value instanceof Number number) {
            return number.intValue();
        }
        return defaultValue;
    }

    private static double doubleValue(UnmodifiableConfig config, String path, double defaultValue) {
        Object value = config.get(path);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return defaultValue;
    }

    private static boolean booleanValue(UnmodifiableConfig config, String path, boolean defaultValue) {
        Object value = config.get(path);
        if (value instanceof Boolean bool) {
            return bool;
        }
        return defaultValue;
    }

    private static List<String> stringList(UnmodifiableConfig config, String path) {
        Object value = config.get(path);
        if (value instanceof List<?> list) {
            List<String> result = new ArrayList<>(list.size());
            for (Object item : list) {
                if (item != null) {
                    result.add(String.valueOf(item));
                }
            }
            return result;
        }
        return List.of();
    }
}
