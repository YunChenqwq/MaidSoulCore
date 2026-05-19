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
