package com.yunchen.maidsoulcore.core.prompt;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class PromptCatalog {
    private final Path externalPromptDir;

    public PromptCatalog(Path externalPromptDir) {
        this.externalPromptDir = externalPromptDir;
    }

    public String load(String name) {
        Path external = externalPromptDir.resolve(name + ".md");
        try {
            if (Files.exists(external)) {
                return Files.readString(external, StandardCharsets.UTF_8);
            }
            String resource = "/maidsoulcore/prompts/" + name + ".md";
            try (InputStream stream = PromptCatalog.class.getResourceAsStream(resource)) {
                if (stream == null) {
                    return "";
                }
                return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("读取提示词失败: " + name, e);
        }
    }

    public void installDefaultsIfMissing() {
        try {
            Files.createDirectories(externalPromptDir);
            for (String name : new String[]{"identity", "timing_gate", "planner", "replyer"}) {
                Path target = externalPromptDir.resolve(name + ".md");
                if (Files.notExists(target)) {
                    Files.writeString(target, load(name), StandardCharsets.UTF_8);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("初始化提示词目录失败", e);
        }
    }
}
