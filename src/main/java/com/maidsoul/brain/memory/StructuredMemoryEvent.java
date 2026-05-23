package com.maidsoul.brain.memory;

import java.util.List;

/**
 * 结构化记忆事件。
 *
 * <p>自然语言到语义标签的判断不放在记忆底座里做。外部 planner、工具、GUI 或未来的
 * 语义抽取器如果确认某条信息属于偏好、关系、世界事实、纠错等，就用这个事件显式写入。</p>
 */
public record StructuredMemoryEvent(
        MemoryType type,
        String layer,
        String role,
        String content,
        int importance,
        List<String> tags,
        String source
) {
    public StructuredMemoryEvent {
        type = type == null ? MemoryType.DIALOGUE : type;
        layer = layer == null ? "" : layer.trim();
        role = role == null ? "" : role.trim();
        content = content == null ? "" : content.trim();
        importance = Math.max(1, Math.min(5, importance));
        tags = tags == null ? List.of() : List.copyOf(tags);
        source = source == null ? "" : source.trim();
    }
}
