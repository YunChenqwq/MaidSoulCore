package com.maidsoul.brain.memory.v2;

import java.util.List;

/**
 * 写入结果。storedIds/skippedIds 复刻 A_Memorix 的幂等写入语义。
 */
public record MemoryWriteResult(
        boolean success,
        List<String> storedIds,
        List<String> skippedIds,
        String detail
) {
    static MemoryWriteResult stored(String id) {
        return new MemoryWriteResult(true, List.of(id), List.of(), "");
    }

    static MemoryWriteResult skipped(String id, String detail) {
        return new MemoryWriteResult(true, List.of(), List.of(id), detail);
    }
}
