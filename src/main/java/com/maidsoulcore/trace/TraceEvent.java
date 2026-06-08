package com.maidsoulcore.trace;

import com.maidsoulcore.event.EventPriority;
import com.maidsoulcore.event.EventStage;

import java.time.Instant;

/**
 * 统一 trace 事件结构。
 * <p>
 * 这类数据面向调试和运维，而不是直接面向大模型。
 */
public record TraceEvent(
        long sequence,
        Instant timestamp,
        String maidId,
        String linkId,
        String type,
        EventPriority priority,
        EventStage stage,
        String route,
        String reason
) {
}
