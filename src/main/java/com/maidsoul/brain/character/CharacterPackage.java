package com.maidsoul.brain.character;

import com.maidsoul.brain.affect.AffectProfile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;

/**
 * 一个角色的完整本地包。
 *
 * <p>设计目标是把“人设、人格权重、心情、关系、长期参考记忆”收拢到同一个目录。
 * 这样角色不是散落在 prompt/config/memory 里的碎片，而是一个可以迁移、备份、回滚的
 * Character Package。第一版只负责读取和投影，不在这里生成台词。</p>
 */
public final class CharacterPackage {
    private final Path root;
    private final CharacterDefinition definition;
    private final CharacterTraits traits;
    private final RelationshipState relationship;

    private CharacterPackage(
            Path root,
            CharacterDefinition definition,
            CharacterTraits traits,
            RelationshipState relationship
    ) {
        this.root = root;
        this.definition = definition;
        this.traits = traits;
        this.relationship = relationship;
    }

    public static CharacterPackage load(Path root, String fallbackId) {
        Path resolved = root.toAbsolutePath().normalize();
        CharacterDefinition definition = CharacterDefinition.from(loadProperties(resolved.resolve("character.properties")), fallbackId);
        CharacterTraits traits = CharacterTraits.from(loadProperties(resolved.resolve("traits.properties")));
        RelationshipState relationship = loadRelationship(resolved.resolve("relationship.json"));
        return new CharacterPackage(resolved, definition, traits, relationship);
    }

    public String renderPromptBlock(AffectProfile affectProfile, String latestText, int memoryLimit) {
        StringBuilder builder = new StringBuilder();
        builder.append("[角色包核心定义]\n")
                .append(definition.renderCoreBlock())
                .append("\n\n[人格参数]\n")
                .append(traits.renderForPrompt())
                .append("\n\n[长期关系状态]\n")
                .append(relationship.renderForPrompt());
        if (affectProfile != null) {
            builder.append("\n\n[当前心情状态]\n")
                    .append(affectProfile.brief())
                    .append("\n状态解释=")
                    .append(affectProfile.stateHint());
        }
        String memories = renderCharacterMemories(latestText, memoryLimit);
        if (!memories.isBlank()) {
            builder.append("\n\n[角色包长期参考记忆]\n")
                    .append(memories);
        }
        builder.append("\n\n[使用原则]\n")
                .append("这些内容是角色状态的内部投影，不要逐字复述；")
                .append("planner 用它判断关系现场，replyer 用它把状态自然表达出来。");
        return builder.toString();
    }

    public Path affectPath() {
        return root.resolve("affect_state.json");
    }

    private String renderCharacterMemories(String latestText, int limit) {
        List<CharacterMemoryEntry> entries = loadMemoryEntries(root.resolve("memories").resolve("core_memories.jsonl"));
        if (entries.isEmpty()) {
            return "";
        }
        String query = latestText == null ? "" : latestText;
        return entries.stream()
                .filter(entry -> entry.matches(query))
                .sorted(Comparator.comparingInt(CharacterMemoryEntry::salience).reversed())
                .limit(Math.max(1, limit))
                .map(CharacterMemoryEntry::render)
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
    }

    private static Properties loadProperties(Path path) {
        Properties properties = new Properties();
        if (Files.notExists(path)) {
            return properties;
        }
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            properties.load(reader);
            return properties;
        } catch (IOException e) {
            throw new UncheckedIOException("读取角色包配置失败: " + path, e);
        }
    }

    private static RelationshipState loadRelationship(Path path) {
        if (Files.notExists(path)) {
            return new RelationshipState();
        }
        try {
            return RelationshipState.fromJson(Files.readString(path, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("读取角色关系状态失败: " + path, e);
        }
    }

    private static List<CharacterMemoryEntry> loadMemoryEntries(Path path) {
        if (Files.notExists(path)) {
            return List.of();
        }
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return reader.lines()
                    .map(String::trim)
                    .filter(line -> !line.isBlank())
                    .map(CharacterMemoryEntry::fromJsonLine)
                    .filter(entry -> !entry.text().isBlank())
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("读取角色长期参考记忆失败: " + path, e);
        }
    }
}
