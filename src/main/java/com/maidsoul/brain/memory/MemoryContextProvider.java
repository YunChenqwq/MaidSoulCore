package com.maidsoul.brain.memory;

/**
 * 给推理上下文提供情绪、记忆、画像摘要。
 */
@FunctionalInterface
public interface MemoryContextProvider {
    String render(String latestText);

    static MemoryContextProvider none() {
        return latestText -> "";
    }
}
