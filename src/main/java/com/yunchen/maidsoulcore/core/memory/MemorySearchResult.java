package com.yunchen.maidsoulcore.core.memory;

public record MemorySearchResult(
        LifeMemoryStore.MemoryEpisode episode,
        double score,
        double lexicalScore,
        double graphScore,
        double metadataScore,
        double priorityScore,
        String source
) {
}
