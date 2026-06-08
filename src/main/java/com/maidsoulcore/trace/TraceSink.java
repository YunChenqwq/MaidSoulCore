package com.maidsoulcore.trace;

/**
 * trace 输出端抽象。
 */
public interface TraceSink {
    /**
     * 接收一条 trace 事件。
     */
    void accept(TraceEvent event);
}
