package com.maidsoul.brain.config;

import java.nio.file.Path;

/**
 * 顶层配置对象。
 *
 * <p>配置按目录拆分，避免一个巨大配置文件把模型、人设、节奏、分句和调试全部挤在一起。
 * 后续接入模组配置页时，可以按这些对象分别做页面。</p>
 */
public record BrainConfig(
        IdentityConfig identity,
        ModelConfig model,
        FlowConfig flow,
        SplitterConfig splitter,
        MemoryConfig memory,
        DebugConfig debug
) {
    /**
     * 返回绑定到具体女仆、主人和世界的配置副本。
     *
     * <p>Forge/TLM 层只负责告诉核心“这是谁的会话”，不应该重新实现记忆路径规则。
     * 这样每只女仆都会写入自己的记忆目录，同时仍然共享同一个模组级推荐配置。</p>
     */
    public BrainConfig withRuntimeIdentity(String maidId, String ownerId, String worldId) {
        return new BrainConfig(
                identity,
                model,
                flow,
                splitter,
                memory.withRuntimeIdentity(maidId, ownerId, worldId),
                debug
        );
    }

    public static BrainConfig load(Path configRoot) {
        return new BrainConfig(
                IdentityConfig.load(configRoot.resolve("bot").resolve("identity.properties")),
                ModelConfig.load(configRoot.resolve("model").resolve("llm.properties")),
                FlowConfig.load(configRoot.resolve("conversation").resolve("flow.properties")),
                SplitterConfig.load(configRoot.resolve("conversation").resolve("splitter.properties")),
                MemoryConfig.load(configRoot.resolve("memory").resolve("memory.properties")),
                DebugConfig.load(configRoot.resolve("debug").resolve("trace.properties"))
        );
    }
}
