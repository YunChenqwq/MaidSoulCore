package com.maidsoulcore.forge.plan;

import com.maidsoulcore.event.EventPriority;

/**
 * MaidSoulCore 内部统一事件信封。
 * <p>
 * 这层的作用是把 Forge/TLM/运行时状态变化统一压成稳定结构，
 * 方便后续计划器、调度器、调试输出都围绕同一份事件对象工作。
 */
public record MaidSoulEventEnvelope(
        String eventType,
        String detail,
        EventPriority priority,
        long timestampMillis
) {
    /**
     * 创建一条标准事件。
     */
    public static MaidSoulEventEnvelope of(String eventType, String detail, EventPriority priority) {
        return new MaidSoulEventEnvelope(
                eventType == null ? "unknown" : eventType,
                detail == null ? "" : detail,
                priority == null ? EventPriority.P2 : priority,
                System.currentTimeMillis()
        );
    }
}
