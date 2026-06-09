package com.maidsoulcore.integration.maibot;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * MaiBot 配置读取服务。
 * <p>
 * 这里不直接复用 MaiBot 的 Python 运行时，而是只借它的配置内容：
 * 人设、回复风格、计划规则、模型分工。
 * <p>
 * 这样做的好处是：
 * 1. Minecraft 侧集成简单；
 * 2. 你不需要再配一次模型；
 * 3. 后续 Java 运行时成熟后也还能继续沿用同一套配置。
 */
public final class MaiBotConfigService {
    private static volatile MaiBotConfigSnapshot cached = null;
    private static volatile long botConfigLastModified = Long.MIN_VALUE;
    private static volatile long modelConfigLastModified = Long.MIN_VALUE;

    private MaiBotConfigService() {
    }

    /**
     * 获取当前最新配置快照。
     * <p>
     * 这里做了基于文件修改时间的缓存，避免每次取上下文都重新读盘。
     */
    public static MaiBotConfigSnapshot getSnapshot() {
        String explicitDir = System.getenv("MAIDSOUL_MAIBOT_CONFIG_DIR");
        Path configDir = explicitDir == null || explicitDir.isBlank()
                ? FMLPaths.CONFIGDIR.get().resolve("maidsoulcore").resolve("maibot")
                : Path.of(explicitDir);
        Path cleanBotConfig = configDir.resolve("textgame_bot_config.toml");
        Path botConfig = Files.exists(cleanBotConfig) ? cleanBotConfig : configDir.resolve("bot_config.toml");
        Path modelConfig = configDir.resolve("model_config.toml");
        long botModified = lastModified(botConfig);
        long modelModified = lastModified(modelConfig);
        MaiBotConfigSnapshot local = cached;
        if (local != null && botModified == botConfigLastModified && modelModified == modelConfigLastModified) {
            return local;
        }

        synchronized (MaiBotConfigService.class) {
            local = cached;
            if (local != null && botModified == botConfigLastModified && modelModified == modelConfigLastModified) {
                return local;
            }
            MaiBotConfigSnapshot loaded = load(configDir, botConfig, modelConfig);
            cached = loaded;
            botConfigLastModified = botModified;
            modelConfigLastModified = modelModified;
            return loaded;
        }
    }

    /**
     * 真正执行加载逻辑。
     */
    private static MaiBotConfigSnapshot load(Path configDir, Path botConfig, Path modelConfig) {
        if (!Files.exists(botConfig) || !Files.exists(modelConfig)) {
            return MaiBotConfigSnapshot.unavailable(configDir, "MaiBot config files not found");
        }

        try (
                CommentedFileConfig bot = CommentedFileConfig.builder(botConfig).sync().build();
                CommentedFileConfig model = CommentedFileConfig.builder(modelConfig).sync().build()
        ) {
            bot.load();
            model.load();
            MaiBotPersonalitySettings personality = new MaiBotPersonalitySettings(
                    stringValue(bot, "bot.nickname"),
                    stringValue(bot, "personality.personality"),
                    stringValue(bot, "personality.reply_style"),
                    stringValue(bot, "personality.plan_style"),
                    stringList(bot, "bot.alias_names")
            );
            MaiBotModelSettings modelSettings = new MaiBotModelSettings(
                    stringList(model, "model_task_config.planner.model_list"),
                    stringList(model, "model_task_config.replyer.model_list"),
                    stringList(model, "model_task_config.tool_use.model_list"),
                    stringList(model, "model_task_config.vlm.model_list")
            );
            return new MaiBotConfigSnapshot(configDir, modelSettings, personality, true, "loaded");
        } catch (Exception exception) {
            return MaiBotConfigSnapshot.unavailable(configDir, exception.getClass().getSimpleName() + ": " + exception.getMessage());
        }
    }

    /**
     * 读取文件最后修改时间，用于缓存判断。
     */
    private static long lastModified(Path path) {
        try {
            return Files.exists(path) ? Files.getLastModifiedTime(path).toMillis() : Long.MIN_VALUE;
        } catch (Exception ignored) {
            return Long.MIN_VALUE;
        }
    }

    /**
     * 读取单个字符串字段。
     */
    private static String stringValue(UnmodifiableConfig config, String path) {
        Object value = config.get(path);
        return value == null ? "" : String.valueOf(value).trim();
    }

    /**
     * 读取字符串列表字段。
     */
    private static List<String> stringList(UnmodifiableConfig config, String path) {
        Object value = config.get(path);
        if (value instanceof List<?> list) {
            List<String> result = new ArrayList<>(list.size());
            for (Object item : list) {
                if (item instanceof Config childConfig) {
                    result.add(childConfig.valueMap().toString());
                } else if (item != null) {
                    result.add(String.valueOf(item));
                }
            }
            return result;
        }
        return List.of();
    }
}
