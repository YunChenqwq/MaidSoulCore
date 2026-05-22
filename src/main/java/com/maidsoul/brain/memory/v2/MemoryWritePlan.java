package com.maidsoul.brain.memory.v2;

import java.util.List;

/**
 * 记忆写入计划。
 *
 * <p>这一层专门回答“这句话应该进入哪类记忆”。它不负责持久化，也不参与 prompt 话术，
 * 只把上游候选记忆拆成稳定的结构标签，避免所有内容都混进同一种 dialogue 段落里。</p>
 */
public record MemoryWritePlan(
        boolean shouldStore,
        String layer,
        String sourceType,
        int salience,
        List<String> tags,
        boolean protect,
        boolean permanent,
        String reason
) {
    public String metadataSuffix() {
        return "memoryLayer=" + layer
                + ";writeReason=" + reason
                + ";protect=" + protect
                + ";permanent=" + permanent;
    }

    public static MemoryWritePlan skip(String reason) {
        return new MemoryWritePlan(false, "skip", "skip", 0, List.of(), false, false, reason);
    }
}
