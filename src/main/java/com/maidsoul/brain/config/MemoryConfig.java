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
        String characterRoot,
        String maidId,
        String ownerId,
        String worldId,
        int promptMemoryLimit,
        int promptProfileLimit,
        int retrievalLimit,
        boolean queryMemoryToolEnabled
) {
    /**
     * 为某个具体女仆实例创建运行时记忆配置。
     *
     * <p>桌面原型里 maidId / ownerId / worldId 可以写死在配置文件里；接入 Forge 后，
     * 每只车万女仆都有自己的实体 UUID，每个主人和存档也不同。这里保留同一套
     * 写入策略、容量和开关，只替换运行态身份，避免 Forge adapter 直接改核心字段。</p>
     */
    public MemoryConfig withRuntimeIdentity(String maidId, String ownerId, String worldId) {
        return new MemoryConfig(
                enabled,
                dataRoot,
                characterRoot,
                sanitize(maidId, this.maidId),
                sanitize(ownerId, this.ownerId),
                sanitize(worldId, this.worldId),
                promptMemoryLimit,
                promptProfileLimit,
                retrievalLimit,
                queryMemoryToolEnabled
        );
    }

    public static MemoryConfig load(Path path) {
        Properties p = ConfigFiles.load(path);
        return new MemoryConfig(
                ConfigFiles.bool(p, "enabled", true),
                ConfigFiles.text(p, "dataRoot", "data/memory"),
                ConfigFiles.text(p, "characterRoot", "data/characters"),
                ConfigFiles.text(p, "maidId", "prototype-jiuhu"),
                ConfigFiles.text(p, "ownerId", "prototype-owner"),
                ConfigFiles.text(p, "worldId", "prototype-world"),
                ConfigFiles.integer(p, "promptMemoryLimit", 3),
                ConfigFiles.integer(p, "promptProfileLimit", 5),
                ConfigFiles.integer(p, "retrievalLimit", 5),
                ConfigFiles.bool(p, "queryMemoryToolEnabled", true)
        );
    }

    private static String sanitize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
