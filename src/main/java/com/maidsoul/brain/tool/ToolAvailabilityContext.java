package com.maidsoul.brain.tool;

import java.util.Map;

/**
 * 工具可见性判断上下文。
 *
 * <p>这对应动态 availability context：工具不是永远暴露给 planner，
 * 而是根据当前阶段、会话、平台能力和配置动态决定。第一版先保留最小字段，
 * 后续接 Forge 行为工具、客户端工具和调试工具时继续扩展 metadata。</p>
 */
public record ToolAvailabilityContext(
        String sessionId,
        String stage,
        Map<String, Object> metadata
) {
    public ToolAvailabilityContext {
        sessionId = sessionId == null ? "" : sessionId;
        stage = stage == null ? "action" : stage;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static ToolAvailabilityContext action() {
        return new ToolAvailabilityContext("prototype-session", "action", Map.of());
    }
}
