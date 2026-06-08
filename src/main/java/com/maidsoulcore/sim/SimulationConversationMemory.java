package com.maidsoulcore.sim;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * 对话历史缓存。
 * <p>
 * 这里保留最近若干轮用户发言、女仆回复和世界事件，
 * 让真实 LLM 不会每轮都像失忆。
 */
public final class SimulationConversationMemory {
    private final int maxEntries;
    private final Deque<String> entries;

    public SimulationConversationMemory(int maxEntries) {
        this.maxEntries = Math.max(8, maxEntries);
        this.entries = new ArrayDeque<>(this.maxEntries);
    }

    /**
     * 记录一条历史文本。
     */
    public synchronized void add(String line) {
        if (entries.size() >= maxEntries) {
            entries.removeFirst();
        }
        entries.addLast(line);
    }

    /**
     * 返回最近历史。
     */
    public synchronized List<String> snapshot() {
        return new ArrayList<>(entries);
    }

    /**
     * 拼成提示词片段。
     */
    public synchronized String joined() {
        return String.join("\n", entries);
    }
}
