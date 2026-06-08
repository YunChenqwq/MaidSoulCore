package com.maidsoulcore.event;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * MaidSoulCore 内部统一事件格式。
 * <p>
 * 不论原始事件来自 Forge、TLM 还是未来的自定义输入，
 * 进入核心层之后都应尽量转换成这个结构，保证后续模块
 * 不需要关心来源差异。
 */
public record MaidEvent(
        UUID eventId,
        String maidId,
        String ownerId,
        String type,
        EventPriority priority,
        Instant timestamp,
        Map<String, Object> payload
) {
    /**
     * 方便快速构造一条“现在发生”的事件。
     */
    public static MaidEvent of(
            String maidId,
            String ownerId,
            String type,
            EventPriority priority,
            Map<String, Object> payload
    ) {
        return new MaidEvent(UUID.randomUUID(), maidId, ownerId, type, priority, Instant.now(), payload);
    }
}
