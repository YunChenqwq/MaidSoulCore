package com.maidsoul.brain.memory;

import com.maidsoul.brain.config.MemoryConfig;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * JSONL 人生记忆存储。
 */
public final class LifeMemoryStore {
    private final Path root;
    private final MemoryConfig config;

    public LifeMemoryStore(MemoryConfig config) {
        this.config = config;
        Path configured = Path.of(config.dataRoot());
        this.root = configured.isAbsolute() ? configured : Path.of("").toAbsolutePath().resolve(configured).normalize();
    }

    public synchronized void append(LifeMemory memory) {
        if (!config.enabled() || memory == null || memory.content == null || memory.content.isBlank()) {
            return;
        }
        try {
            Files.createDirectories(maidDir());
            try (BufferedWriter writer = Files.newBufferedWriter(memoryPath(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                writer.write(memory.toJsonLine());
                writer.newLine();
            }
        } catch (IOException e) {
            throw new UncheckedIOException("保存人生记忆失败: " + memoryPath(), e);
        }
    }

    public synchronized List<LifeMemory> all() {
        if (Files.notExists(memoryPath())) {
            return List.of();
        }
        List<LifeMemory> memories = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(memoryPath(), StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String clean = line.trim();
                if (!clean.isBlank()) {
                    memories.add(LifeMemory.fromJsonLine(clean));
                }
            }
            return memories;
        } catch (IOException e) {
            throw new UncheckedIOException("读取人生记忆失败: " + memoryPath(), e);
        }
    }

    public synchronized List<LifeMemory> search(String query, int limit) {
        String normalized = normalize(query);
        return all().stream()
                .sorted(Comparator.comparingDouble((LifeMemory memory) -> score(memory, normalized)).reversed())
                .limit(Math.max(1, limit))
                .toList();
    }

    public String renderPromptBlock(String query, int limit) {
        List<LifeMemory> memories = search(query, limit);
        if (memories.isEmpty()) {
            return "none";
        }
        StringBuilder builder = new StringBuilder();
        for (LifeMemory memory : memories) {
            builder.append("- [")
                    .append(memory.type.name().toLowerCase(Locale.ROOT))
                    .append("] ")
                    .append(memory.content)
                    .append("（重要度")
                    .append(memory.importance)
                    .append("，当时情绪 ")
                    .append("心情")
                    .append(memory.mood)
                    .append("/受伤")
                    .append(memory.hurt)
                    .append("/愤怒")
                    .append(memory.anger)
                    .append("）\n");
        }
        return builder.toString().trim();
    }

    private double score(LifeMemory memory, String query) {
        double score = memory.importance * 1.5 + memory.emotionalWeight * 3.0;
        String text = normalize(memory.content + " " + String.join(" ", memory.tags));
        if (!query.isBlank()) {
            if (text.contains(query) || query.contains(text)) {
                score += 6.0;
            }
            for (String token : query.split("\\s+")) {
                if (token.length() >= 2 && text.contains(token)) {
                    score += 1.5;
                }
            }
            for (int i = 0; i + 1 < query.length(); i++) {
                String gram = query.substring(i, i + 2);
                if (text.contains(gram)) {
                    score += 0.25;
                }
            }
        }
        return score;
    }

    public Path maidDir() {
        return root.resolve("maids")
                .resolve(config.maidId())
                .resolve(config.worldId());
    }

    public Path dailyDir() {
        return maidDir().resolve("daily");
    }

    private Path memoryPath() {
        return maidDir().resolve("life_memory.jsonl");
    }

    private static String normalize(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }
}
