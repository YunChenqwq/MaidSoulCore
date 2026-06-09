package com.yunchen.maidsoulcore.core.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class DialogueConfigLoader {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private DialogueConfigLoader() {
    }

    public static DialogueCoreConfig loadOrCreate(Path path) {
        try {
            if (Files.notExists(path)) {
                Files.createDirectories(path.getParent());
                DialogueCoreConfig defaults = loadDefault();
                save(path, defaults);
                return defaults;
            }
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                DialogueCoreConfig config = GSON.fromJson(reader, DialogueCoreConfig.class);
                config = config == null ? new DialogueCoreConfig() : config;
                normalize(config);
                return config;
            }
        } catch (IOException e) {
            throw new UncheckedIOException("读取女仆灵魂核心配置失败: " + path, e);
        }
    }

    public static DialogueCoreConfig loadDefault() {
        try (InputStream stream = DialogueConfigLoader.class.getResourceAsStream("/maidsoulcore/default-dialogue-config.json")) {
            if (stream == null) {
                DialogueCoreConfig fallback = new DialogueCoreConfig();
                normalize(fallback);
                return fallback;
            }
            String json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            DialogueCoreConfig config = GSON.fromJson(json, DialogueCoreConfig.class);
            config = config == null ? new DialogueCoreConfig() : config;
            normalize(config);
            return config;
        } catch (IOException e) {
            throw new UncheckedIOException("读取内置默认配置失败", e);
        }
    }

    public static void save(Path path, DialogueCoreConfig config) {
        try {
            Files.createDirectories(path.getParent());
            normalize(config);
            Files.writeString(path, GSON.toJson(config), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("保存女仆灵魂核心配置失败: " + path, e);
        }
    }

    public static void normalize(DialogueCoreConfig config) {
        if (config.model == null) {
            config.model = new DialogueModelConfig();
        }
        if (config.vision == null) {
            config.vision = new DialogueVisionConfig();
        }
        if (config.debug == null) {
            config.debug = new DialogueDebugConfig();
        }
    }
}
