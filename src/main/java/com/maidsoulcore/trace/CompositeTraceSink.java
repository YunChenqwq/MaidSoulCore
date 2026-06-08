package com.maidsoulcore.trace;

import java.util.List;

/**
 * 把一条 trace 同时广播到多个输出目的地。
 * <p>
 * 例如同时输出到内存环形缓冲、日志文件、远程调试器。
 */
public final class CompositeTraceSink implements TraceSink {
    private final List<TraceSink> sinks;

    public CompositeTraceSink(List<TraceSink> sinks) {
        this.sinks = List.copyOf(sinks);
    }

    @Override
    public void accept(TraceEvent event) {
        for (TraceSink sink : sinks) {
            sink.accept(event);
        }
    }
}
