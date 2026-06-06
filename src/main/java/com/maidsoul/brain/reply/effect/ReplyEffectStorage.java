package com.maidsoul.brain.reply.effect;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 回复效果记录落盘器。
 *
 * <p>上游参考系统 会为每条 reply effect 写 JSON 文件，便于调试、复盘和离线评分。
 * 原型机先使用本地 UTF-8 JSON 文件，不引入数据库依赖。</p>
 */
public final class ReplyEffectStorage {
    private final Path root;

    public ReplyEffectStorage() {
        this(Path.of("data", "reply_effect"));
    }

    public ReplyEffectStorage(Path root) {
        this.root = root == null ? Path.of("data", "reply_effect") : root;
    }

    public synchronized Path createRecordFile(ReplyEffectRecord record) {
        Path path = resolveRecordPath(record);
        record.setFilePath(path);
        saveRecord(record);
        return path;
    }

    public synchronized void saveRecord(ReplyEffectRecord record) {
        if (record == null) {
            return;
        }
        Path path = record.filePath();
        if (path == null) {
            path = resolveRecordPath(record);
            record.setFilePath(path);
        }
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, record.toJson(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            // 观察数据不能阻断聊天主链路，失败时只让上层日志继续可见。
            throw new IllegalStateException("保存回复效果记录失败: " + path, e);
        }
    }

    private Path resolveRecordPath(ReplyEffectRecord record) {
        String session = sanitize(record.sessionId().isBlank() ? "default" : record.sessionId());
        return root.resolve(session).resolve(record.effectId() + ".json");
    }

    private static String sanitize(String value) {
        String text = value == null ? "" : value.trim();
        if (text.isBlank()) {
            return "default";
        }
        return text.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
