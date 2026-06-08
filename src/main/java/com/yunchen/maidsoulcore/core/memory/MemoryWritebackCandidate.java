package com.yunchen.maidsoulcore.core.memory;

public record MemoryWritebackCandidate(
        MemoryCategory category,
        String subject,
        String object,
        String summary,
        String evidence,
        double confidence,
        int importance,
        String eventType
) {
}
