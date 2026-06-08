package com.maidsoulcore.forge.state;

/**
 * 单个女仆的轻量运行时摘要。
 * <p>
 * 这个结构主要面向：
 * 1. 上下文系统
 * 2. 调试面板
 * 3. trace 工具
 */
public record MaidSoulStateSnapshot(
        String maidName,
        String ownerName,
        String lastEventType,
        String lastEventDetail,
        String ownerViewRawSummary,
        String ownerViewInterpretedSummary,
        String schedule,
        String task,
        boolean homeMode,
        boolean sitting,
        boolean sleeping,
        String weather,
        String timePhase,
        double health,
        long lastOwnerChatMillis,
        int totalObservedEvents
) {
    /**
     * 返回一个空快照，避免上层处理 null。
     */
    public static MaidSoulStateSnapshot empty() {
        return new MaidSoulStateSnapshot("", "", "none", "", "", "", "unknown", "unknown", false, false, false, "clear", "unknown", 0.0d, 0L, 0);
    }
}
