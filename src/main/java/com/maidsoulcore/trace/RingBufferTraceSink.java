package com.maidsoulcore.trace;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * 基于环形缓冲的 trace 输出。
 * <p>
 * 适合给调试面板、调试工具和最近事件查询使用。
 */
public final class RingBufferTraceSink implements TraceSink {
    private final int capacity;
    private final ArrayDeque<TraceEvent> events;

    public RingBufferTraceSink(int capacity) {
        this.capacity = capacity;
        this.events = new ArrayDeque<>(capacity);
    }

    @Override
    public synchronized void accept(TraceEvent event) {
        if (events.size() >= capacity) {
            events.removeFirst();
        }
        events.addLast(event);
    }

    /**
     * 返回当前缓冲区的稳定快照。
     */
    public synchronized List<TraceEvent> snapshot() {
        return new ArrayList<>(events);
    }
}
