package com.maidsoul.brain.expression;

import com.maidsoul.brain.util.SimpleJson;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 表达方式本地存储。
 *
 * <p>maibotdev 使用数据库保存表达方式；原型机先用 JSONL 保持可迁移结构。
 * 每行字段：id/session_id/situation/style/count/checked。</p>
 */
public final class ExpressionStore {
    private final Path file;

    public ExpressionStore() {
        this(Path.of("data", "expression", "expressions.jsonl"));
    }

    public ExpressionStore(Path file) {
        this.file = file == null ? Path.of("data", "expression", "expressions.jsonl") : file;
    }

    public List<ExpressionCandidate> loadCandidates(String sessionId) {
        if (!Files.exists(file)) {
            return List.of();
        }
        List<ExpressionCandidate> candidates = new ArrayList<>();
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                ExpressionCandidate candidate = parseLine(line);
                if (candidate == null) {
                    continue;
                }
                if (candidate.sessionId().isBlank() || candidate.sessionId().equals(sessionId)) {
                    candidates.add(candidate);
                }
            }
        } catch (IOException ignored) {
            return List.of();
        }
        return candidates;
    }

    private static ExpressionCandidate parseLine(String line) {
        if (line == null || line.isBlank()) {
            return null;
        }
        Map<String, String> map = SimpleJson.object(line);
        int id = SimpleJson.integer(map.get("id"), 0);
        String situation = map.getOrDefault("situation", "");
        String style = map.getOrDefault("style", "");
        if (id <= 0 || situation.isBlank() || style.isBlank()) {
            return null;
        }
        return new ExpressionCandidate(
                id,
                map.getOrDefault("session_id", ""),
                situation,
                style,
                SimpleJson.integer(map.get("count"), 1),
                Boolean.parseBoolean(map.getOrDefault("checked", "true"))
        );
    }
}
