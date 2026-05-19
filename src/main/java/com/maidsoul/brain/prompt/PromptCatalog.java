package com.maidsoul.brain.prompt;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class PromptCatalog {
    private final Path promptDir;
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public PromptCatalog(Path promptDir) {
        this.promptDir = promptDir;
    }

    public String load(String name) {
        return cache.computeIfAbsent(name, this::read);
    }

    private String read(String name) {
        Path path = promptDir.resolve(name);
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("读取提示词失败: " + path, e);
        }
    }
}

