package com.maidsoul.brain.config;

import java.nio.file.Path;
import java.util.Properties;

/**
 * 记忆与情绪系统配置。
 *
 * <p>先保持轻量：JSONL 持久化 + 关键词检索。等聊天链路稳定后，再把 embedding/vector
 * 作为可选索引挂上来，避免记忆系统一开始就拖慢主回复。</p>
 */
public record MemoryConfig(
        boolean enabled,
        String dataRoot,
        String maidId,
        String ownerId,
        String worldId,
        int promptMemoryLimit,
        int promptProfileLimit,
        int retrievalLimit,
        boolean queryMemoryToolEnabled
) {
    public static MemoryConfig load(Path path) {
        Properties p = ConfigFiles.load(path);
        return new MemoryConfig(
                ConfigFiles.bool(p, "enabled", true),
                ConfigFiles.text(p, "dataRoot", "data/memory"),
                ConfigFiles.text(p, "maidId", "prototype-jiuhu"),
                ConfigFiles.text(p, "ownerId", "prototype-owner"),
                ConfigFiles.text(p, "worldId", "prototype-world"),
                ConfigFiles.integer(p, "promptMemoryLimit", 3),
                ConfigFiles.integer(p, "promptProfileLimit", 5),
                ConfigFiles.integer(p, "retrievalLimit", 5),
                ConfigFiles.bool(p, "queryMemoryToolEnabled", true)
        );
    }
}
