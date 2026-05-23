package com.maidsoul.brain.forge.soul;

public record SoulSummary(
        String soulId,
        String displayName,
        String lastWorldId,
        String lastMaidUuid,
        String lastOwnerUuid,
        long lastUsedAt
) {
    public static SoulSummary from(SoulRecord record) {
        return new SoulSummary(
                record.soulId(),
                record.displayName(),
                record.lastWorldId(),
                record.lastMaidUuid(),
                record.lastOwnerUuid(),
                record.lastUsedAt()
        );
    }
}
