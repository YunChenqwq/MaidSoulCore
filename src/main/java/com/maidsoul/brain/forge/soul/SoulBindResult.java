package com.maidsoul.brain.forge.soul;

public record SoulBindResult(
        SoulBindingData binding,
        SoulRecord record,
        String eventType,
        String eventDetail
) {
}
